package com.pjh.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.config.AiProperties;
import com.pjh.server.dashboard.HomeAiSummaryRefreshRequestedEvent;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.HomeAiSummarySnapshot;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.HomeAiSummarySnapshotMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.vo.HomeAiSummaryVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeAiSummarySnapshotServiceTest {

    @Mock
    private HomeAiSummarySnapshotMapper homeAiSummarySnapshotMapper;

    @Mock
    private CompanyMapper companyMapper;

    @Mock
    private FinanceRecordMapper financeRecordMapper;

    @Mock
    private TaxRecordMapper taxRecordMapper;

    @Mock
    private ObjectProvider<OpenAiChatModel> chatModelProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private Response<AiMessage> response;

    @Mock
    private AiMessage aiMessage;

    private HomeAiSummarySnapshotService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-17T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        AiProperties aiProperties = new AiProperties();
        service = new HomeAiSummarySnapshotService(
                homeAiSummarySnapshotMapper,
                companyMapper,
                financeRecordMapper,
                taxRecordMapper,
                chatModelProvider,
                aiProperties,
                fixedClock,
                new ObjectMapper(),
                eventPublisher
        );
    }

    @Test
    void getHomeAiSummaryShouldReturnRefreshingAndPublishRefreshWhenSnapshotMissing() {
        mockDashboardData(true);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L)).thenReturn(null);

        HomeAiSummaryVO result = service.getHomeAiSummary(9L);

        assertEquals("refreshing", result.getStatus());
        assertEquals(List.of(), result.getSummaryLines());
        assertNull(result.getGeneratedAt());

        ArgumentCaptor<HomeAiSummarySnapshot> captor = ArgumentCaptor.forClass(HomeAiSummarySnapshot.class);
        verify(homeAiSummarySnapshotMapper).insert(captor.capture());
        HomeAiSummarySnapshot snapshot = captor.getValue();
        assertEquals(9L, snapshot.getCompanyId());
        assertEquals("refreshing", snapshot.getStatus());
        assertEquals(1, snapshot.getIsDirty());
        verify(eventPublisher).publishEvent(any(HomeAiSummaryRefreshRequestedEvent.class));
    }

    @Test
    void getHomeAiSummaryShouldReturnReadySnapshotWithoutPublishingRefresh() {
        mockDashboardData(true);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L))
                .thenReturn(snapshot(1L, 9L, "[\"旧摘要仍可展示。\"]", "ready", 0, LocalDateTime.of(2026, 4, 17, 16, 0)));

        HomeAiSummaryVO result = service.getHomeAiSummary(9L);

        assertEquals("ready", result.getStatus());
        assertEquals(List.of("旧摘要仍可展示。"), result.getSummaryLines());
        assertEquals("2026-04-17T16:00", result.getGeneratedAt());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getHomeAiSummaryShouldReturnOldSummaryAndRefreshingWhenSnapshotIsDirty() {
        mockDashboardData(true);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L))
                .thenReturn(snapshot(1L, 9L, "[\"上一版摘要。\"]", "ready", 1, LocalDateTime.of(2026, 4, 17, 15, 30)));

        HomeAiSummaryVO result = service.getHomeAiSummary(9L);

        assertEquals("refreshing", result.getStatus());
        assertEquals(List.of("上一版摘要。"), result.getSummaryLines());

        ArgumentCaptor<HomeAiSummarySnapshot> captor = ArgumentCaptor.forClass(HomeAiSummarySnapshot.class);
        verify(homeAiSummarySnapshotMapper).updateById(captor.capture());
        assertEquals("refreshing", captor.getValue().getStatus());
        verify(eventPublisher).publishEvent(any(HomeAiSummaryRefreshRequestedEvent.class));
    }

    @Test
    void getHomeAiSummaryShouldReturnEmptyWhenNoObservationDataExists() {
        mockDashboardData(false);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L)).thenReturn(null);

        HomeAiSummaryVO result = service.getHomeAiSummary(9L);

        assertEquals("empty", result.getStatus());
        assertEquals(List.of(), result.getSummaryLines());
        assertNull(result.getGeneratedAt());
        verify(homeAiSummarySnapshotMapper).insert(any(HomeAiSummarySnapshot.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refreshSummaryShouldPersistReadySnapshotWhenAiGenerationSucceeds() {
        mockDashboardData(true);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L))
                .thenReturn(snapshot(1L, 9L, "[]", "refreshing", 1, null));
        when(companyMapper.selectById(9L)).thenReturn(company());

        AiProperties aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setApiKey("test-key");
        aiProperties.setBaseUrl("https://example.com");
        aiProperties.setModel("qwen");
        service = new HomeAiSummarySnapshotService(
                homeAiSummarySnapshotMapper,
                companyMapper,
                financeRecordMapper,
                taxRecordMapper,
                chatModelProvider,
                aiProperties,
                Clock.fixed(Instant.parse("2026-04-17T08:00:00Z"), ZoneId.of("Asia/Shanghai")),
                new ObjectMapper(),
                eventPublisher
        );

        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.generate(anyList())).thenReturn(response);
        when(response.content()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn("收入恢复增长\n待缴税额需要跟进");

        service.refreshSummary(9L);

        ArgumentCaptor<HomeAiSummarySnapshot> captor = ArgumentCaptor.forClass(HomeAiSummarySnapshot.class);
        verify(homeAiSummarySnapshotMapper).updateById(captor.capture());
        HomeAiSummarySnapshot snapshot = captor.getValue();
        assertEquals("ready", snapshot.getStatus());
        assertEquals(0, snapshot.getIsDirty());
        assertEquals("2026-04-17T16:00", snapshot.getGeneratedAt().toString());
        assertNull(snapshot.getRefreshStartedAt());
        assertNull(snapshot.getLastError());
        assertTrue(snapshot.getSummaryLinesJson().contains("收入恢复增长"));
    }

    @Test
    void refreshSummaryShouldKeepOldSummaryWhenGenerationFails() {
        mockDashboardData(true);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L))
                .thenReturn(snapshot(1L, 9L, "[\"上一版摘要。\"]", "refreshing", 1, LocalDateTime.of(2026, 4, 17, 12, 0)));
        when(companyMapper.selectById(9L)).thenReturn(company());

        service.refreshSummary(9L);

        ArgumentCaptor<HomeAiSummarySnapshot> captor = ArgumentCaptor.forClass(HomeAiSummarySnapshot.class);
        verify(homeAiSummarySnapshotMapper).updateById(captor.capture());
        HomeAiSummarySnapshot snapshot = captor.getValue();
        assertEquals("ready", snapshot.getStatus());
        assertEquals(1, snapshot.getIsDirty());
        assertEquals("2026-04-17T12:00", snapshot.getGeneratedAt().toString());
        assertTrue(snapshot.getSummaryLinesJson().contains("上一版摘要。"));
        assertTrue(snapshot.getLastError() != null && !snapshot.getLastError().isBlank());
    }

    @Test
    void refreshSummaryShouldMarkFailedWhenNoPreviousSummaryExists() {
        mockDashboardData(true);
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L))
                .thenReturn(snapshot(1L, 9L, "[]", "refreshing", 1, null));
        when(companyMapper.selectById(9L)).thenReturn(company());

        service.refreshSummary(9L);

        ArgumentCaptor<HomeAiSummarySnapshot> captor = ArgumentCaptor.forClass(HomeAiSummarySnapshot.class);
        verify(homeAiSummarySnapshotMapper).updateById(captor.capture());
        HomeAiSummarySnapshot snapshot = captor.getValue();
        assertEquals("failed", snapshot.getStatus());
        assertEquals(1, snapshot.getIsDirty());
        assertNull(snapshot.getGeneratedAt());
        assertEquals("[]", snapshot.getSummaryLinesJson());
    }

    @Test
    void markDirtyShouldResetFailedStatusWithoutSummaryBackToEmpty() {
        when(homeAiSummarySnapshotMapper.selectByCompanyId(9L))
                .thenReturn(snapshot(1L, 9L, "[]", "failed", 1, null));

        service.markDirty(9L);

        ArgumentCaptor<HomeAiSummarySnapshot> captor = ArgumentCaptor.forClass(HomeAiSummarySnapshot.class);
        verify(homeAiSummarySnapshotMapper).updateById(captor.capture());
        HomeAiSummarySnapshot snapshot = captor.getValue();
        assertEquals("empty", snapshot.getStatus());
        assertEquals(1, snapshot.getIsDirty());
    }

    private void mockDashboardData(boolean hasData) {
        if (hasData) {
            when(financeRecordMapper.selectCurrentMonthSummaryByCompanyId(eq(9L), any(), any()))
                    .thenReturn(List.of(
                            row("type", "income", "total", new BigDecimal("12000.00")),
                            row("type", "expense", "total", new BigDecimal("5000.00"))
                    ));
            when(financeRecordMapper.selectHomeMonthlyTrendByCompanyId(eq(9L), any(), any()))
                    .thenReturn(List.of(
                            row("month", "2026-03", "income", new BigDecimal("9000.00"), "expense", new BigDecimal("4000.00"))
                    ));
            when(taxRecordMapper.selectUnpaidTaxTotalByCompanyId(9L)).thenReturn(new BigDecimal("1200.00"));
            when(taxRecordMapper.selectHomeTaxCalendarRecordsByCompanyId(9L)).thenReturn(List.of(taxRecord(1L, "2026-Q1", "增值税", 0, "1200.00")));
            when(financeRecordMapper.selectCountByCompanyId(9L)).thenReturn(1L);
            return;
        }

        when(financeRecordMapper.selectCurrentMonthSummaryByCompanyId(eq(9L), any(), any())).thenReturn(List.of());
        when(financeRecordMapper.selectHomeMonthlyTrendByCompanyId(eq(9L), any(), any())).thenReturn(List.of());
        when(taxRecordMapper.selectUnpaidTaxTotalByCompanyId(9L)).thenReturn(BigDecimal.ZERO);
        when(taxRecordMapper.selectHomeTaxCalendarRecordsByCompanyId(9L)).thenReturn(List.of());
        when(financeRecordMapper.selectCountByCompanyId(9L)).thenReturn(0L);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }

    private HomeAiSummarySnapshot snapshot(Long id,
                                           Long companyId,
                                           String summaryLinesJson,
                                           String status,
                                           Integer isDirty,
                                           LocalDateTime generatedAt) {
        HomeAiSummarySnapshot snapshot = new HomeAiSummarySnapshot();
        snapshot.setId(id);
        snapshot.setCompanyId(companyId);
        snapshot.setSummaryLinesJson(summaryLinesJson);
        snapshot.setStatus(status);
        snapshot.setIsDirty(isDirty);
        snapshot.setGeneratedAt(generatedAt);
        return snapshot;
    }

    private TaxRecord taxRecord(Long id, String taxPeriod, String taxType, Integer status, String amount) {
        TaxRecord record = new TaxRecord();
        record.setId(id);
        record.setTaxPeriod(taxPeriod);
        record.setTaxType(taxType);
        record.setPaymentStatus(status);
        record.setTaxAmount(new BigDecimal(amount));
        return record;
    }

    private Company company() {
        Company company = new Company();
        company.setId(9L);
        company.setName("星桥供应链");
        company.setIndustry("跨境贸易");
        company.setTaxpayerType("一般纳税人");
        company.setDescription("主营供应链服务");
        return company;
    }
}
