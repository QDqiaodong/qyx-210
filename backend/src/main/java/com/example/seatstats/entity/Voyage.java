package com.example.seatstats.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 航线上的一个班次（排班）。
 *
 * <p>班次标记 {@code status} 有两种：</p>
 * <ul>
 *   <li>{@link #STATUS_NORMAL 正常 NORMAL}：可以按点开航；</li>
 *   <li>{@link #STATUS_JACKET_SHORT 缺衣待补 JACKET_SHORT}：救生衣实点少于额定，
 *       补齐之前该班不能按点开航，需等救生衣补齐。</li>
 * </ul>
 *
 * <p>标记规则（在清点保存的同一事务内生效）：</p>
 * <ul>
 *   <li>实点少于额定时：只把<strong>开航时刻还没到</strong>的已排班次改成缺衣待补；
 *       已经开航的班次不动，保持原样；</li>
 *   <li>实点补到不少于额定后：之前被标成缺衣待补、且开航时刻仍没到的班次自动回到正常，
 *       新班也可以继续排；但已经标成缺衣待补、<strong>且开航时刻已经过了</strong>的班次
 *       不会自动改回正常——它带着缺衣的事实开过了航，标记作为历史留存。</li>
 * </ul>
 */
@Entity
@Table(name = "voyage", indexes = {
    @Index(name = "idx_voyage_route_id", columnList = "route_id"),
    @Index(name = "idx_voyage_departure", columnList = "departure_time"),
    @Index(name = "idx_voyage_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Voyage {

    /** 正常班 */
    public static final String STATUS_NORMAL = "NORMAL";

    /** 缺衣待补：该航线救生衣实点少于额定时，尚未开航的班次被标记成此状态 */
    public static final String STATUS_JACKET_SHORT = "JACKET_SHORT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 班次编号，如 “R01-20260916-001” */
    @Column(name = "voyage_code", nullable = false, unique = true, length = 50)
    private String voyageCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    /** 开航时刻：是否“已开航”一律拿它与当前时刻比较 */
    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "vessel_name", length = 100)
    private String vesselName;

    /** 状态：NORMAL / JACKET_SHORT。不由编辑接口直接改，只跟随清点结果联动 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_NORMAL;

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
