package com.example.seatstats.service;

import com.example.seatstats.dto.RouteDTO;
import com.example.seatstats.entity.Route;
import com.example.seatstats.exception.BusinessException;
import com.example.seatstats.repository.RouteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RouteService {

    @Autowired
    private RouteRepository routeRepository;

    @Cacheable(value = "routes", key = "'all'")
    public List<RouteDTO> getAllRoutes() {
        return routeRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "routes", key = "#id")
    public RouteDTO getRouteById(Long id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "航线不存在"));
        return convertToDTO(route);
    }

    @CacheEvict(value = "routes", allEntries = true)
    @Transactional
    public RouteDTO createRoute(RouteDTO dto) {
        if (routeRepository.existsByRouteCode(dto.getRouteCode())) {
            throw new BusinessException(400, "航线编码已存在");
        }

        Route route = new Route();
        route.setRouteCode(dto.getRouteCode());
        route.setRouteName(dto.getRouteName());
        route.setStartPort(dto.getStartPort());
        route.setEndPort(dto.getEndPort());
        route.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        route.setRemark(dto.getRemark());

        Route savedRoute = routeRepository.save(route);
        return convertToDTO(savedRoute);
    }

    @CacheEvict(value = "routes", allEntries = true)
    @Transactional
    public RouteDTO updateRoute(Long id, RouteDTO dto) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "航线不存在"));

        if (!route.getRouteCode().equals(dto.getRouteCode()) && routeRepository.existsByRouteCode(dto.getRouteCode())) {
            throw new BusinessException(400, "航线编码已存在");
        }

        route.setRouteCode(dto.getRouteCode());
        route.setRouteName(dto.getRouteName());
        route.setStartPort(dto.getStartPort());
        route.setEndPort(dto.getEndPort());
        if (dto.getStatus() != null) {
            route.setStatus(dto.getStatus());
        }
        route.setRemark(dto.getRemark());

        Route updatedRoute = routeRepository.save(route);
        return convertToDTO(updatedRoute);
    }

    @CacheEvict(value = "routes", allEntries = true)
    @Transactional
    public void deleteRoute(Long id) {
        if (!routeRepository.existsById(id)) {
            throw new BusinessException(404, "航线不存在");
        }
        routeRepository.deleteById(id);
    }

    public Route findEntityById(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "航线不存在"));
    }

    private RouteDTO convertToDTO(Route route) {
        RouteDTO dto = new RouteDTO();
        dto.setId(route.getId());
        dto.setRouteCode(route.getRouteCode());
        dto.setRouteName(route.getRouteName());
        dto.setStartPort(route.getStartPort());
        dto.setEndPort(route.getEndPort());
        dto.setStatus(route.getStatus());
        dto.setRemark(route.getRemark());
        return dto;
    }
}