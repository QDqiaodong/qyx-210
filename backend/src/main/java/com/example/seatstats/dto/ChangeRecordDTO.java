package com.example.seatstats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRecordDTO {

    private Long id;

    @NotNull(message = "座椅ID不能为空")
    private Long seatId;

    private String seatCode;

    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    private String oldRouteCode;

    private String oldRouteName;

    private String newRouteCode;

    private String newRouteName;

    private String changeReason;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    private String remark;
}