package com.pjh.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiConfirmActionDTO {

    @NotBlank(message = "确认令牌不能为空")
    private String confirmToken;

    @NotNull(message = "审批结果不能为空")
    private Boolean isApproved;
}
