package com.example.seatstats.controller;

import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.dto.SeatDTO;
import com.example.seatstats.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seats")
@CrossOrigin(origins = "*")
public class SeatController {

    @Autowired
    private SeatService seatService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<SeatDTO>>> getAllSeats() {
        List<SeatDTO> seats = seatService.getAllSeats();
        return ResponseEntity.ok(ResponseDTO.success(seats));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<SeatDTO>> getSeatById(@PathVariable Long id) {
        SeatDTO seat = seatService.getSeatById(id);
        return ResponseEntity.ok(ResponseDTO.success(seat));
    }

    @GetMapping("/route/{routeId}")
    public ResponseEntity<ResponseDTO<List<SeatDTO>>> getSeatsByRoute(@PathVariable Long routeId) {
        List<SeatDTO> seats = seatService.getSeatsByRoute(routeId);
        return ResponseEntity.ok(ResponseDTO.success(seats));
    }

    @GetMapping("/size/{seatCode}")
    public ResponseEntity<ResponseDTO<String>> getSeatSizeSpec(@PathVariable String seatCode) {
        String sizeSpec = seatService.getSeatSizeSpecFromCache(seatCode);
        return ResponseEntity.ok(ResponseDTO.success(sizeSpec));
    }

    @PostMapping
    public ResponseEntity<ResponseDTO<SeatDTO>> createSeat(@Valid @RequestBody SeatDTO dto) {
        SeatDTO created = seatService.createSeat(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.success("创建成功", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDTO<SeatDTO>> updateSeat(@PathVariable Long id, @Valid @RequestBody SeatDTO dto) {
        SeatDTO updated = seatService.updateSeat(id, dto);
        return ResponseEntity.ok(ResponseDTO.success("更新成功", updated));
    }

    @PostMapping("/{id}/bind")
    public ResponseEntity<ResponseDTO<SeatDTO>> bindRoute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Long routeId = ((Number) request.get("routeId")).longValue();
        String operator = (String) request.getOrDefault("operator", "SYSTEM");
        String reason = (String) request.get("reason");

        SeatDTO updated = seatService.bindRoute(id, routeId, operator, reason);
        return ResponseEntity.ok(ResponseDTO.success("绑定成功", updated));
    }

    @PostMapping("/{id}/unbind")
    public ResponseEntity<ResponseDTO<SeatDTO>> unbindRoute(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        String operator = (String) request.getOrDefault("operator", "SYSTEM");
        String reason = (String) request.get("reason");

        SeatDTO updated = seatService.unbindRoute(id, operator, reason);
        return ResponseEntity.ok(ResponseDTO.success("解绑成功", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> deleteSeat(@PathVariable Long id) {
        seatService.deleteSeat(id);
        return ResponseEntity.ok(ResponseDTO.success("删除成功", null));
    }
}