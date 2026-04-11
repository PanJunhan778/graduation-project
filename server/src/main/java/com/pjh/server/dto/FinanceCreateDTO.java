package com.pjh.server.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinanceCreateDTO {

    @NotBlank(message = "收支类型不能为空")
    private String type;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;

    @NotBlank(message = "财务分类不能为空")
    private String category;

    private String project;

    @NotNull(message = "发生日期不能为空")
    private LocalDate date;

    private String remark;
}
