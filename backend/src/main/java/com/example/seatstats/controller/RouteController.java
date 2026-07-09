package com.example.seatstats.controller;

import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.dto.RouteDTO;
import com.example.seatstats.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "*")
public class RouteController {

    @Autowired
    private RouteService routeService;

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
}