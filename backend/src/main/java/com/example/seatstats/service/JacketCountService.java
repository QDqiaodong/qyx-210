package com.example.seatstats.service;

import com.example.seatstats.dto.JacketCountDTO;
import com.example.seatstats.dto.JacketCountSubmitDTO;
import com.example.seatstats.entity.JacketCount;
import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Voyage;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.JacketCountRepository;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.VoyageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 救生衣按航线清点领域服务。
 *
 * <p>一次清点提交在<strong>同一个数据库事务</strong>里完成两件事，任一失败整体回滚：</p>
 * <ol>
 *   <li>按航线登记<strong>额定件数、实点件数、清点人</strong>；</li>
 *   <li>联动排班页班次标记：
 *     <ul>
 *       <li>实点少于额定时：已排好、<strong>开航时刻还没到</strong>的班次全部标成
 *           “缺衣待补”，已开航的班次一律不动；</li>
 *       <li>实点补到不少于额定后：仍是“缺衣待补”且开航时刻还没到的班次自动回到正常；
 *           但已经标成“缺衣待补”、且<strong>开航时刻已经过了</strong>的班次不会自动改回正常
 *           （它带着缺衣事实开过了航，标记作为历史留存）。</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>并发：两人同时给同一航线交清点时，先对航线行加悲观写锁串行化，
 * 再用乐观版本校验——后写完者持旧版本，拿到锁后会被拒绝（409），
 * 最终只留下先写完的那份实点，后写完者的班次联动也随之整笔回滚。</p>
 */
@Service
public class JacketCountService {

    private static final Logger log = LoggerFactory.getLogger(JacketCountService.class);

    private final RouteRepository routeRepository;
    private final JacketCountRepository jacketCountRepository;
    private final VoyageRepository voyageRepository;

    public JacketCountService(RouteRepository routeRepository,
                              JacketCountRepository jacketCountRepository,
                              VoyageRepository voyageRepository) {
        this.routeRepository = routeRepository;
        this.jacketCountRepository = jacketCountRepository;
        this.voyageRepository = voyageRepository;
    }

    /**
     * 当前时间来源。默认取系统时钟，测试可替换成固定时钟，
     * 以便确定性地构造“开航时刻已过/未到”的班次。
     */
    private Supplier<LocalDateTime> clock = LocalDateTime::now;

    public void setClock(Supplier<LocalDateTime> clock) {
        this.clock = clock != null ? clock : LocalDateTime::now;
    }

    /**
     * 测试/演练用故障注入开关：置为 true 时，在清点记录已落库之后、
     * 班次联动标记阶段抛出异常，验证整次提交回滚——
     * 清点记录与排班页标记都必须保持保存前的样子。
     */
    private final AtomicBoolean failAfterCountSaved = new AtomicBoolean(false);

    public void setFailAfterCountSaved(boolean fail) {
        this.failAfterCountSaved.set(fail);
    }

    /**
     * 提交一条航线的救生衣清点结果，并在同一事务内联动班次标记。
     */
    @Transactional
    public JacketCountDTO submitCount(Long routeId, JacketCountSubmitDTO dto) {
        // 1. 锁住航线行：同一航线的两次清点在此排队，不会交错写半份结果。
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new BusinessException(404, "航线不存在"));

        LocalDateTime now = clock.get();
        JacketCount count = jacketCountRepository.findByRouteId(routeId).orElse(null);

        if (count == null) {
            // 2a. 首次清点：expectedVersion 必须为 null。
            //     两人同时首交时，先拿锁者插入成功；后拿锁者会读到已插入记录走更新分支被版本校验拒绝；
            //     极端交错下 route_id 唯一约束再兜底，抛 DataIntegrityViolationException。
            if (dto.getExpectedVersion() != null) {
                throw new BusinessException(409,
                        "该航线已有他人先完成的清点，请刷新页面查看后再重新清点");
            }
            count = new JacketCount();
            count.setRoute(route);
            count.setRequiredCount(dto.getRequiredCount());
            count.setActualCount(dto.getActualCount());
            count.setCounter(dto.getCounter());
            count.setRemark(dto.getRemark());
            count.setCountTime(now);
            try {
                count = jacketCountRepository.saveAndFlush(count);
            } catch (DataIntegrityViolationException e) {
                // 并发首交的最后一道闸：只留下先写完的那份
                log.warn("航线 {} 的救生衣清点并发首交，唯一约束拒绝了后写者：{}", routeId, e.getMessage());
                throw new BusinessException(409,
                        "该航线已有他人先完成的清点，请刷新页面查看后再重新清点");
            }
        } else {
            // 2b. 再次清点：必须带对当前版本。两人同时交时，先写完者已把版本 +1，
            //     后写完者手里的 expectedVersion 过期——拒绝，不允许覆盖先写完的实点。
            if (dto.getExpectedVersion() == null
                    || !dto.getExpectedVersion().equals(count.getVersion())) {
                throw new BusinessException(409,
                        "该航线清点记录已被他人先更新（已留下先写完的那份实点），请刷新后重新清点");
            }
            count.setRequiredCount(dto.getRequiredCount());
            count.setActualCount(dto.getActualCount());
            count.setCounter(dto.getCounter());
            count.setRemark(dto.getRemark());
            count.setCountTime(now);
            count = jacketCountRepository.saveAndFlush(count);
        }

        // 3. 故障注入：清点已写库、班次尚未联动时失败，
        //    整笔事务必须回滚到保存前（清点记录不留痕、班次标记不变）。
        if (failAfterCountSaved.get()) {
            throw new BusinessException(500, "模拟故障：班次联动失败，整次清点保存回滚");
        }

        // 4. 同一事务内联动排班页班次标记。
        applyVoyageMarks(routeId, count.getActualCount() < count.getRequiredCount(), now);

        log.info("救生衣清点保存：航线 {}({})，额定 {} 件，实点 {} 件，清点人 {}，短缺={}",
                route.getRouteCode(), route.getRouteName(),
                count.getRequiredCount(), count.getActualCount(), count.getCounter(),
                count.getActualCount() < count.getRequiredCount());

        return toDTO(count);
    }

    /**
     * 按最新清点结果重算本航线各班次的缺衣标记。
     *
     * <p>只动开航时刻未到的班次，已开航的班次一律不碰；
     * 补够后，已经带着“缺衣待补”开过航的班次也不会被改回正常。</p>
     *
     * @param shortage 本次清点是否短缺
     * @param now      判定开航时刻的基准时间
     */
    private void applyVoyageMarks(Long routeId, boolean shortage, LocalDateTime now) {
        List<Voyage> voyages = voyageRepository.findByRouteIdOrderByDepartureTimeAscIdAsc(routeId);
        for (Voyage voyage : voyages) {
            // 已经开航的班次不要改：不管现在是正常还是缺衣待补，都保留开航当时的事实。
            if (!voyage.getDepartureTime().isAfter(now)) {
                continue;
            }
            if (shortage) {
                // 短缺：还没开航的班次标成缺衣待补
                voyage.setStatus(Voyage.STATUS_JACKET_SHORT);
            } else {
                // 补够：只把缺衣待补、且仍未开航的班次恢复正常；
                // 正常的班次保持正常；已开航的在上面已跳过，缺衣开过航的标记不会被洗掉。
                if (Voyage.STATUS_JACKET_SHORT.equals(voyage.getStatus())) {
                    voyage.setStatus(Voyage.STATUS_NORMAL);
                }
            }
            voyageRepository.save(voyage);
        }
    }

    /** 查一条航线的清点结果；从未清点返回 null（前端据此显示“未清点”，不阻塞排班）。 */
    @Transactional(readOnly = true)
    public JacketCountDTO getByRoute(Long routeId) {
        if (!routeRepository.existsById(routeId)) {
            throw new BusinessException(404, "航线不存在");
        }
        return jacketCountRepository.findByRouteId(routeId)
                .map(this::toDTO)
                .orElse(null);
    }

    /**
     * 清点一览：每条航线一行，未清点的航线也列出（实点/额定留空），
     * 供调度按航线核对额定件数、实点件数和清点人。
     */
    @Transactional(readOnly = true)
    public List<JacketCountDTO> listAll() {
        Map<Long, JacketCount> byRoute = jacketCountRepository.findAll().stream()
                .collect(Collectors.toMap(c -> c.getRoute().getId(), c -> c, (a, b) -> a, HashMap::new));

        return routeRepository.findAll().stream()
                .map(route -> {
                    JacketCount count = byRoute.get(route.getId());
                    if (count == null) {
                        JacketCountDTO empty = new JacketCountDTO();
                        empty.setRouteId(route.getId());
                        empty.setRouteCode(route.getRouteCode());
                        empty.setRouteName(route.getRouteName());
                        empty.setShortage(false);
                        empty.setShortageCount(0);
                        return empty;
                    }
                    return toDTO(count);
                })
                .collect(Collectors.toList());
    }

    private JacketCountDTO toDTO(JacketCount count) {
        JacketCountDTO dto = new JacketCountDTO();
        dto.setId(count.getId());
        dto.setRouteId(count.getRoute().getId());
        dto.setRouteCode(count.getRoute().getRouteCode());
        dto.setRouteName(count.getRoute().getRouteName());
        dto.setRequiredCount(count.getRequiredCount());
        dto.setActualCount(count.getActualCount());
        dto.setCounter(count.getCounter());
        boolean shortage = count.isShortage();
        dto.setShortage(shortage);
        dto.setShortageCount(shortage ? count.getRequiredCount() - count.getActualCount() : 0);
        dto.setVersion(count.getVersion());
        dto.setCountTime(count.getCountTime());
        dto.setRemark(count.getRemark());
        return dto;
    }
}
