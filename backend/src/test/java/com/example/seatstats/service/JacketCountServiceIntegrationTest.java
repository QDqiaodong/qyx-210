package com.example.seatstats.service;

import com.example.seatstats.dto.JacketCountDTO;
import com.example.seatstats.dto.JacketCountSubmitDTO;
import com.example.seatstats.dto.VoyageDTO;
import com.example.seatstats.entity.JacketCount;
import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Voyage;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.JacketCountRepository;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.VoyageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 救生衣按航线清点 + 排班联动集成测试（H2 内存库）。
 *
 * <p>验证业务要求：</p>
 * <ul>
 *   <li>清点按航线登记额定件数、实点件数、清点人；</li>
 *   <li>实点少于额定时，已排未开航班次标成“缺衣待补”，已开航班次不动；</li>
 *   <li>短缺期间不能再新排未开航班次；补够后新班可以排、未开航的缺衣班次自动恢复正常；</li>
 *   <li>已经标成缺衣待补且开航时刻已过的班次，补够后不会自动改回正常；</li>
 *   <li>两人同时交同一航线的清点，只留下先写完的那份实点；</li>
 *   <li>清点保存失败（联动阶段故障）时，清点记录与排班标记都回到保存前的样子。</li>
 * </ul>
 */
@SpringBootTest
class JacketCountServiceIntegrationTest {

    /** 固定时钟：测试里的“现在”固定在此时刻，班次开航时刻相对它构造，避免用真实时间产生抖动 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 10, 0, 0);

    @Autowired
    private JacketCountService jacketCountService;

    @Autowired
    private VoyageService voyageService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private JacketCountRepository jacketCountRepository;

    @Autowired
    private VoyageRepository voyageRepository;

    private Route route;

    @BeforeEach
    void setUp() {
        voyageRepository.deleteAll();
        jacketCountRepository.deleteAll();
        routeRepository.deleteAll();

        route = new Route();
        route.setRouteCode("R-LJ-" + System.nanoTime());
        route.setRouteName("救生衣清点测试航线");
        route.setStartPort("甲码头");
        route.setEndPort("乙码头");
        route.setStatus("ACTIVE");
        route = routeRepository.save(route);

        jacketCountService.setClock(() -> NOW);
        voyageService.setClock(() -> NOW);
        jacketCountService.setFailAfterCountSaved(false);
    }

    @AfterEach
    void tearDown() {
        // 新表与既有封航测试共用同一 H2 实例：每个用例结束就清干净，
        // 避免最后一个用例留下的清点/班次行以外键挡住其他测试类的航线清理。
        voyageRepository.deleteAll();
        jacketCountRepository.deleteAll();
        jacketCountService.setClock(LocalDateTime::now);
        voyageService.setClock(LocalDateTime::now);
        jacketCountService.setFailAfterCountSaved(false);
    }

    private Voyage persistVoyage(String code, LocalDateTime departure, String status) {
        Voyage v = new Voyage();
        v.setVoyageCode(code + "-" + System.nanoTime());
        v.setRoute(route);
        v.setDepartureTime(departure);
        v.setVesselName("测试船");
        v.setStatus(status);
        return voyageRepository.save(v);
    }

    private JacketCountSubmitDTO submit(int required, int actual, String counter, Long expectedVersion) {
        JacketCountSubmitDTO dto = new JacketCountSubmitDTO();
        dto.setRequiredCount(required);
        dto.setActualCount(actual);
        dto.setCounter(counter);
        dto.setExpectedVersion(expectedVersion);
        return dto;
    }

    @Test
    void submitCount_recordsRequiredActualAndCounterPerRoute() {
        JacketCountDTO result = jacketCountService.submitCount(route.getId(),
                submit(50, 50, "清点员甲", null));

        assertEquals(50, result.getRequiredCount());
        assertEquals(50, result.getActualCount());
        assertEquals("清点员甲", result.getCounter());
        assertFalse(result.isShortage());
        assertEquals(0, result.getShortageCount());
        assertEquals(0L, result.getVersion(), "首次落库版本为 0");

        JacketCount persisted = jacketCountRepository.findByRouteId(route.getId()).orElseThrow();
        assertEquals(50, persisted.getRequiredCount());
        assertEquals(50, persisted.getActualCount());
        assertEquals("清点员甲", persisted.getCounter());
    }

    @Test
    void shortage_marksFutureVoyagesShort_leavesDepartedVoyagesUntouched() {
        // 两个还没开航的班 + 一个开航时刻已经过去的班（正常）
        Voyage future1 = persistVoyage("V-F1", NOW.plusHours(1), Voyage.STATUS_NORMAL);
        Voyage future2 = persistVoyage("V-F2", NOW.plusHours(2), Voyage.STATUS_NORMAL);
        Voyage departed = persistVoyage("V-D1", NOW.minusHours(1), Voyage.STATUS_NORMAL);

        JacketCountDTO result = jacketCountService.submitCount(route.getId(),
                submit(50, 45, "清点员甲", null));

        assertTrue(result.isShortage(), "实点 45 少于额定 50，必须是短缺");
        assertEquals(5, result.getShortageCount());

        // 未开航班次标成缺衣待补
        assertEquals(Voyage.STATUS_JACKET_SHORT,
                voyageRepository.findById(future1.getId()).orElseThrow().getStatus());
        assertEquals(Voyage.STATUS_JACKET_SHORT,
                voyageRepository.findById(future2.getId()).orElseThrow().getStatus());

        // 已经开航的班次不要改
        assertEquals(Voyage.STATUS_NORMAL,
                voyageRepository.findById(departed.getId()).orElseThrow().getStatus(),
                "已开航班次不能被清点联动修改");
    }

    @Test
    void shortage_blocksNewVoyageScheduling() {
        jacketCountService.submitCount(route.getId(), submit(50, 40, "清点员甲", null));

        VoyageDTO dto = new VoyageDTO();
        dto.setRouteId(route.getId());
        dto.setDepartureTime(NOW.plusHours(3));
        dto.setVesselName("新班船");

        BusinessException ex = assertThrows(BusinessException.class, () -> voyageService.createVoyage(dto));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("短缺"));
        assertEquals(0, voyageRepository.findByRouteIdOrderByDepartureTimeAscIdAsc(route.getId()).stream()
                .filter(v -> "新班船".equals(v.getVesselName())).count(),
                "短缺期间新班不能真正排上去，而不是只在旁边写个短缺数字还放行");
    }

    @Test
    void restock_unblocksNewVoyages_andRestoresOnlyFutureShortVoyages() {
        // 已排：一个未来班（缺衣待补）、一个已开航班（缺衣待补——带着缺衣事实开过了航）
        Voyage futureShort = persistVoyage("V-FS", NOW.plusHours(1), Voyage.STATUS_JACKET_SHORT);
        Voyage departedShort = persistVoyage("V-DS", NOW.minusHours(1), Voyage.STATUS_JACKET_SHORT);
        Voyage departedNormal = persistVoyage("V-DN", NOW.minusHours(2), Voyage.STATUS_NORMAL);

        // 先短缺，再补够（带版本 0）
        jacketCountService.submitCount(route.getId(), submit(50, 40, "清点员甲", null));
        assertEquals(Voyage.STATUS_JACKET_SHORT,
                voyageRepository.findById(futureShort.getId()).orElseThrow().getStatus());

        Long version = jacketCountRepository.findByRouteId(route.getId()).orElseThrow().getVersion();
        JacketCountDTO restocked = jacketCountService.submitCount(route.getId(),
                submit(50, 50, "清点员乙", version));
        assertFalse(restocked.isShortage());

        // 未开航的缺衣班次自动恢复正常
        assertEquals(Voyage.STATUS_NORMAL,
                voyageRepository.findById(futureShort.getId()).orElseThrow().getStatus(),
                "补够后，开航时刻未到的缺衣待补班次应恢复正常");

        // 已开航的缺衣待补班次不要自动改回正常（历史留存）
        assertEquals(Voyage.STATUS_JACKET_SHORT,
                voyageRepository.findById(departedShort.getId()).orElseThrow().getStatus(),
                "已开航的缺衣待补班次补够后也不能自动改回正常");

        // 已开航的正常班依旧不动
        assertEquals(Voyage.STATUS_NORMAL,
                voyageRepository.findById(departedNormal.getId()).orElseThrow().getStatus());

        // 补够后新班可以再排
        VoyageDTO dto = new VoyageDTO();
        dto.setRouteId(route.getId());
        dto.setDepartureTime(NOW.plusHours(4));
        dto.setVesselName("补够后新班");
        VoyageDTO created = voyageService.createVoyage(dto);
        assertEquals(Voyage.STATUS_NORMAL, created.getStatus());
    }

    @Test
    void failureDuringSync_rollsBackCountAndLeavesVoyageMarksUntouched() {
        Voyage future = persistVoyage("V-FX", NOW.plusHours(1), Voyage.STATUS_NORMAL);

        // 故障注入：清点记录写库后、班次联动时失败
        jacketCountService.setFailAfterCountSaved(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> jacketCountService.submitCount(route.getId(), submit(50, 40, "清点员甲", null)));
        assertTrue(ex.getMessage().contains("回滚"));

        // 清点记录没有留下半份
        assertTrue(jacketCountRepository.findByRouteId(route.getId()).isEmpty(),
                "保存失败时清点记录必须整笔回滚，不能留下已写的实点");

        // 排班页各班次标记仍是保存前的样子
        assertEquals(Voyage.STATUS_NORMAL,
                voyageRepository.findById(future.getId()).orElseThrow().getStatus(),
                "保存失败时已排班次标记必须保持保存前的正常状态");
    }

    @Test
    void concurrentSubmissions_onlyFirstFinishedActualCountWins() throws Exception {
        // 两个清点员同时打开同一条航线的清点单（都没有历史版本 = 同时首交）
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<JacketCountDTO> f1 = pool.submit(() ->
                    jacketCountService.submitCount(route.getId(), submit(50, 48, "先写完者", null)));
            Future<JacketCountDTO> f2 = pool.submit(() ->
                    jacketCountService.submitCount(route.getId(), submit(50, 30, "后写完者", null)));

            JacketCountDTO winner = null;
            BusinessException loser = null;
            try {
                winner = f1.get();
            } catch (Exception e) {
                loser = asBusiness(e);
            }
            try {
                JacketCountDTO other = f2.get();
                if (winner == null) {
                    winner = other;
                } else {
                    fail("两次并发首交必须有一方被拒绝");
                }
            } catch (Exception e) {
                loser = asBusiness(e);
            }

            assertNotNull(winner, "必须有一份清点成功");
            assertNotNull(loser, "必须有一份清点被拒绝");
            assertEquals(409, loser.getCode());
            assertTrue(loser.getMessage().contains("先"));

            // 库里只剩先写完的那份实点
            List<JacketCount> all = jacketCountRepository.findAll();
            assertEquals(1, all.size(), "同一航线只能留下一份清点");
            assertEquals(winner.getActualCount(), all.get(0).getActualCount());
            assertEquals(winner.getCounter(), all.get(0).getCounter());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleVersionUpdate_isRejectedAndDoesNotOverwriteFirstWriter() {
        // 航线已有初始清点（版本 0）：两个清点员都看到版本 0 后各自填写
        jacketCountService.submitCount(route.getId(), submit(50, 45, "初始清点", null));
        assertEquals(0L, jacketCountRepository.findByRouteId(route.getId()).orElseThrow().getVersion());

        // 先写完者基于版本 0 完成更新（0 → 1）
        jacketCountService.submitCount(route.getId(), submit(50, 48, "先写完者", 0L));
        assertEquals(1L, jacketCountRepository.findByRouteId(route.getId()).orElseThrow().getVersion());

        // 后写完者拿着过期的版本 0 晚提交——拒绝，不能覆盖先写完的实点
        BusinessException ex = assertThrows(BusinessException.class,
                () -> jacketCountService.submitCount(route.getId(), submit(50, 20, "后写完者", 0L)));
        assertEquals(409, ex.getCode());

        JacketCount kept = jacketCountRepository.findByRouteId(route.getId()).orElseThrow();
        assertEquals(48, kept.getActualCount(), "后写者不能覆盖先写完的实点");
        assertEquals("先写完者", kept.getCounter());
        assertEquals(1L, kept.getVersion());

        // 刷新拿到新版本后可以正常再清点
        JacketCountDTO again = jacketCountService.submitCount(route.getId(),
                submit(50, 50, "复核者", 1L));
        assertEquals(50, again.getActualCount());
    }

    @Test
    void listAll_includesRoutesWithoutCountAndShortageFlags() {
        // route 尚未清点
        var all = jacketCountService.listAll();
        var row = all.stream().filter(r -> r.getRouteId().equals(route.getId())).findFirst().orElseThrow();
        assertNull(row.getRequiredCount());
        assertNull(row.getActualCount());
        assertNull(row.getCounter());
        assertFalse(row.isShortage());

        jacketCountService.submitCount(route.getId(), submit(10, 7, "清点员甲", null));
        row = jacketCountService.listAll().stream()
                .filter(r -> r.getRouteId().equals(route.getId())).findFirst().orElseThrow();
        assertEquals(10, row.getRequiredCount());
        assertEquals(7, row.getActualCount());
        assertEquals("清点员甲", row.getCounter());
        assertTrue(row.isShortage());
        assertEquals(3, row.getShortageCount());
    }

    private BusinessException asBusiness(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof BusinessException be) {
                return be;
            }
            cur = cur.getCause();
        }
        throw new AssertionError("期望 BusinessException，实际：" + t, t);
    }
}
