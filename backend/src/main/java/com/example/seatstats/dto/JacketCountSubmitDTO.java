package com.example.seatstats.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 清点提交入参。
 *
 * <p>{@code expectedVersion} 为打开清点表单时页面上看到的版本：
 * 首次清点时为 null；再次清点必须带上当前版本。两人同时提交同一航线时，
 * 先写完的那份把版本 +1，后写者持旧版本提交会被拒绝（409）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JacketCountSubmitDTO {

    @NotNull(message = "额定件数不能为空")
    @Min(value = 0, message = "额定件数不能为负数")
    private Integer requiredCount;

    @NotNull(message = "实点件数不能为空")
    @Min(value = 0, message = "实点件数不能为负数")
    private Integer actualCount;

    @NotBlank(message = "清点人不能为空")
    @Size(max = 100, message = "清点人长度不能超过100")
    private String counter;

    /** 提交方掌握的最新版本；首次清点（尚无记录）时为 null */
    private Long expectedVersion;

    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
