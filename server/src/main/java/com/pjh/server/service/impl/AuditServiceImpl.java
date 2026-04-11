package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.entity.AuditLog;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.AuditService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public IPage<AuditLogVO> listAuditLogs(
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

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getCompanyId, companyId)
                .eq(normalizedModule != null, AuditLog::getModule, normalizedModule)
                .eq(normalizedOperationType != null, AuditLog::getOperationType, normalizedOperationType)
                .ge(startDateTime != null, AuditLog::getOperationTime, startDateTime)
                .lt(endExclusive != null, AuditLog::getOperationTime, endExclusive)
                .orderByDesc(AuditLog::getOperationTime)
                .orderByDesc(AuditLog::getId);

        IPage<AuditLog> auditPage = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, User> userMap = resolveUserMap(auditPage.getRecords());

        return auditPage.convert(log -> {
            AuditLogVO vo = new AuditLogVO();
            vo.setId(log.getId());
            vo.setModule(log.getModule());
            vo.setOperationType(log.getOperationType());
            vo.setTargetId(log.getTargetId());
            vo.setFieldName(log.getFieldName());
            vo.setOldValue(log.getOldValue());
            vo.setNewValue(log.getNewValue());
            vo.setOperationTime(log.getOperationTime());
            vo.setUserId(log.getUserId());
            vo.setOperatorName(resolveOperatorName(log.getUserId(), userMap.get(log.getUserId())));
            return vo;
        });
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

    private Map<Long, User> resolveUserMap(List<AuditLog> records) {
        List<Long> userIds = records.stream()
                .map(AuditLog::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
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
