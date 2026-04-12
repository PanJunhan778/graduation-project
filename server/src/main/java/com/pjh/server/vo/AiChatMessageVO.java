package com.pjh.server.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AiChatMessageVO {

    private Long id;

    private String role;

    private String messageType;

    private String content;

    private Map<String, Object> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
