package com.example.seatstats.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "route", indexes = {
    @Index(name = "idx_route_code", columnList = "route_code"),
    @Index(name = "idx_route_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_code", nullable = false, unique = true, length = 50)
    private String routeCode;

    @Column(name = "route_name", nullable = false, length = 100)
    private String routeName;

    @Column(name = "start_port", nullable = false, length = 100)
    private String startPort;

    @Column(name = "end_port", nullable = false, length = 100)
    private String endPort;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}