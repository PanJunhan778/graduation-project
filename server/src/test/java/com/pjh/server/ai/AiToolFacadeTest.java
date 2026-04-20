package com.pjh.server.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.mapper.AuditLogMapper;
import com.pjh.server.mapper.EmployeeMapper;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.mapper.TaxRecordMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolFacadeTest {

    @Mock
    private FinanceRecordMapper financeRecordMapper;

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private TaxRecordMapper taxRecordMapper;

    @Mock
    private AuditLogMapper auditLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-20T06:23:45Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void queryTaxRecordsShouldUsePrefixMatchingForYearInput() throws Exception {
        initializeTableInfo();
        AiToolFacade aiToolFacade = newAiToolFacade();
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                taxRecord(1L, "2023-01", "增值税", "3200.00"),
                taxRecord(2L, "2023-Q2", "企业所得税", "1800.00"),
                taxRecord(3L, "2023-Annual", "印花税", "260.00")
        ));

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("query_tax_records")
                .arguments("{\"taxPeriod\":\"2023\"}")
                .build();

        AiToolExecutionOutcome outcome = aiToolFacade.execute(4L, request);

        JsonNode payload = objectMapper.readTree(outcome.resultJson());
        assertThat(payload.get("count").asInt()).isEqualTo(3);
        assertThat(payload.get("records")).hasSize(3);

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taxRecordMapper).selectList(wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment().toUpperCase();
        assertThat(sqlSegment).contains("TAXPERIOD");
        assertThat(sqlSegment).contains("LIKE");
    }

    @Test
    void calculateTaxSumShouldAcceptYearOnlyRangeBoundaries() throws Exception {
        AiToolFacade aiToolFacade = newAiToolFacade();
        when(taxRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                taxRecord(1L, "2023-01", "增值税", "3200.00"),
                taxRecord(2L, "2023-Q2", "企业所得税", "1800.00"),
                taxRecord(3L, "2023-Annual", "印花税", "260.00"),
                taxRecord(4L, "2024-01", "增值税", "999.00")
        ));

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("calculate_tax_sum")
                .arguments("{\"startPeriod\":\"2023\",\"endPeriod\":\"2023\"}")
                .build();

        AiToolExecutionOutcome outcome = aiToolFacade.execute(4L, request);

        JsonNode payload = objectMapper.readTree(outcome.resultJson());
        assertThat(payload.get("total").decimalValue()).isEqualByComparingTo("5260.00");
    }

    @Test
    void getCurrentDateTimeShouldReturnCurrentDateAndPeriodBoundaries() throws Exception {
        AiToolFacade aiToolFacade = newAiToolFacade();

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("get_current_datetime")
                .arguments("{}")
                .build();

        AiToolExecutionOutcome outcome = aiToolFacade.execute(4L, request);

        JsonNode payload = objectMapper.readTree(outcome.resultJson());
        assertThat(payload.get("currentDate").asText()).isEqualTo("2026-04-20");
        assertThat(payload.get("currentDateTime").asText()).isEqualTo("2026-04-20T14:23:45");
        assertThat(payload.get("timezone").asText()).isEqualTo("Asia/Shanghai");
        assertThat(payload.get("currentYearMonth").asText()).isEqualTo("2026-04");
        assertThat(payload.get("currentQuarter").asText()).isEqualTo("2026-Q2");
        assertThat(payload.get("monthStartDate").asText()).isEqualTo("2026-04-01");
        assertThat(payload.get("monthEndDate").asText()).isEqualTo("2026-04-30");
        assertThat(payload.get("quarterStartDate").asText()).isEqualTo("2026-04-01");
        assertThat(payload.get("quarterEndDate").asText()).isEqualTo("2026-06-30");
        assertThat(payload.get("yearStartDate").asText()).isEqualTo("2026-01-01");
        assertThat(payload.get("yearEndDate").asText()).isEqualTo("2026-12-31");
    }

    private AiToolFacade newAiToolFacade() {
        return new AiToolFacade(
                financeRecordMapper,
                employeeMapper,
                taxRecordMapper,
                auditLogMapper,
                objectMapper,
                fixedClock
        );
    }

    private TaxRecord taxRecord(Long id, String taxPeriod, String taxType, String amount) {
        TaxRecord record = new TaxRecord();
        record.setId(id);
        record.setCompanyId(4L);
        record.setTaxPeriod(taxPeriod);
        record.setTaxType(taxType);
        record.setTaxAmount(new BigDecimal(amount));
        record.setPaymentStatus(1);
        return record;
    }

    private void initializeTableInfo() {
        if (TableInfoHelper.getTableInfo(TaxRecord.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "test");
        TableInfoHelper.initTableInfo(assistant, TaxRecord.class);
    }
}
