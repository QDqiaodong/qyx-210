package com.example.seatstats.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoyageDTO {

    private Long id;

    /** 班次编号（创建时可留空，由系统生成） */
    @Size(max = 50, message = "班次编号长度不能超过50")
    private String voyageCode;

    @NotNull(message = "航线不能为空")
    private Long routeId;

    private String routeCode;

    private String routeName;

    @NotNull(message = "开航时刻不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime departureTime;

    @Size(max = 100, message = "船名长度不能超过100")
    private String vesselName;

    /** NORMAL 正常 / JACKET_SHORT 缺衣待补；由清点联动，不由入参直接决定 */
    private String status;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 以开航时刻为准：开航时刻已过即为已开航。已开航的班次不会被清点联动改动。
     * 这是请求时点的判断，仅用于页面展示。
     */
    private boolean departed;
}
