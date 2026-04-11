package com.pjh.server.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxUpsertDTO {

    @NotBlank(message = "税款所属期不能为空")
    @Pattern(
            regexp = "^\\d{4}-(0[1-9]|1[0-2]|Q[1-4]|Annual)$",
            message = "税款所属期格式不正确"
    )
    private String taxPeriod;

    @NotBlank(message = "税种不能为空")
    private String taxType;

    private String declarationType;

    @NotNull(message = "税额不能为空")
    private BigDecimal taxAmount;

    @NotNull(message = "缴纳状态不能为空")
    @Min(value = 0, message = "缴纳状态只能为待缴纳、已缴纳或免征")
    @Max(value = 2, message = "缴纳状态只能为待缴纳、已缴纳或免征")
    private Integer paymentStatus;

    private LocalDate paymentDate;

    private String remark;
}
