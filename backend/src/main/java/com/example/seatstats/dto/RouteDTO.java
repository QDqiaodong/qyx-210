package com.example.seatstats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteDTO {

    private Long id;

    @NotBlank(message = "航线编码不能为空")
    @Size(max = 50, message = "航线编码长度不能超过50")
    private String routeCode;

    @NotBlank(message = "航线名称不能为空")
    @Size(max = 100, message = "航线名称长度不能超过100")
    private String routeName;

    @NotBlank(message = "起点码头不能为空")
    @Size(max = 100, message = "起点码头长度不能超过100")
    private String startPort;

    @NotBlank(message = "终点码头不能为空")
    @Size(max = 100, message = "终点码头长度不能超过100")
    private String endPort;

    private String status;

    private String remark;
}