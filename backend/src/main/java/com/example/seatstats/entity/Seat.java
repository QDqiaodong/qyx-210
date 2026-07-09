package com.example.seatstats.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat", indexes = {
    @Index(name = "idx_seat_code", columnList = "seat_code"),
    @Index(name = "idx_seat_route_id", columnList = "route_id"),
    @Index(name = "idx_seat_area", columnList = "waiting_area")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_code", nullable = false, unique = true, length = 50)
    private String seatCode;

    @Column(name = "material", nullable = false, length = 50)
    private String material;

    @Column(name = "waiting_area", nullable = false, length = 100)
    private String waitingArea;

    @Column(name = "size_spec", length = 50)
    private String sizeSpec;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "IN_USE";

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