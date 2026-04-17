package com.pjh.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.vo.AuditOperationVO;

import java.time.LocalDate;

public interface AuditService {

    IPage<AuditOperationVO> listAuditLogs(
            int page,
            int size,
            String module,
            String operationType,
            LocalDate startDate,
            LocalDate endDate
    );
}
