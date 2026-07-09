package com.example.seatstats.controller;

import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.dto.SeatStatDTO;
import com.example.seatstats.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin(origins = "*")
public class StatsController {

    @Autowired
    private StatsService statsService;

    @GetMapping("/routes")
    public ResponseEntity<ResponseDTO<List<SeatStatDTO>>> getRouteSeatStats() {
        List<SeatStatDTO> stats = statsService.getRouteSeatStats();
        return ResponseEntity.ok(ResponseDTO.success(stats));
    }

    @GetMapping("/routes/{routeId}")
    public ResponseEntity<ResponseDTO<SeatStatDTO>> getRouteSeatStat(@PathVariable Long routeId) {
        SeatStatDTO stat = statsService.getRouteSeatStat(routeId);
        if (stat == null) {
            return ResponseEntity.ok(ResponseDTO.error(404, "航线不存在"));
        }
        return ResponseEntity.ok(ResponseDTO.success(stat));
    }

    @GetMapping("/summary")
    public ResponseEntity<ResponseDTO<Map<String, Object>>> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSeats", statsService.getTotalSeatCount());
        summary.put("totalRoutes", statsService.getTotalRouteCount());
        return ResponseEntity.ok(ResponseDTO.success(summary));
    }
}