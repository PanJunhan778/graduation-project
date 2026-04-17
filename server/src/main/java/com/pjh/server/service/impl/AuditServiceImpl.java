package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.audit.AuditOperationGroupKey;
import com.pjh.server.audit.AuditOperationGroupQuery;
import com.pjh.server.audit.AuditOperationGroupRow;
import com.pjh.server.entity.AuditLog;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.AuditService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AuditFieldChangeVO;
import com.pjh.server.vo.AuditOperationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final Set<String> SUPPORTED_MODULES = Set.of("finance", "employee", "tax");
    private static final Set<String> SUPPORTED_OPERATION_TYPES = Set.of("CREATE", "UPDATE", "DELETE");

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    private final CurrentSessionService currentSessionService;

    @Override
    public IPage<AuditOperationVO> listAuditLogs(
            int page,
            int size,
            String module,
            String operationType,
            LocalDate startDate,
            LocalDate endDate
    ) {
        String normalizedModule = validateAndNormalizeModule(module);
        String normalizedOperationType = validateAndNormalizeOperationType(operationType);
        validateDateRange(startDate, endDate);

        Long companyId = currentSessionService.requireCurrentCompanyId();
        LocalDateTime startDateTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        AuditOperationGroupQuery query = new AuditOperationGroupQuery(
                companyId,
                normalizedModule,
                normalizedOperationType,
                startDateTime,
                endExclusive,
                (long) (page - 1) * size,
                size
        );
        Page<AuditOperationVO> resultPage = new Page<>(page, size);
        long total = auditLogMapper.countOperationGroups(query);
        resultPage.setTotal(total);
        if (total == 0) {
            resultPage.setRecords(List.of());
            return resultPage;
        }

        List<AuditOperationGroupRow> operationGroups = auditLogMapper.selectOperationGroups(query);
        if (operationGroups.isEmpty()) {
            resultPage.setRecords(List.of());
            return resultPage;
        }

        Map<Long, User> userMap = resolveUserMap(operationGroups.stream()
                .map(AuditOperationGroupRow::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList());
        List<AuditOperationGroupKey> groupKeys = operationGroups.stream()
                .map(this::toGroupKey)
                .toList();
        Map<AuditOperationGroupKey, List<AuditLog>> logsByGroup = auditLogMapper
                .selectByOperationGroups(companyId, groupKeys)
                .stream()
                .collect(Collectors.groupingBy(this::toGroupKey, LinkedHashMap::new, Collectors.toList()));

        resultPage.setRecords(operationGroups.stream()
                .map(group -> toOperationVO(group, logsByGroup.getOrDefault(toGroupKey(group), List.of()), userMap))
                .toList());
        return resultPage;
    }

    private String validateAndNormalizeModule(String module) {
        if (StrUtil.isBlank(module)) {
            return null;
        }

        String normalizedModule = module.trim();
        if (!SUPPORTED_MODULES.contains(normalizedModule)) {
            throw new BusinessException(400, "module 参数无效，仅支持 finance、employee、tax");
        }
        return normalizedModule;
    }

    private String validateAndNormalizeOperationType(String operationType) {
        if (StrUtil.isBlank(operationType)) {
            return null;
        }

        String normalizedOperationType = operationType.trim().toUpperCase();
        if (!SUPPORTED_OPERATION_TYPES.contains(normalizedOperationType)) {
            throw new BusinessException(400, "operationType 参数无效，仅支持 CREATE、UPDATE、DELETE");
        }
        return normalizedOperationType;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        }
    }

    private Map<Long, User> resolveUserMap(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AuditOperationVO toOperationVO(
            AuditOperationGroupRow group,
            List<AuditLog> auditLogs,
            Map<Long, User> userMap
    ) {
        AuditOperationVO vo = new AuditOperationVO();
        vo.setId(group.getMaxId());
        vo.setModule(group.getModule());
        vo.setOperationType(group.getOperationType());
        vo.setTargetId(group.getTargetId());
        vo.setOperationTime(group.getOperationTime());
        vo.setUserId(group.getUserId());
        vo.setOperatorName(resolveOperatorName(group.getUserId(), userMap.get(group.getUserId())));
        vo.setChangeCount(group.getChangeCount() == null ? auditLogs.size() : group.getChangeCount().intValue());
        vo.setChanges(auditLogs.stream().map(this::toFieldChangeVO).toList());
        return vo;
    }

    private AuditFieldChangeVO toFieldChangeVO(AuditLog log) {
        AuditFieldChangeVO vo = new AuditFieldChangeVO();
        vo.setFieldName(log.getFieldName());
        vo.setOldValue(log.getOldValue());
        vo.setNewValue(log.getNewValue());
        return vo;
    }

    private AuditOperationGroupKey toGroupKey(AuditOperationGroupRow group) {
        return new AuditOperationGroupKey(
                group.getModule(),
                group.getOperationType(),
                group.getTargetId(),
                group.getUserId(),
                group.getOperationTime()
        );
    }

    private AuditOperationGroupKey toGroupKey(AuditLog log) {
        return new AuditOperationGroupKey(
                log.getModule(),
                log.getOperationType(),
                log.getTargetId(),
                log.getUserId(),
                log.getOperationTime()
        );
    }

    private String resolveOperatorName(Long userId, User user) {
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
}
