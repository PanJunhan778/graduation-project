package com.pjh.server.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAuditMetadata {

    private Long targetId;

    private LocalDateTime deletedTime;

    private Long deletedByUserId;

    private String deletedByName;
}
