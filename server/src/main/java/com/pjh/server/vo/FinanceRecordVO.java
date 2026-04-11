package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FinanceRecordVO {

    private Long id;

    private String type;

    private BigDecimal amount;

    private String category;

    private String project;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}
