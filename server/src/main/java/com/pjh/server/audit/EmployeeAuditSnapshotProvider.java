package com.pjh.server.audit;

import com.pjh.server.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeAuditSnapshotProvider implements AuditSnapshotProvider {

    private final EmployeeMapper employeeMapper;

    @Override
    public String module() {
        return "employee";
    }

    @Override
    public Object loadById(Long id) {
        return employeeMapper.selectById(id);
    }
}
