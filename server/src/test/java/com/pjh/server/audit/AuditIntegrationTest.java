package com.pjh.server.audit;

import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import com.pjh.server.common.Constants;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.service.FinanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class AuditIntegrationTest {

    @Autowired
    private FinanceService financeService;

    @MockBean
    private FinanceRecordMapper financeRecordMapper;

    @MockBean
    private AuditLogMapper auditLogMapper;

    @AfterEach
    void tearDown() {
        StpUtil.logout();
    }

    @Test
    void financeUpdateShouldWriteAuditLogAfterTransactionCommit() {
        loginAsOwner();

        when(financeRecordMapper.selectById(1L)).thenReturn(
                financeRecord("旧备注"),
                financeRecord("新备注")
        );

        FinanceCreateDTO dto = new FinanceCreateDTO();
        dto.setType("expense");
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setCategory("采购支出");
        dto.setProject("供应链项目");
        dto.setDate(LocalDate.of(2026, 4, 11));
        dto.setRemark("新备注");

        financeService.updateRecord(1L, dto);

        verify(auditLogMapper, timeout(2000)).insert(any());
    }

    @Test
    void financeUpdateShouldWriteAuditLogWhenMapperReusesSameInstance() {
        loginAsOwner();
        FinanceRecord sharedRecord = financeRecord("old remark");
        sharedRecord.setId(2L);

        when(financeRecordMapper.selectById(2L)).thenReturn(sharedRecord, sharedRecord, sharedRecord);

        FinanceCreateDTO dto = new FinanceCreateDTO();
        dto.setType(sharedRecord.getType());
        dto.setAmount(sharedRecord.getAmount());
        dto.setCategory(sharedRecord.getCategory());
        dto.setProject(sharedRecord.getProject());
        dto.setDate(sharedRecord.getDate());
        dto.setRemark("new remark");

        financeService.updateRecord(2L, dto);

        verify(auditLogMapper, timeout(2000)).insert(any());
    }

    private void loginAsOwner() {
        StpUtil.login(
                1001L,
                SaLoginConfig.setExtra(Constants.JWT_USER_ID_KEY, 1001L)
                        .setExtra(Constants.JWT_ROLE_KEY, Constants.ROLE_OWNER)
                        .setExtra(Constants.JWT_COMPANY_ID_KEY, 2001L)
        );
    }

    private FinanceRecord financeRecord(String remark) {
        FinanceRecord record = new FinanceRecord();
        record.setId(1L);
        record.setCompanyId(2001L);
        record.setType("expense");
        record.setAmount(new BigDecimal("1000.00"));
        record.setCategory("采购支出");
        record.setProject("供应链项目");
        record.setDate(LocalDate.of(2026, 4, 11));
        record.setRemark(remark);
        return record;
    }
}
