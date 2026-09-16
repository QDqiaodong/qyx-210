package com.example.seatstats.controller;

import com.example.seatstats.dto.JacketCountDTO;
import com.example.seatstats.dto.JacketCountSubmitDTO;
import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.service.JacketCountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 救生衣按航线清点。
 *
 * <p>清点保存与排班页班次标记在同一事务内联动：任一失败整笔回滚，
 * 保存失败时排班页上各班次标记仍是保存前的样子。</p>
 */
@RestController
@RequestMapping("/api/jacket-counts")
@CrossOrigin(origins = "*")
public class JacketCountController {

    private final JacketCountService jacketCountService;

    public JacketCountController(JacketCountService jacketCountService) {
        this.jacketCountService = jacketCountService;
    }

    /** 清点一览：每条航线的额定件数、实点件数、清点人（未清点航线也列出） */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<JacketCountDTO>>> listAll() {
        return ResponseEntity.ok(ResponseDTO.success(jacketCountService.listAll()));
    }

    /** 查单条航线的清点结果；从未清点时 data 为 null */
    @GetMapping("/route/{routeId}")
    public ResponseEntity<ResponseDTO<JacketCountDTO>> getByRoute(@PathVariable Long routeId) {
        return ResponseEntity.ok(ResponseDTO.success(jacketCountService.getByRoute(routeId)));
    }

    /**
     * 提交（或更新）某航线的清点结果，同一事务内联动班次标记。
     * expectedVersion 为页面上看到的版本；两人同时交时只有先写完的那份留下。
     */
    @PutMapping("/route/{routeId}")
    public ResponseEntity<ResponseDTO<JacketCountDTO>> submitCount(
            @PathVariable Long routeId,
            @Valid @RequestBody JacketCountSubmitDTO dto) {
        JacketCountDTO result = jacketCountService.submitCount(routeId, dto);
        String message = result.isShortage()
                ? "清点已保存：救生衣短缺 " + result.getShortageCount()
                + " 件，未开航班次已标为缺衣待补，补齐前不能新排班次"
                : "清点已保存：救生衣足额，缺衣待补的未开航班次已恢复正常，可继续排新班";
        return ResponseEntity.status(HttpStatus.OK).body(ResponseDTO.success(message, result));
    }
}
