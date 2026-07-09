package com.example.seatstats.service;

import com.example.seatstats.dto.SeatStatDTO;
import com.example.seatstats.entity.Route;
import com.example.seatstats.repository.RouteRepository;
import com.example.seatstats.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StatsService {

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Cacheable(value = "seatStats", key = "'all'")
    public List<SeatStatDTO> getRouteSeatStats() {
        List<Route> routes = routeRepository.findAll();
        List<SeatStatDTO> stats = new ArrayList<>();

        for (Route route : routes) {
            Long seatCount = seatRepository.countByRouteId(route.getId());
            SeatStatDTO stat = new SeatStatDTO();
            stat.setRouteId(route.getId());
            stat.setRouteCode(route.getRouteCode());
            stat.setRouteName(route.getRouteName());
            stat.setStartPort(route.getStartPort());
            stat.setEndPort(route.getEndPort());
            stat.setSeatCount(seatCount);
            stat.setStatus(route.getStatus());
            stats.add(stat);
        }

        return stats;
    }

    @Cacheable(value = "seatStats", key = "#routeId")
    public SeatStatDTO getRouteSeatStat(Long routeId) {
        Route route = routeRepository.findById(routeId).orElse(null);
        if (route == null) {
            return null;
        }

        Long seatCount = seatRepository.countByRouteId(routeId);
        SeatStatDTO stat = new SeatStatDTO();
        stat.setRouteId(route.getId());
        stat.setRouteCode(route.getRouteCode());
        stat.setRouteName(route.getRouteName());
        stat.setStartPort(route.getStartPort());
        stat.setEndPort(route.getEndPort());
        stat.setSeatCount(seatCount);
        stat.setStatus(route.getStatus());

        return stat;
    }

    public Long getTotalSeatCount() {
        return seatRepository.countAllInUse();
    }

    public Long getTotalRouteCount() {
        return routeRepository.count();
    }
}