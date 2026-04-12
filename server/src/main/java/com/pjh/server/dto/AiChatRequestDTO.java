package com.pjh.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiChatRequestDTO {

    @Size(max = 36, message = "会话 ID 长度不能超过 36 位")
    private String sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过 2000 个字符")
    private String message;
}
