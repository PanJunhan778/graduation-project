package com.pjh.server.audit;

import cn.hutool.core.util.StrUtil;
import com.pjh.server.entity.AuditLog;
import com.pjh.server.entity.User;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeleteAuditMetadataResolver {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    public Map<Long, DeleteAuditMetadata> resolve(String module, Long companyId, Collection<Long> targetIds) {
        if (companyId == null || StrUtil.isBlank(module) || targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }

        List<Long> normalizedTargetIds = targetIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedTargetIds.isEmpty()) {
            return Map.of();
        }

        List<AuditLog> deleteLogs = auditLogMapper.selectDeleteLogsForTargets(
                companyId,
                module.trim(),
                normalizedTargetIds
        );
        if (deleteLogs.isEmpty()) {
            return Map.of();
        }

        Map<Long, DeleteAuditMetadata> metadataByTargetId = new LinkedHashMap<>();
        for (AuditLog log : deleteLogs) {
            metadataByTargetId.computeIfAbsent(
                    log.getTargetId(),
                    ignored -> new DeleteAuditMetadata(log.getTargetId(), log.getOperationTime(), log.getUserId(), null)
            );
        }

        Set<Long> userIds = metadataByTargetId.values().stream()
                .map(DeleteAuditMetadata::getDeletedByUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = loadUsers(userIds);

        metadataByTargetId.values().forEach(metadata -> metadata.setDeletedByName(
                resolveOperatorName(metadata.getDeletedByUserId(), userMap.get(metadata.getDeletedByUserId()))
        ));
        return metadataByTargetId;
    }

    public String resolveOperatorName(Long userId, User user) {
        if (user == null) {
            return userId == null ? "用户#未知" : "用户#" + userId;
        }
        if (StrUtil.isNotBlank(user.getRealName())) {
            return user.getRealName();
        }
        if (StrUtil.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        return userId == null ? "用户#未知" : "用户#" + userId;
    }

    public Map<Long, User> loadUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));
    }
}
