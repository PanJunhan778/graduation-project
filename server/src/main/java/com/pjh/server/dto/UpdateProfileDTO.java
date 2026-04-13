package com.pjh.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileDTO {

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名不超过50个字符")
    private String realName;
}
