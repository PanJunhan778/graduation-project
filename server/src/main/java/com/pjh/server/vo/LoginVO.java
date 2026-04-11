package com.pjh.server.vo;

import lombok.Data;

@Data
public class LoginVO {

    private String token;

    private String role;

    private String realName;

    private String companyName;

    private String companyCode;

    private String industry;

    private String taxpayerType;
}
