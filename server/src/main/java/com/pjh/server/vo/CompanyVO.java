package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanyVO {

    private Long id;

    private String name;

    private String companyCode;

    private String industry;

    private String taxpayerType;

    private String description;

    private Integer status;

    private String ownerName;

    private String ownerUsername;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}
