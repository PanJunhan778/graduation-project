package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeVO {

    private Long id;

    private String name;

    private String department;

    private String position;

    private BigDecimal salary;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate hireDate;

    private Integer status;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}
