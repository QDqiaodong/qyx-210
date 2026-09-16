package com.example.seatstats.controller;

import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Voyage;
import com.example.seatstats.repository.JacketCountRepository;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.VoyageRepository;
import com.example.seatstats.service.JacketCountService;
import com.example.seatstats.service.VoyageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 救生衣清点 + 排班 API 端到端测试（MockMvc + H2）。
 * 走完整 HTTP → 控制器 → 事务服务 → JPA 链路，验证清点联动、短缺拦截与失败回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
class JacketVoyageControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 10, 0, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RouteRepository routeRepository;
    @Autowired
    private JacketCountRepository jacketCountRepository;
    @Autowired
    private VoyageRepository voyageRepository;
    @Autowired
    private JacketCountService jacketCountService;
    @Autowired
    private VoyageService voyageService;

    private Route route;

    @BeforeEach
    void setUp() {
        voyageRepository.deleteAll();
        jacketCountRepository.deleteAll();
        routeRepository.deleteAll();

        route = new Route();
        route.setRouteCode("R-HTTP-LJ-" + System.nanoTime());
        route.setRouteName("接口清点航线");
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
        voyageRepository.deleteAll();
        jacketCountRepository.deleteAll();
        jacketCountService.setClock(LocalDateTime::now);
        voyageService.setClock(LocalDateTime::now);
        jacketCountService.setFailAfterCountSaved(false);
    }

    private void persistVoyage(LocalDateTime departure, String status) {
        Voyage v = new Voyage();
        v.setVoyageCode("VH-" + System.nanoTime());
        v.setRoute(route);
        v.setDepartureTime(departure);
        v.setStatus(status);
        voyageRepository.save(v);
    }

    @Test
    void shortageCount_marksFutureVoyages_andBlocksNewVoyage() throws Exception {
        persistVoyage(NOW.plusHours(1), Voyage.STATUS_NORMAL);   // 未开航
        persistVoyage(NOW.minusHours(1), Voyage.STATUS_NORMAL);  // 已开航

        String body = "{\"requiredCount\":50,\"actualCount\":46,\"counter\":\"清点员甲\"}";

        mockMvc.perform(put("/api/jacket-counts/route/{routeId}", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.requiredCount").value(50))
                .andExpect(jsonPath("$.data.actualCount").value(46))
                .andExpect(jsonPath("$.data.counter").value("清点员甲"))
                .andExpect(jsonPath("$.data.shortage").value(true))
                .andExpect(jsonPath("$.data.shortageCount").value(4));

        // 排班页：未开航班缺衣待补，已开航班保持正常
        mockMvc.perform(get("/api/voyages").param("routeId", String.valueOf(route.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        long shortCount = voyageRepository.findAll().stream()
                .filter(v -> Voyage.STATUS_JACKET_SHORT.equals(v.getStatus()))
                .count();
        long normalCount = voyageRepository.findAll().stream()
                .filter(v -> Voyage.STATUS_NORMAL.equals(v.getStatus()))
                .count();
        assertEquals(1, shortCount, "只有未开航班被标缺衣待补");
        assertEquals(1, normalCount, "已开航班保持原样");

        // 短缺期间新班排不上：返回 409 业务码
        String newVoyage = "{\"routeId\":" + route.getId()
                + ",\"departureTime\":\"2026-09-16 15:00:00\",\"vesselName\":\"新班船\"}";
        mockMvc.perform(post("/api/voyages")
                        .contentType(MediaType.APPLICATION_JSON).content(newVoyage))
                .andExpect(status().isOk()) // GlobalExceptionHandler 包成 ResponseDTO，HTTP 200
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("短缺")));
    }

    @Test
    void failedCountSave_rollsBackAndKeysVoyageMarksAsBefore() throws Exception {
        persistVoyage(NOW.plusHours(1), Voyage.STATUS_NORMAL);
        jacketCountService.setFailAfterCountSaved(true);

        String body = "{\"requiredCount\":50,\"actualCount\":40,\"counter\":\"清点员甲\"}";
        mockMvc.perform(put("/api/jacket-counts/route/{routeId}", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("回滚")));

        // 清点记录未留下
        assertFalse(jacketCountRepository.findByRouteId(route.getId()).isPresent());
        // 排班页标记仍是保存前的正常
        voyageRepository.findAll().forEach(v ->
                assertEquals(Voyage.STATUS_NORMAL, v.getStatus(), "保存失败时班次标记必须保持保存前样子"));
    }

    @Test
    void concurrentSecondSubmitter_gets409AndFirstCountStays() throws Exception {
        // 已有初始清点（版本 0）
        String init = "{\"requiredCount\":50,\"actualCount\":45,\"counter\":\"初始清点\"}";
        mockMvc.perform(put("/api/jacket-counts/route/{routeId}", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(init))
                .andExpect(jsonPath("$.data.version").value(0));

        // 先写完者基于版本 0 更新（0 → 1）
        String first = "{\"requiredCount\":50,\"actualCount\":48,\"counter\":\"先写完者\",\"expectedVersion\":0}";
        mockMvc.perform(put("/api/jacket-counts/route/{routeId}", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(jsonPath("$.data.version").value(1));

        // 后写完者持过期版本 0 提交被拒
        String stale = "{\"requiredCount\":50,\"actualCount\":20,\"counter\":\"后写完者\",\"expectedVersion\":0}";
        mockMvc.perform(put("/api/jacket-counts/route/{routeId}", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("先")));

        mockMvc.perform(get("/api/jacket-counts/route/{routeId}", route.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualCount").value(48))
                .andExpect(jsonPath("$.data.counter").value("先写完者"));
    }
}
