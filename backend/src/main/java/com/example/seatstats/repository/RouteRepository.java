package com.example.seatstats.repository;

import com.example.seatstats.entity.Route;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByRouteCode(String routeCode);

    List<Route> findByStatus(String status);

    boolean existsByRouteCode(String routeCode);

    /**
     * 行级悲观锁：封航事务内先锁住航线行，避免两次封航并发执行导致
     * 台账批次错乱或部分解绑的中间态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);
}