package com.example.seatstats.controller;

import com.example.seatstats.dto.ResponseDTO;
import com.example.seatstats.dto.VoyageDTO;
import com.example.seatstats.service.VoyageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 排班（班次）接口。
 *
 * <p>航线救生衣实点少于额定期间，新排班次被拒绝（409）；
 * 已排未开航班次的“缺衣待补”标记由清点事务联动，不在这里手工改。</p>
 */
@RestController
@RequestMapping("/api/voyages")
@CrossOrigin(origins = "*")
public class VoyageController {

    private final VoyageService voyageService;

    public VoyageController(VoyageService voyageService) {
        this.voyageService = voyageService;
    }

    /** 排班页：全部班次，按开航时刻升序，含正常/缺衣待补标记与是否已开航 */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<VoyageDTO>>> list(@RequestParam(required = false) Long routeId) {
        List<VoyageDTO> voyages = (routeId == null)
                ? voyageService.listVoyages()
                : voyageService.listByRoute(routeId);
        return ResponseEntity.ok(ResponseDTO.success(voyages));
    }

    /** 新排班次；航线救生衣短缺时拒绝，缺衣补齐后才能再排 */
    @PostMapping
    public ResponseEntity<ResponseDTO<VoyageDTO>> create(@Valid @RequestBody VoyageDTO dto) {
        VoyageDTO created = voyageService.createVoyage(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success("排班成功", created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> delete(@PathVariable Long id) {
        voyageService.deleteVoyage(id);
        return ResponseEntity.ok(ResponseDTO.success("删除成功", null));
    }
}
