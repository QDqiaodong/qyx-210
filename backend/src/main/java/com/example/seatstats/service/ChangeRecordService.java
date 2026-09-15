package com.example.seatstats.service;

import com.example.seatstats.dto.ChangeRecordDTO;
import com.example.seatstats.entity.ChangeRecord;
import com.example.seatstats.entity.Seat;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.ChangeRecordRepository;
import com.example.seatstats.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChangeRecordService {

    @Autowired
    private ChangeRecordRepository changeRecordRepository;

    @Autowired
    private SeatRepository seatRepository;

    public List<ChangeRecordDTO> getAllRecords() {
        return changeRecordRepository.findAllByOrderByChangeTimeDesc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ChangeRecordDTO> getRecordsBySeat(Long seatId) {
        return changeRecordRepository.findBySeatIdOrderByChangeTimeDesc(seatId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ChangeRecordDTO> getRecordsByType(String changeType) {
        return changeRecordRepository.findByChangeType(changeType).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ChangeRecordDTO getRecordById(Long id) {
        ChangeRecord record = changeRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "变更记录不存在"));
        return convertToDTO(record);
    }

    public void deleteRecord(Long id) {
        if (!changeRecordRepository.existsById(id)) {
            throw new BusinessException(404, "变更记录不存在");
        }
        changeRecordRepository.deleteById(id);
    }

    private ChangeRecordDTO convertToDTO(ChangeRecord record) {
        ChangeRecordDTO dto = new ChangeRecordDTO();
        dto.setId(record.getId());
        dto.setSeatId(record.getSeat().getId());
        dto.setSeatCode(record.getSeat().getSeatCode());
        dto.setChangeType(record.getChangeType());
        dto.setSuspendBatchNo(record.getSuspendBatchNo());
        dto.setOldRouteCode(record.getOldRouteCode());
        dto.setOldRouteName(record.getOldRouteName());
        dto.setNewRouteCode(record.getNewRouteCode());
        dto.setNewRouteName(record.getNewRouteName());
        dto.setChangeReason(record.getChangeReason());
        dto.setOperator(record.getOperator());
        dto.setChangeTime(record.getChangeTime());
        dto.setRemark(record.getRemark());
        return dto;
    }
}