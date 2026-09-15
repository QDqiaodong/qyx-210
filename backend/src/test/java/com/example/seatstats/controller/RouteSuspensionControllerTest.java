package com.example.seatstats.controller;

import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Seat;
import com.example.seatstats.repository.ChangeRecordRepository;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.SeatRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 封航 API 端到端测试（MockMvc + H2）：
 * 走完整 HTTP → 控制器 → 事务服务 → JPA 链路，验证封航成功/失败两种返回，
 * 以及按批次号查询挂载清单。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RouteSuspensionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RouteRepository routeRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ChangeRecordRepository changeRecordRepository;
    @Autowired
    private com.example.seatstats.service.SuspensionService suspensionService;

    private Route route;

    @BeforeEach
    void setUp() {
        changeRecordRepository.deleteAll();
        seatRepository.deleteAll();
        routeRepository.deleteAll();

        route = new Route();
        route.setRouteCode("R-HTTP-" + System.nanoTime());
        route.setRouteName("接口测试航线");
        route.setStartPort("甲码头");
        route.setEndPort("乙码头");
        route.setStatus("ACTIVE");
        route = routeRepository.save(route);

        for (int i = 1; i <= 2; i++) {
            Seat seat = new Seat();
            seat.setSeatCode("SH-" + i + "-" + System.nanoTime());
            seat.setMaterial("塑钢");
            seat.setWaitingArea("二号候船区");
            seat.setStatus("IN_USE");
            seat.setRoute(route);
            seatRepository.save(seat);
        }
    }

    @Test
    void suspendEndpoint_atomicallySuspends_andManifestIsRetrievable() throws Exception {
        String body = "{\"operator\":\"调度员乙\",\"reason\":\"汛期临时封航\"}";

        String response = mockMvc.perform(post("/api/routes/{id}/suspend", route.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.seatCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String batchNo = json.path("data").path("batchNo").asText();
        assertFalse(batchNo.isBlank());

        // 清单中每把椅子都记录了停运当时的原航线
        JsonNode items = json.path("data").path("items");
        for (JsonNode item : items) {
            assertEquals("SUSPEND", item.path("changeType").asText());
            assertEquals(route.getRouteCode(), item.path("oldRouteCode").asText());
            assertEquals(batchNo, item.path("suspendBatchNo").asText());
            assertTrue(item.path("newRouteCode").isNull());
        }

        // 看板统计接口：停运航线仍在列表里（没有被藏起来），但配套数为 0
        mockMvc.perform(get("/api/stats/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.routeId==" + route.getId() + ")].status").value(
                        org.hamcrest.Matchers.contains("INACTIVE")))
                .andExpect(jsonPath("$.data[?(@.routeId==" + route.getId() + ")].seatCount").value(
                        org.hamcrest.Matchers.contains(0)));

        // 调度按批次号取清单，供复航逐把核对
        mockMvc.perform(get("/api/routes/suspensions/{batchNo}/manifest", batchNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].oldRouteCode").value(route.getRouteCode()));

        // 批次一览
        mockMvc.perform(get("/api/routes/suspensions/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].batchNo").value(batchNo))
                .andExpect(jsonPath("$.data[0].seatCount").value(2));
    }

    @Test
    void suspendEndpoint_failureMidway_returns500_andNothingChanges() throws Exception {
        suspensionService.setFailAfterUnbind(true);
        try {
            mockMvc.perform(post("/api/routes/{id}/suspend", route.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk()) // GlobalExceptionHandler 把异常包成 ResponseDTO，HTTP 200
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("回滚")));
        } finally {
            suspensionService.setFailAfterUnbind(false);
        }

        // 事务回滚：航线仍运营、椅子仍挂载、无半份台账
        assertEquals("ACTIVE", routeRepository.findById(route.getId()).orElseThrow().getStatus());
        assertEquals(2L, seatRepository.countByRouteId(route.getId()));
        assertTrue(changeRecordRepository.findByChangeType("SUSPEND").isEmpty());
    }

    @Test
    void suspendEndpoint_alreadyStopped_returns400() throws Exception {
        mockMvc.perform(post("/api/routes/{id}/suspend", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/routes/{id}/suspend", route.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不是运营中")));
    }
}
