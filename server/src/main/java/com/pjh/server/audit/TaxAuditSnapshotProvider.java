package com.pjh.server.audit;

import com.pjh.server.mapper.TaxRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxAuditSnapshotProvider implements AuditSnapshotProvider {

    private final TaxRecordMapper taxRecordMapper;

    @Override
    public String module() {
        return "tax";
    }

    @Override
    public Object loadById(Long id) {
        return taxRecordMapper.selectById(id);
    }
}
