package com.example.seatstats.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "change_record", indexes = {
    @Index(name = "idx_record_seat_id", columnList = "seat_id"),
    @Index(name = "idx_record_type", columnList = "change_type"),
    @Index(name = "idx_record_time", columnList = "change_time"),
    @Index(name = "idx_record_batch_no", columnList = "suspend_batch_no")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    /**
     * 封航批次号：同一次封航为每把椅子生成的清单记录共享同一批次号，
     * 复航时可按批次号查出停运当时的完整挂载清单，逐把核对。
     */
    @Column(name = "suspend_batch_no", length = 64)
    private String suspendBatchNo;

    @Column(name = "old_route_code", length = 50)
    private String oldRouteCode;

    @Column(name = "old_route_name", length = 100)
    private String oldRouteName;

    @Column(name = "new_route_code", length = 50)
    private String newRouteCode;

    @Column(name = "new_route_name", length = 100)
    private String newRouteName;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "operator", nullable = false, length = 50)
    private String operator;

    @Column(name = "change_time", nullable = false)
    private LocalDateTime changeTime;

    @Column(name = "remark", length = 500)
    private String remark;

    @PrePersist
    protected void onCreate() {
        changeTime = LocalDateTime.now();
    }
}