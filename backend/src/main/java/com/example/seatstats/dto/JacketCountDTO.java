package com.example.seatstats.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 按航线的救生衣清点结果（含排班联动所需的短缺信息）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JacketCountDTO {

    private Long id;

    private Long routeId;

    private String routeCode;

    private String routeName;

    /** 额定件数 */
    private Integer requiredCount;

    /** 实点件数 */
    private Integer actualCount;

    /** 清点人 */
    private String counter;

    /** 实点是否少于额定（短缺）。短缺时该航线不能再新排未开航班次 */
    private boolean shortage;

    /** 短缺件数（额定 - 实点），不短缺时为 0 */
    private int shortageCount;

    /** 乐观锁版本，再次提交清点时需原样带回 */
    private Long version;

    private LocalDateTime countTime;

    private String remark;
}
