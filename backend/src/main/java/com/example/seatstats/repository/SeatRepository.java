package com.example.seatstats.repository;

import com.example.seatstats.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    Optional<Seat> findBySeatCode(String seatCode);

    List<Seat> findByRouteId(Long routeId);

    List<Seat> findByWaitingArea(String waitingArea);

    List<Seat> findByStatus(String status);

    boolean existsBySeatCode(String seatCode);

    @Query("SELECT s FROM Seat s LEFT JOIN FETCH s.route WHERE s.status = 'IN_USE'")
    List<Seat> findAllWithRoute();

    @Query("SELECT s FROM Seat s LEFT JOIN FETCH s.route WHERE s.route.id = :routeId")
    List<Seat> findByRouteIdWithRoute(@Param("routeId") Long routeId);

    /**
     * 封航拆绑用：取当时所有还挂在该航线上的椅子（含其航线关联），
     * 不限座椅自身状态，确保一把不漏、看板配套数彻底归零。
     */
    @Query("SELECT s FROM Seat s JOIN FETCH s.route WHERE s.route.id = :routeId")
    List<Seat> findAllBoundByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.route.id = :routeId AND s.status = 'IN_USE'")
    Long countByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.status = 'IN_USE'")
    Long countAllInUse();
}