package com.pjh.server.vo;

import lombok.Data;

@Data
public class AuditFieldChangeVO {

    private String fieldName;

    private String oldValue;

    private String newValue;
}
