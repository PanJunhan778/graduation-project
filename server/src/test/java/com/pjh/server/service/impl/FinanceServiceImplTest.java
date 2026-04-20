package com.pjh.server.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.audit.DeleteAuditMetadata;
import com.pjh.server.audit.DeleteAuditMetadataResolver;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.User;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.FinanceRecycleBinVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceImplTest {

    @Mock
    private FinanceRecordMapper financeRecordMapper;

    @Mock
    private AuditOperationService auditOperationService;

    @Mock
    private HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;

    @Mock
    private DeleteAuditMetadataResolver deleteAuditMetadataResolver;

    @Mock
    private CurrentSessionService currentSessionService;

    @InjectMocks
    private FinanceServiceImpl financeService;

    @Test
    void downloadTemplateShouldWriteExcelContent() throws Exception {
        FinanceServiceImpl service = new FinanceServiceImpl(null, null, null, null, null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.downloadTemplate(response);

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                response.getContentType()
        );
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void updateRecordShouldNormalizeOptionalBlankStringsToNull() {
        FinanceRecord existing = new FinanceRecord();
        existing.setId(1L);
        when(financeRecordMapper.selectById(1L)).thenReturn(existing);

        FinanceCreateDTO dto = new FinanceCreateDTO();
        dto.setType(" expense ");
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setCategory(" 采购支出 ");
        dto.setProject("   ");
        dto.setDate(LocalDate.of(2026, 4, 11));
        dto.setRemark("");

        financeService.updateRecord(1L, dto);

        ArgumentCaptor<FinanceRecord> captor = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(financeRecordMapper).updateById(captor.capture());
        assertEquals("expense", captor.getValue().getType());
        assertEquals("采购支出", captor.getValue().getCategory());
        assertNull(captor.getValue().getProject());
        assertNull(captor.getValue().getRemark());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    void createRecordShouldPublishCreateAuditWithNormalizedPayload() {
        when(financeRecordMapper.insert(any(FinanceRecord.class))).thenAnswer(invocation -> {
            FinanceRecord record = invocation.getArgument(0);
            record.setId(9L);
            return 1;
        });

        FinanceCreateDTO dto = new FinanceCreateDTO();
        dto.setType("income");
        dto.setAmount(new BigDecimal("5000.00"));
        dto.setCategory("销售收入");
        dto.setProject("  ");
        dto.setDate(LocalDate.of(2026, 4, 11));
        dto.setRemark("   ");

        financeService.createRecord(dto);

        ArgumentCaptor<FinanceRecord> captor = ArgumentCaptor.forClass(FinanceRecord.class);
        verify(financeRecordMapper).insert(captor.capture());
        assertNull(captor.getValue().getProject());
        assertNull(captor.getValue().getRemark());
        verify(auditOperationService).publishCreate(eq("finance"), eq(9L), any(FinanceRecord.class), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRecordsShouldBuildKeywordQueryForProjectAndRemark() {
        initFinanceLambdaCache();
        when(financeRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<FinanceRecord> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        financeService.listRecords(
                1,
                20,
                "income",
                "销售收入",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                "项目"
        );

        ArgumentCaptor<LambdaQueryWrapper<FinanceRecord>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(financeRecordMapper).selectPage(any(Page.class), captor.capture());

        String sqlSegment = captor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("type"));
        assertTrue(sqlSegment.contains("category"));
        assertTrue(sqlSegment.contains("date"));
        assertTrue(sqlSegment.contains("project"));
        assertTrue(sqlSegment.contains("remark"));
        assertEquals(6, captor.getValue().getParamNameValuePairs().size());
    }

    @Test
    void listCategoriesShouldNormalizeDistinctAndSortValuesByType() {
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(financeRecordMapper.selectDistinctCategoriesByCompanyId(88L, "expense")).thenReturn(Arrays.asList(
                " 办公费用 ",
                "营销推广",
                "办公费用",
                "",
                null
        ));

        List<String> categories = financeService.listCategories(" expense ");

        assertIterableEquals(List.of("办公费用", "营销推广"), categories);
    }

    @Test
    void listRecycleBinRecordsShouldPreferDeleteAuditMetadataAndFallbackToUpdatedFields() {
        FinanceRecord recordWithAudit = financeRecord(1L, "income", 21L, LocalDateTime.of(2026, 4, 10, 8, 0));
        FinanceRecord recordWithFallback = financeRecord(2L, "expense", 22L, LocalDateTime.of(2026, 4, 9, 8, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(financeRecordMapper.selectDeletedByCompanyId(88L)).thenReturn(List.of(recordWithAudit, recordWithFallback));
        when(deleteAuditMetadataResolver.resolve("finance", 88L, List.of(1L, 2L))).thenReturn(Map.of(
                1L,
                new DeleteAuditMetadata(1L, LocalDateTime.of(2026, 4, 12, 9, 30), 31L, "审计删除人")
        ));
        when(deleteAuditMetadataResolver.loadUsers(Set.of(21L, 22L))).thenReturn(Map.of(
                22L, user(22L, "回退删除人", "staff22")
        ));
        when(deleteAuditMetadataResolver.resolveOperatorName(eq(22L), any(User.class))).thenReturn("回退删除人");

        IPage<FinanceRecycleBinVO> result = financeService.listRecycleBinRecords(1, 20);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals(1L, result.getRecords().get(0).getId());
        assertEquals("审计删除人", result.getRecords().get(0).getDeletedByName());
        assertEquals("回退删除人", result.getRecords().get(1).getDeletedByName());
        assertEquals(recordWithFallback.getUpdatedTime(), result.getRecords().get(1).getDeletedTime());
    }

    @Test
    void restoreRecordShouldPublishRestoreAuditAndInvalidateHomeSummary() {
        FinanceRecord deletedRecord = financeRecord(5L, "income", 12L, LocalDateTime.of(2026, 4, 10, 10, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(66L);
        when(financeRecordMapper.selectDeletedById(88L, 5L)).thenReturn(deletedRecord);
        when(financeRecordMapper.restoreDeletedById(88L, 5L, 66L)).thenReturn(1);

        financeService.restoreRecord(5L);

        verify(auditOperationService).publishRestore(eq("finance"), eq(5L), same(deletedRecord), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    @Test
    void batchRestoreShouldRestoreOnlyExistingDeletedRecords() {
        FinanceRecord first = financeRecord(7L, "income", 12L, LocalDateTime.of(2026, 4, 10, 10, 0));
        FinanceRecord second = financeRecord(8L, "expense", 13L, LocalDateTime.of(2026, 4, 11, 10, 0));
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(88L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(66L);
        when(financeRecordMapper.selectDeletedByIds(88L, List.of(7L, 8L, 999L))).thenReturn(List.of(first, second));
        when(financeRecordMapper.restoreDeletedBatch(88L, List.of(7L, 8L), 66L)).thenReturn(2);

        int restoredCount = financeService.batchRestore(List.of(7L, 8L, 999L));

        assertEquals(2, restoredCount);
        verify(auditOperationService).publishRestore(eq("finance"), eq(7L), same(first), any());
        verify(auditOperationService).publishRestore(eq("finance"), eq(8L), same(second), any());
        verify(homeAiSummarySnapshotInvalidationPublisher).publishCurrentCompany();
    }

    private FinanceRecord financeRecord(Long id, String type, Long updatedBy, LocalDateTime updatedTime) {
        FinanceRecord record = new FinanceRecord();
        record.setId(id);
        record.setType(type);
        record.setAmount(new BigDecimal("99.00"));
        record.setCategory("测试分类");
        record.setDate(LocalDate.of(2026, 4, 1));
        record.setUpdatedBy(updatedBy);
        record.setUpdatedTime(updatedTime);
        record.setCreatedTime(updatedTime.minusDays(3));
        return record;
    }

    private User user(Long id, String realName, String username) {
        User user = new User();
        user.setId(id);
        user.setRealName(realName);
        user.setUsername(username);
        return user;
    }

    private void initFinanceLambdaCache() {
        LambdaUtils.installCache(
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), FinanceRecord.class)
        );
    }
}
