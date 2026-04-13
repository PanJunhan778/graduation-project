package com.pjh.server.vo;

import lombok.Data;

@Data
public class ProfileVO {

    private Long id;

    private String username;

    private String realName;

    private String role;

    private String companyName;

    private String companyCode;

    private String industry;

    private String taxpayerType;

    private String companyDescription;
}
