package com.example.seatstats.service;

import com.example.seatstats.dto.ChangeRecordDTO;
import com.example.seatstats.dto.RouteSuspensionDTO;
import com.example.seatstats.entity.ChangeRecord;
import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Seat;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.ChangeRecordRepository;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 封航停运领域服务。
 *
 * <p>封航必须在<strong>同一个数据库事务</strong>里同时完成两件事，任一失败整次回滚：</p>
 * <ol>
 *   <li>把当时还挂在这条航线上的候船座椅全部拆下（seat.route 置空），
 *       资产看板的该航线配套件数随之归零；</li>
 *   <li>按本次封航生成一份停运挂载清单（每把椅子一条 SUSPEND 台账，共享同一批次号），
 *       记录每把椅子停运当时原先挂在哪条航线，供复航逐把核对。台账只增不改不删。</li>
 * </ol>
 *
 * <p>因此不会出现“航线已停运却还挂着椅子”或“航线仍在运营但配套已先被清零”的中间态：
 * 若拆到一半失败（例如某把椅子写不出台账），事务回滚，航线保持运营中，
 * 已拆下的椅子也会自动回到这条航线上。</p>
 */
@Service
public class SuspensionService {

    private static final Logger log = LoggerFactory.getLogger(SuspensionService.class);

    /** 变更类型：封航拆绑。区别于普通的 UNBIND（人工调整），台账可据此区分封航清单 */
    public static final String CHANGE_TYPE_SUSPEND = "SUSPEND";

    public static final String ROUTE_STATUS_ACTIVE = "ACTIVE";
    public static final String ROUTE_STATUS_INACTIVE = "INACTIVE";

    private static final DateTimeFormatter BATCH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ChangeRecordRepository changeRecordRepository;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 测试/演练用故障注入开关：置为 true 时，在处理到第 2 把椅子时模拟
     * “该椅子写不出台账”抛出异常——此时第 1 把椅子已经拆下并写过台账。
     * 用于验证整次封航回滚：第 1 把椅子的绑定与半份台账全部撤销，
     * 航线保持运营。生产环境永远保持 false。
     */
    private final AtomicBoolean failMidway = new AtomicBoolean(false);

    public void setFailAfterUnbind(boolean fail) {
        this.failMidway.set(fail);
    }

    /**
     * 对指定航线执行封航停运。
     *
     * @param routeId  航线ID
     * @param operator 调度操作人
     * @param reason   封航原因（如汛期临时封航）
     * @return 封航结果，含本次批次号与停运当时的完整挂载清单
     */
    @Transactional
    public RouteSuspensionDTO suspendRoute(Long routeId, String operator, String reason) {
        // 1. 锁住航线行 + 校验当前必须运营中。并发封航在此串行化，
        //    第二个事务会等锁，拿到锁后看到 INACTIVE 直接拒绝，保证只生效一次。
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new BusinessException(404, "航线不存在"));

        if (!ROUTE_STATUS_ACTIVE.equals(route.getStatus())) {
            throw new BusinessException(400, "航线当前不是运营中状态，无法封航停运");
        }

        // 2. 取出当时所有还挂在这条航线上的椅子（JOIN FETCH，含航线信息），一把不漏
        List<Seat> boundSeats = seatRepository.findAllBoundByRouteId(routeId);

        String batchNo = generateBatchNo(route.getRouteCode());
        String op = (operator == null || operator.isBlank()) ? "SYSTEM" : operator;
        String suspendReason = (reason == null || reason.isBlank()) ? "汛期临时封航" : reason;

        List<ChangeRecord> records = new ArrayList<>(boundSeats.size());

        // 3. 逐把：先按“停运当时的挂载事实”写台账，再解绑。
        //    台账快照取椅子自身当前挂载（它们应当都挂在本航线上，直接取绑定事实，
        //    不依赖外层入参），保证清单反映的是真实挂载、无法被改写成“从没挂过”。
        int seatIndex = 0;
        for (Seat seat : boundSeats) {
            seatIndex++;

            // 故障注入：模拟拆到第二把时“这把椅子写不出台账”——
            // 第一把已经拆下，正好验证此前的解绑与台账随事务整体回滚。
            if (failMidway.get() && seatIndex == 2) {
                throw new BusinessException(500, "模拟故障：第二把椅子台账写入失败，整次封航回滚");
            }

            Route boundRoute = seat.getRoute();

            ChangeRecord record = new ChangeRecord();
            record.setSeat(seat);
            record.setChangeType(CHANGE_TYPE_SUSPEND);
            record.setSuspendBatchNo(batchNo);
            record.setOldRouteCode(boundRoute.getRouteCode());
            record.setOldRouteName(boundRoute.getRouteName());
            // 封航是拆下，不是改挂他线：新航线留空，明确表达“当时被拆离本航线”
            record.setNewRouteCode(null);
            record.setNewRouteName(null);
            record.setChangeReason(suspendReason);
            record.setOperator(op);
            record.setRemark("封航停运挂载清单，复航按批次 " + batchNo + " 逐把核对");

            // 先落台账；任何一把写失败（DB 异常 / 约束失败等）都会让整个事务回滚，
            // 此前已解绑的椅子、已写的台账随之一并撤销。
            records.add(changeRecordRepository.saveAndFlush(record));

            seat.setRoute(null);
            seatRepository.save(seat);
        }

        // 4. 全部椅子拆下且台账全部落库之后，航线才允许转为停运。
        //    状态翻转与解绑/台账在同一事务内提交，对外要么全部可见、要么全部不可见。
        route.setStatus(ROUTE_STATUS_INACTIVE);
        routeRepository.saveAndFlush(route);

        RouteSuspensionDTO result = new RouteSuspensionDTO();
        result.setRouteId(route.getId());
        result.setRouteCode(route.getRouteCode());
        result.setRouteName(route.getRouteName());
        result.setStatus(route.getStatus());
        result.setBatchNo(batchNo);
        result.setSeatCount(boundSeats.size());
        result.setOperator(op);
        result.setReason(suspendReason);
        result.setSuspendedAt(LocalDateTime.now());
        result.setItems(records.stream().map(this::toRecordDTO).collect(Collectors.toList()));

        // 5. 事务提交后清看板/航线缓存，保证后勤看到的配套数立即归零。
        //    用 afterCommit 避免回滚后误清缓存（回滚时数据没变，缓存本就仍正确）。
        evictCachesAfterCommit(routeId);

        log.info("封航停运生效：航线 {}({})，拆下座椅 {} 把，批次号 {}",
                route.getRouteCode(), route.getRouteName(), boundSeats.size(), batchNo);

        return result;
    }

    /**
     * 查询某次封航批次留下的完整挂载清单。台账只追加，
     * 因此即使航线后来复航/再次封航，按批次号仍能查到停运当时每把椅子的原挂载。
     */
    @Transactional(readOnly = true)
    public List<ChangeRecordDTO> getSuspensionManifest(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException(400, "批次号不能为空");
        }
        List<ChangeRecord> records = changeRecordRepository.findBySuspendBatchNoOrderByIdAsc(batchNo);
        if (records.isEmpty()) {
            throw new BusinessException(404, "未找到该封航批次的挂载清单");
        }
        return records.stream().map(this::toRecordDTO).collect(Collectors.toList());
    }

    /**
     * 历次封航批次一览（按批次号聚合成批次摘要），调度可据此选定某次封航查看清单。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSuspensionBatches() {
        List<ChangeRecord> records =
                changeRecordRepository.findByChangeTypeOrderByChangeTimeDescIdAsc(CHANGE_TYPE_SUSPEND);

        // 查询按时间倒序，每批次第一次出现时建摘要（该批次最近一条记录），随后只累计数量
        Map<String, Map<String, Object>> batches = new LinkedHashMap<>();
        for (ChangeRecord r : records) {
            Map<String, Object> summary = batches.get(r.getSuspendBatchNo());
            if (summary == null) {
                summary = new LinkedHashMap<>();
                summary.put("batchNo", r.getSuspendBatchNo());
                summary.put("oldRouteCode", r.getOldRouteCode());
                summary.put("oldRouteName", r.getOldRouteName());
                summary.put("operator", r.getOperator());
                summary.put("changeReason", r.getChangeReason());
                summary.put("changeTime", r.getChangeTime());
                summary.put("seatCount", 1);
                batches.put(r.getSuspendBatchNo(), summary);
            } else {
                summary.put("seatCount", (Integer) summary.get("seatCount") + 1);
            }
        }
        return new ArrayList<>(batches.values());
    }

    void evictCachesAfterCommit(Long routeId) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 事务已提交、数据已落库。缓存清理只做尽力而为：
                        // 即使 Redis 暂不可用也不能反过来让一次本已生效的封航报错，
                        // 缓存自带 30 分钟 TTL，最迟过期后看板自然归零。
                        try {
                            evict("seatStats", "all");
                            evict("seatStats", routeId);
                            evict("routes", "all");
                            evict("routes", routeId);
                        } catch (Exception e) {
                            log.warn("封航提交后清理看板缓存失败，将等待缓存自然过期：{}", e.getMessage());
                        }
                    }
                });
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private String generateBatchNo(String routeCode) {
        return "SUS-" + routeCode + "-" + LocalDateTime.now().format(BATCH_TIME_FORMAT)
                + "-" + (int) (Math.random() * 9000 + 1000);
    }

    private ChangeRecordDTO toRecordDTO(ChangeRecord record) {
        ChangeRecordDTO dto = new ChangeRecordDTO();
        dto.setId(record.getId());
        dto.setSeatId(record.getSeat().getId());
        dto.setSeatCode(record.getSeat().getSeatCode());
        dto.setChangeType(record.getChangeType());
        dto.setSuspendBatchNo(record.getSuspendBatchNo());
        dto.setOldRouteCode(record.getOldRouteCode());
        dto.setOldRouteName(record.getOldRouteName());
        dto.setNewRouteCode(record.getNewRouteCode());
        dto.setNewRouteName(record.getNewRouteName());
        dto.setChangeReason(record.getChangeReason());
        dto.setOperator(record.getOperator());
        dto.setChangeTime(record.getChangeTime());
        dto.setRemark(record.getRemark());
        return dto;
    }
}
