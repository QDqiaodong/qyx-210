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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 封航停运原子性集成测试（H2 内存库）。
 *
 * <p>验证业务要求：</p>
 * <ul>
 *   <li>成功路径：椅子全部拆下 + 看板配套数归零 + 逐椅封航清单（同批次号）同时生效；</li>
 *   <li>失败路径：拆到一半失败时整次封航回滚——航线仍运营中、椅子全部回到原航线、
 *       看板配套数不变、没有留下半份封航台账；</li>
 *   <li>停运后的航线不能重复封航；</li>
 *   <li>普通航线编辑不能直接改运营状态（必须走封航流程）。</li>
 * </ul>
 */
@SpringBootTest
class SuspensionServiceIntegrationTest {

    @Autowired
    private SuspensionService suspensionService;

    @Autowired
    private RouteService routeService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ChangeRecordRepository changeRecordRepository;

    private Route activeRoute;
    private List<Seat> boundSeats;

    @BeforeEach
    void setUp() {
        changeRecordRepository.deleteAll();
        seatRepository.deleteAll();
        routeRepository.deleteAll();

        activeRoute = new Route();
        activeRoute.setRouteCode("R-FLOOD-" + System.nanoTime());
        activeRoute.setRouteName("汛期测试航线");
        activeRoute.setStartPort("甲码头");
        activeRoute.setEndPort("乙码头");
        activeRoute.setStatus("ACTIVE");
        activeRoute = routeRepository.save(activeRoute);

        // 另一条运营航线，验证封航不波及其他航线
        Route otherRoute = new Route();
        otherRoute.setRouteCode("R-OTHER-" + System.nanoTime());
        otherRoute.setRouteName("无关航线");
        otherRoute.setStartPort("丙码头");
        otherRoute.setEndPort("丁码头");
        otherRoute.setStatus("ACTIVE");
        routeRepository.save(otherRoute);

        Seat s1 = newSeat("S-1", activeRoute);
        Seat s2 = newSeat("S-2", activeRoute);
        Seat s3 = newSeat("S-3", activeRoute);
        Seat other = newSeat("S-OTHER", otherRoute);
        boundSeats = List.of(s1, s2, s3);
    }

    private Seat newSeat(String code, Route route) {
        Seat seat = new Seat();
        seat.setSeatCode(code + "-" + System.nanoTime());
        seat.setMaterial("钢制");
        seat.setWaitingArea("一号候船区");
        seat.setStatus("IN_USE");
        seat.setRoute(route);
        return seatRepository.save(seat);
    }

    @Test
    void suspendRoute_success_unbindsAllSeats_zeroesStats_andWritesManifest() {
        long beforeCount = seatRepository.countByRouteId(activeRoute.getId());
        assertEquals(3, beforeCount, "前置条件：航线配套 3 把椅子");

        RouteSuspensionDTO result = suspensionService.suspendRoute(
                activeRoute.getId(), "调度员甲", "汛期临时封航");

        // —— 资产看板侧：航线停运且配套件数立即归零 ——
        Route reloaded = routeRepository.findById(activeRoute.getId()).orElseThrow();
        assertEquals("INACTIVE", reloaded.getStatus(), "航线必须变为停运");
        assertEquals(0L, seatRepository.countByRouteId(activeRoute.getId()),
                "看板配套件数必须归零，不能有椅子仍挂在已停运航线上");

        // 椅子实体确实被拆下（route 为空）
        for (Seat seat : boundSeats) {
            Seat fresh = seatRepository.findById(seat.getId()).orElseThrow();
            assertNull(fresh.getRoute(), "椅子 " + fresh.getSeatCode() + " 必须已拆下");
        }

        // —— 调度台账侧：返回值即停运当时的逐椅挂载清单 ——
        assertEquals(3, result.getItems().size(), "清单必须覆盖每一把椅子");
        assertEquals(result.getBatchNo(), result.getItems().get(0).getSuspendBatchNo());
        for (ChangeRecordDTO item : result.getItems()) {
            assertEquals("SUSPEND", item.getChangeType());
            assertEquals(activeRoute.getRouteCode(), item.getOldRouteCode(),
                    "清单必须记录每把椅子停运当时原先挂在哪");
            assertEquals(activeRoute.getRouteName(), item.getOldRouteName());
            assertNull(item.getNewRouteCode(), "封航是拆下而非改挂，新航线必须为空");
            assertEquals("调度员甲", item.getOperator());
        }

        // 台账持久化且可按批次号复查（复航核对入口）
        List<ChangeRecord> persisted =
                changeRecordRepository.findBySuspendBatchNoOrderByIdAsc(result.getBatchNo());
        assertEquals(3, persisted.size(), "封航清单必须落库，供复航逐把核对");

        // 既有台账没有被删改：椅子编号在清单中逐把可对
        for (Seat seat : boundSeats) {
            boolean found = persisted.stream()
                    .anyMatch(r -> r.getSeat().getId().equals(seat.getId()));
            assertTrue(found, "原挂在航线上的椅子 " + seat.getId() + " 必须在封航清单中");
        }
    }

    @Test
    void suspendRoute_failureMidway_rollsBackEverything_routeStaysActive() {
        // 故障注入：在座椅已解绑、航线尚未翻转时模拟“某把椅子写不出台账/中途失败”
        suspensionService.setFailAfterUnbind(true);
        try {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> suspensionService.suspendRoute(activeRoute.getId(), "调度员甲", "汛期临时封航"));
            assertTrue(ex.getMessage().contains("回滚"));
        } finally {
            suspensionService.setFailAfterUnbind(false);
        }

        // 航线仍保持运营中——不能出现“已停运却挂着椅子/配套先清零”的中间态
        Route reloaded = routeRepository.findById(activeRoute.getId()).orElseThrow();
        assertEquals("ACTIVE", reloaded.getStatus(), "失败后航线必须仍为运营中");

        // 已经拆过的椅子全部回到这条航线
        assertEquals(3L, seatRepository.countByRouteId(activeRoute.getId()),
                "失败后已拆下的椅子必须全部回挂，看板配套数恢复原值");
        for (Seat seat : boundSeats) {
            Seat fresh = seatRepository.findById(seat.getId()).orElseThrow();
            assertNotNull(fresh.getRoute(), "椅子必须仍挂在航线上");
            assertEquals(activeRoute.getId(), fresh.getRoute().getId(),
                    "椅子必须回到原航线，不能被改挂或拆走");
        }

        // 没有留下半份封航台账
        List<ChangeRecord> suspendRecords = changeRecordRepository.findByChangeType("SUSPEND");
        assertTrue(suspendRecords.isEmpty(), "失败的封航不能留下任何台账记录");
    }

    @Test
    void suspendRoute_alreadyInactive_isRejected() {
        suspensionService.suspendRoute(activeRoute.getId(), "调度员甲", "汛期临时封航");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> suspensionService.suspendRoute(activeRoute.getId(), "调度员甲", "再次封航"));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不是运营中"));

        // 第二次封航不能重复造清单
        List<ChangeRecord> all = changeRecordRepository.findByChangeType("SUSPEND");
        assertEquals(3, all.size(), "重复封航不得再生成清单");
    }

    @Test
    void suspendRoute_routeNotFound_throws404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> suspensionService.suspendRoute(999999L, "调度员甲", null));
        assertEquals(404, ex.getCode());
    }

    @Test
    void updateRoute_cannotFlipStatusDirectly() {
        com.example.seatstats.dto.RouteDTO dto = new com.example.seatstats.dto.RouteDTO();
        dto.setRouteCode(activeRoute.getRouteCode());
        dto.setRouteName(activeRoute.getRouteName());
        dto.setStartPort(activeRoute.getStartPort());
        dto.setEndPort(activeRoute.getEndPort());
        dto.setStatus("INACTIVE"); // 试图通过普通编辑直接停运

        BusinessException ex = assertThrows(BusinessException.class,
                () -> routeService.updateRoute(activeRoute.getId(), dto));
        assertEquals(400, ex.getCode());

        // 航线仍运营、椅子仍挂载、看板数不变
        assertEquals("ACTIVE", routeRepository.findById(activeRoute.getId()).orElseThrow().getStatus());
        assertEquals(3L, seatRepository.countByRouteId(activeRoute.getId()));
    }

    @Test
    void statsReflectZeroImmediatelyAfterSuspend() {
        suspensionService.suspendRoute(activeRoute.getId(), "调度员甲", "汛期临时封航");

        // 看板统计服务直接读库（缓存关闭），停运航线配套数为 0，航线本身仍在看板可见（不是被藏起来）
        var stat = seatService.countSeatsByRoute(activeRoute.getId());
        assertEquals(0L, stat, "停运航线在看板中的配套件数为 0");

        var routes = routeService.getAllRoutes();
        assertTrue(routes.stream().anyMatch(r -> r.getId().equals(activeRoute.getId())),
                "停运航线仍须在航线列表/看板中可见，不能只是被隐藏");
    }
}
