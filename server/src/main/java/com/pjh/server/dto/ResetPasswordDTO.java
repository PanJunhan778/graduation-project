package com.pjh.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordDTO {

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
