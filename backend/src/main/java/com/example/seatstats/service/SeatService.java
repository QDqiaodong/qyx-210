package com.example.seatstats.service;

import com.example.seatstats.dto.SeatDTO;
import com.example.seatstats.entity.ChangeRecord;
import com.example.seatstats.entity.Route;
import com.example.seatstats.entity.Seat;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.ChangeRecordRepository;
import com.example.seatstats.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SeatService {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private RouteService routeService;

    @Autowired
    private ChangeRecordRepository changeRecordRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String SEAT_SIZE_CACHE_PREFIX = "seat:size:";
    private static final long SEAT_SIZE_CACHE_EXPIRE_DAYS = 7;

    public List<SeatDTO> getAllSeats() {
        return seatRepository.findAllWithRoute().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public SeatDTO getSeatById(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "座椅不存在"));
        return convertToDTO(seat);
    }

    @Transactional
    public SeatDTO createSeat(SeatDTO dto) {
        if (seatRepository.existsBySeatCode(dto.getSeatCode())) {
            throw new BusinessException(400, "座椅编号已存在");
        }

        Seat seat = new Seat();
        seat.setSeatCode(dto.getSeatCode());
        seat.setMaterial(dto.getMaterial());
        seat.setWaitingArea(dto.getWaitingArea());
        seat.setSizeSpec(dto.getSizeSpec());
        seat.setStatus(dto.getStatus() != null ? dto.getStatus() : "IN_USE");
        seat.setRemark(dto.getRemark());

        if (dto.getRouteId() != null) {
            Route route = routeService.findEntityById(dto.getRouteId());
            seat.setRoute(route);
        }

        Seat savedSeat = seatRepository.save(seat);

        cacheSeatSizeSpec(savedSeat.getSeatCode(), savedSeat.getSizeSpec());

        if (savedSeat.getRoute() != null) {
            ChangeRecord record = new ChangeRecord();
            record.setSeat(savedSeat);
            record.setChangeType("BIND");
            record.setNewRouteCode(savedSeat.getRoute().getRouteCode());
            record.setNewRouteName(savedSeat.getRoute().getRouteName());
            record.setChangeReason("初始绑定");
            record.setOperator("SYSTEM");
            changeRecordRepository.save(record);
        }

        return convertToDTO(savedSeat);
    }

    @Transactional
    public SeatDTO updateSeat(Long id, SeatDTO dto) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "座椅不存在"));

        if (!seat.getSeatCode().equals(dto.getSeatCode()) && seatRepository.existsBySeatCode(dto.getSeatCode())) {
            throw new BusinessException(400, "座椅编号已存在");
        }

        seat.setSeatCode(dto.getSeatCode());
        seat.setMaterial(dto.getMaterial());
        seat.setWaitingArea(dto.getWaitingArea());
        seat.setSizeSpec(dto.getSizeSpec());
        if (dto.getStatus() != null) {
            seat.setStatus(dto.getStatus());
        }
        seat.setRemark(dto.getRemark());

        Seat updatedSeat = seatRepository.save(seat);
        cacheSeatSizeSpec(updatedSeat.getSeatCode(), updatedSeat.getSizeSpec());

        return convertToDTO(updatedSeat);
    }

    @Transactional
    public SeatDTO bindRoute(Long seatId, Long routeId, String operator, String reason) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new BusinessException(404, "座椅不存在"));

        Route newRoute = routeService.findEntityById(routeId);

        String oldRouteCode = seat.getRoute() != null ? seat.getRoute().getRouteCode() : null;
        String oldRouteName = seat.getRoute() != null ? seat.getRoute().getRouteName() : null;

        seat.setRoute(newRoute);
        Seat updatedSeat = seatRepository.save(seat);

        ChangeRecord record = new ChangeRecord();
        record.setSeat(updatedSeat);
        record.setChangeType("BIND");
        record.setOldRouteCode(oldRouteCode);
        record.setOldRouteName(oldRouteName);
        record.setNewRouteCode(newRoute.getRouteCode());
        record.setNewRouteName(newRoute.getRouteName());
        record.setChangeReason(reason != null ? reason : "航线调整");
        record.setOperator(operator != null ? operator : "SYSTEM");
        changeRecordRepository.save(record);

        return convertToDTO(updatedSeat);
    }

    @Transactional
    public SeatDTO unbindRoute(Long seatId, String operator, String reason) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new BusinessException(404, "座椅不存在"));

        String oldRouteCode = seat.getRoute() != null ? seat.getRoute().getRouteCode() : null;
        String oldRouteName = seat.getRoute() != null ? seat.getRoute().getRouteName() : null;

        seat.setRoute(null);
        Seat updatedSeat = seatRepository.save(seat);

        ChangeRecord record = new ChangeRecord();
        record.setSeat(updatedSeat);
        record.setChangeType("UNBIND");
        record.setOldRouteCode(oldRouteCode);
        record.setOldRouteName(oldRouteName);
        record.setChangeReason(reason != null ? reason : "解除绑定");
        record.setOperator(operator != null ? operator : "SYSTEM");
        changeRecordRepository.save(record);

        return convertToDTO(updatedSeat);
    }

    @Transactional
    public void deleteSeat(Long id) {
        if (!seatRepository.existsById(id)) {
            throw new BusinessException(404, "座椅不存在");
        }
        seatRepository.deleteById(id);
    }

    public List<SeatDTO> getSeatsByRoute(Long routeId) {
        return seatRepository.findByRouteIdWithRoute(routeId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Long countSeatsByRoute(Long routeId) {
        return seatRepository.countByRouteId(routeId);
    }

    public String getSeatSizeSpecFromCache(String seatCode) {
        String cacheKey = SEAT_SIZE_CACHE_PREFIX + seatCode;
        String sizeSpec = redisTemplate.opsForValue().get(cacheKey);

        if (sizeSpec == null) {
            Seat seat = seatRepository.findBySeatCode(seatCode).orElse(null);
            if (seat != null && seat.getSizeSpec() != null) {
                sizeSpec = seat.getSizeSpec();
                cacheSeatSizeSpec(seatCode, sizeSpec);
            }
        }

        return sizeSpec;
    }

    private void cacheSeatSizeSpec(String seatCode, String sizeSpec) {
        if (sizeSpec != null && !sizeSpec.isEmpty()) {
            String cacheKey = SEAT_SIZE_CACHE_PREFIX + seatCode;
            redisTemplate.opsForValue().set(cacheKey, sizeSpec, SEAT_SIZE_CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    private SeatDTO convertToDTO(Seat seat) {
        SeatDTO dto = new SeatDTO();
        dto.setId(seat.getId());
        dto.setSeatCode(seat.getSeatCode());
        dto.setMaterial(seat.getMaterial());
        dto.setWaitingArea(seat.getWaitingArea());
        dto.setSizeSpec(seat.getSizeSpec());
        dto.setStatus(seat.getStatus());
        dto.setRemark(seat.getRemark());

        if (seat.getRoute() != null) {
            dto.setRouteId(seat.getRoute().getId());
            dto.setRouteCode(seat.getRoute().getRouteCode());
            dto.setRouteName(seat.getRoute().getRouteName());
        }

        return dto;
    }
}