package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSessionVO {

    private String sessionId;

    private String title;

    private String lastMessagePreview;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;
}
