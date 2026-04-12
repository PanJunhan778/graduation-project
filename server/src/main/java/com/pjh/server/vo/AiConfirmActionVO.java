package com.pjh.server.vo;

import lombok.Data;

@Data
public class AiConfirmActionVO {

    private Long actionId;

    private String status;

    private String resultMessage;
}
