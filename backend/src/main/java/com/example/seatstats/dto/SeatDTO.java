package com.example.seatstats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {

    private Long id;

    @NotBlank(message = "座椅编号不能为空")
    @Size(max = 50, message = "座椅编号长度不能超过50")
    private String seatCode;

    @NotBlank(message = "材质不能为空")
    @Size(max = 50, message = "材质长度不能超过50")
    private String material;

    @NotBlank(message = "候船区不能为空")
    @Size(max = 100, message = "候船区长度不能超过100")
    private String waitingArea;

    private String sizeSpec;

    private Long routeId;

    private String routeCode;

    private String routeName;

    private String status;

    private String remark;
}