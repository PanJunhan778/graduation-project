package com.pjh.server.audit;

import com.pjh.server.mapper.FinanceRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinanceAuditSnapshotProvider implements AuditSnapshotProvider {

    private final FinanceRecordMapper financeRecordMapper;

    @Override
    public String module() {
        return "finance";
    }

    @Override
    public Object loadById(Long id) {
        return financeRecordMapper.selectById(id);
    }
}
