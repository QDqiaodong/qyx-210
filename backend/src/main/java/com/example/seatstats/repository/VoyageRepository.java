package com.example.seatstats.repository;

import com.example.seatstats.entity.Voyage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoyageRepository extends JpaRepository<Voyage, Long> {

    Optional<Voyage> findByVoyageCode(String voyageCode);

    boolean existsByVoyageCode(String voyageCode);

    List<Voyage> findByRouteIdOrderByDepartureTimeAscIdAsc(Long routeId);

    List<Voyage> findAllByOrderByDepartureTimeAscIdAsc();
}
