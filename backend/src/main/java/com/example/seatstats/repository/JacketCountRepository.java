package com.example.seatstats.repository;

import com.example.seatstats.entity.JacketCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JacketCountRepository extends JpaRepository<JacketCount, Long> {

    Optional<JacketCount> findByRouteId(Long routeId);

    boolean existsByRouteId(Long routeId);
}
