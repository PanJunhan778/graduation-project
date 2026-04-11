package com.pjh.server.audit;

import com.pjh.server.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditUpdateAspect {

    private final List<AuditSnapshotProvider> snapshotProviders;
    private final AuditDiffBuilder auditDiffBuilder;
    private final AuditOperationService auditOperationService;

    @Around("@annotation(auditUpdate)")
    public Object aroundUpdate(ProceedingJoinPoint joinPoint, AuditUpdate auditUpdate) throws Throwable {
        Long targetId = resolveTargetId(joinPoint.getArgs(), auditUpdate.idArgIndex());
        AuditSnapshotProvider snapshotProvider = resolveProvider(auditUpdate.module());
        Map<String, String> beforeSnapshot = auditDiffBuilder.snapshot(
                snapshotProvider.loadById(targetId),
                auditUpdate.fields()
        );

        Object result = joinPoint.proceed();

        Map<String, String> afterSnapshot = auditDiffBuilder.snapshot(
                snapshotProvider.loadById(targetId),
                auditUpdate.fields()
        );
        auditOperationService.publishUpdate(auditUpdate.module(), targetId, beforeSnapshot, afterSnapshot, auditUpdate.fields());

        return result;
    }

    private AuditSnapshotProvider resolveProvider(String module) {
        Map<String, AuditSnapshotProvider> providerMap = snapshotProviders.stream()
                .collect(Collectors.toMap(AuditSnapshotProvider::module, Function.identity()));
        AuditSnapshotProvider provider = providerMap.get(module);
        if (provider == null) {
            throw new BusinessException("未找到模块对应的审计快照加载器");
        }
        return provider;
    }

    private Long resolveTargetId(Object[] args, int idArgIndex) {
        if (args == null || idArgIndex < 0 || idArgIndex >= args.length) {
            throw new BusinessException("审计配置缺少目标记录 ID");
        }
        Object rawId = args[idArgIndex];
        if (!(rawId instanceof Number number)) {
            throw new BusinessException("审计配置中的目标记录 ID 无效");
        }
        return number.longValue();
    }
}
