package com.example.seatstats.controller;

import com.example.seatstats.dto.ChangeRecordDTO;
import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.dto.RouteDTO;
import com.example.seatstats.dto.RouteSuspensionDTO;
import com.example.seatstats.service.RouteService;
import com.example.seatstats.service.SuspensionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "*")
public class RouteController {

    @Autowired
    private RouteService routeService;

    @Autowired
    private SuspensionService suspensionService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<RouteDTO>>> getAllRoutes() {
        List<RouteDTO> routes = routeService.getAllRoutes();
        return ResponseEntity.ok(ResponseDTO.success(routes));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<RouteDTO>> getRouteById(@PathVariable Long id) {
        RouteDTO route = routeService.getRouteById(id);
        return ResponseEntity.ok(ResponseDTO.success(route));
    }

    @PostMapping
    public ResponseEntity<ResponseDTO<RouteDTO>> createRoute(@Valid @RequestBody RouteDTO dto) {
        RouteDTO created = routeService.createRoute(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.success("创建成功", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDTO<RouteDTO>> updateRoute(@PathVariable Long id, @Valid @RequestBody RouteDTO dto) {
        RouteDTO updated = routeService.updateRoute(id, dto);
        return ResponseEntity.ok(ResponseDTO.success("更新成功", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> deleteRoute(@PathVariable Long id) {
        routeService.deleteRoute(id);
        return ResponseEntity.ok(ResponseDTO.success("删除成功", null));
    }

    /**
     * 汛期临时封航：原子地把航线上挂载的座椅全部拆下（看板配套数归零），
     * 同时留下本次封航的挂载清单台账（按批次号可查），两件事同生共死。
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<ResponseDTO<RouteSuspensionDTO>> suspendRoute(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> request) {
        String operator = request != null ? (String) request.get("operator") : null;
        String reason = request != null ? (String) request.get("reason") : null;

        RouteSuspensionDTO result = suspensionService.suspendRoute(id, operator, reason);
        return ResponseEntity.ok(ResponseDTO.success("封航停运成功，已拆下座椅 " + result.getSeatCount() + " 把", result));
    }

    /** 历次封航批次一览，调度选定批次后再取该批次的逐椅挂载清单 */
    @GetMapping("/suspensions/batches")
    public ResponseEntity<ResponseDTO<List<Map<String, Object>>>> listSuspensionBatches() {
        return ResponseEntity.ok(ResponseDTO.success(suspensionService.listSuspensionBatches()));
    }

    /** 按封航批次号查询停运当时的挂载清单，供复航逐把核对 */
    @GetMapping("/suspensions/{batchNo}/manifest")
    public ResponseEntity<ResponseDTO<List<ChangeRecordDTO>>> getSuspensionManifest(
            @PathVariable String batchNo) {
        return ResponseEntity.ok(ResponseDTO.success(suspensionService.getSuspensionManifest(batchNo)));
    }
}