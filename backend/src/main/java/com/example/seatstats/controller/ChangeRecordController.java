package com.example.seatstats.controller;

import com.example.seatstats.dto.ChangeRecordDTO;
import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.service.ChangeRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/records")
@CrossOrigin(origins = "*")
public class ChangeRecordController {

    @Autowired
    private ChangeRecordService changeRecordService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<ChangeRecordDTO>>> getAllRecords() {
        List<ChangeRecordDTO> records = changeRecordService.getAllRecords();
        return ResponseEntity.ok(ResponseDTO.success(records));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<ChangeRecordDTO>> getRecordById(@PathVariable Long id) {
        ChangeRecordDTO record = changeRecordService.getRecordById(id);
        return ResponseEntity.ok(ResponseDTO.success(record));
    }

    @GetMapping("/seat/{seatId}")
    public ResponseEntity<ResponseDTO<List<ChangeRecordDTO>>> getRecordsBySeat(@PathVariable Long seatId) {
        List<ChangeRecordDTO> records = changeRecordService.getRecordsBySeat(seatId);
        return ResponseEntity.ok(ResponseDTO.success(records));
    }

    @GetMapping("/type/{changeType}")
    public ResponseEntity<ResponseDTO<List<ChangeRecordDTO>>> getRecordsByType(@PathVariable String changeType) {
        List<ChangeRecordDTO> records = changeRecordService.getRecordsByType(changeType);
        return ResponseEntity.ok(ResponseDTO.success(records));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> deleteRecord(@PathVariable Long id) {
        changeRecordService.deleteRecord(id);
        return ResponseEntity.ok(ResponseDTO.success("删除成功", null));
    }
}