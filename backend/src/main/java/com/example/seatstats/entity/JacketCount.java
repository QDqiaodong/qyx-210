package com.example.seatstats.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 按航线登记的救生衣清点结果。
 *
 * <p>一条航线最多一份（route_id 唯一约束），登记三件调度关心的事实：
 * 额定件数 {@code requiredCount}、本次实点件数 {@code actualCount}、清点人 {@code counter}。</p>
 *
 * <p>{@code version} 是乐观锁版本号：前端提交清点时必须带着打开表单时看到的版本，
 * 两人同时给同一航线交清点时只有先写完的那份能落库，后写者拿到的版本已过期会被拒绝，
 * 必须重新打开页面看到先写完者的实点后再决定。</p>
 */
@Entity
@Table(name = "jacket_count", uniqueConstraints = {
    @UniqueConstraint(name = "uk_jacket_count_route", columnNames = "route_id")
}, indexes = {
    @Index(name = "idx_jacket_count_route", columnList = "route_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JacketCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    /** 额定件数：这条航线出航按规定应配备的救生衣件数 */
    @Column(name = "required_count", nullable = false)
    private Integer requiredCount;

    /** 实点件数：本次清点实际点到的救生衣件数；少于额定时排班受约束 */
    @Column(name = "actual_count", nullable = false)
    private Integer actualCount;

    /** 清点人：本次实点由谁清点确认 */
    @Column(name = "counter", nullable = false, length = 100)
    private String counter;

    /**
     * 乐观锁版本。两个人同时交同一条航线的清点时，
     * 先提交者版本 +1 落库，后提交者持旧版本被 JPA 拒绝（ObjectOptimisticLockingFailureException）。
     * 首次清点时前端没有版本（null），并发的两次“首交”还会被 route_id 唯一约束再拦一道。
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "count_time", nullable = false)
    private LocalDateTime countTime;

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
        if (countTime == null) {
            countTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** 实点是否少于额定（短缺）。短缺时该航线不能再新排未开航班次。 */
    @Transient
    public boolean isShortage() {
        return actualCount < requiredCount;
    }
}
