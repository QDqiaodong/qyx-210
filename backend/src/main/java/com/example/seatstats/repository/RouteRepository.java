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
     *
     * <p>救生衣清点与排班同样按航线串行：两人同时交清点时，后到者在这把锁上等待，
     * 拿到锁后版本已过期被拒绝；新排班次也在持锁状态下核对是否短缺。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);
}