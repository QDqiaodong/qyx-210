package com.example.seatstats.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 封航停运结果。
 *
 * <p>{@code items} 即封航台账清单：每一把当时挂在该航线上的椅子一条记录，
 * 记录的是该椅子停运当时的原航线信息。椅子在同一事务里全部解绑，
 * 看板配套数随解绑归零；清单与解绑在同一事务中同时完成，任一失败整体回滚。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteSuspensionDTO {

    private Long routeId;

    private String routeCode;

    private String routeName;

    private String status;

    /** 本次封航批次号，复航时凭它调取整份挂载清单 */
    private String batchNo;

    /** 封航时拆下的座椅数量 */
    private Integer seatCount;

    private String operator;

    private String reason;

    private LocalDateTime suspendedAt;

    /** 停运当时每把椅子原先挂在哪的清单 */
    private List<ChangeRecordDTO> items;
}
