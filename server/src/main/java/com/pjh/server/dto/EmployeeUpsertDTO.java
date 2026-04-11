package com.pjh.server.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeUpsertDTO {

    @NotBlank(message = "员工姓名不能为空")
    private String name;

    @NotBlank(message = "所属部门不能为空")
    private String department;

    private String position;

    @NotNull(message = "基础薪资不能为空")
    @DecimalMin(value = "0.00", message = "基础薪资不能小于0")
    private BigDecimal salary;

    @NotNull(message = "入职日期不能为空")
    private LocalDate hireDate;

    @NotNull(message = "在职状态不能为空")
    @Min(value = 0, message = "在职状态只能为在职或离职")
    @Max(value = 1, message = "在职状态只能为在职或离职")
    private Integer status;

    private String remark;
}
