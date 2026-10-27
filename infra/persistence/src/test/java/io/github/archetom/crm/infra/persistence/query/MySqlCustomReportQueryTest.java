package io.github.archetom.crm.infra.persistence.query;

import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportFieldReference;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportMeasure;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class MySqlCustomReportQueryTest {

    private static final TenantId TENANT = new TenantId(10L);
    private static final MemberId MEMBER = new MemberId(20L);

    private JdbcTemplate jdbcTemplate;
    private MySqlCustomReportQuery executor;
    private Invocation invocation;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        executor = new MySqlCustomReportQuery(jdbcTemplate);
        doAnswer(call -> {
            Object[] arguments = call.getArguments();
            invocation = new Invocation(call.getArgument(0),
                    Arrays.copyOfRange(arguments, 2, arguments.length));
            return List.of();
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void customDateTimeUsesCanonicalStringRangeAndOneExtraRowProbe() {
        ReportFieldReference time = field("cf_time", CustomFieldType.DATETIME, false, 77L);
        CustomReportQuery query = query(time);

        executor.opportunity(TENANT, new OpportunityVisibility(
                DataScope.TENANT, MEMBER, Set.of(), OpportunityAccessMode.READ), query);

        assertTrue(invocation.sql().contains("report_root.status = 'ACTIVE'"));
        assertTrue(invocation.sql().contains("report_value_0.scalar_value >= ?"));
        assertTrue(invocation.sql().endsWith("LIMIT ?"));
        assertEquals(List.of(77L, 10L, "2026-01-01T00:00", "2026-01-03T00:00", 501),
                List.of(invocation.arguments()));
    }

    @Test
    void sourceLifecycleAndBuiltinTemporalParameterTypesStayIndexable() {
        ReportFieldReference opportunityTime = field(
                "outcome_time", CustomFieldType.DATETIME, true, 1L);
        executor.opportunity(TENANT, new OpportunityVisibility(
                DataScope.TENANT, MEMBER, Set.of(), OpportunityAccessMode.READ),
                query(opportunityTime));
        assertTrue(invocation.sql().contains("report_root.status = 'ACTIVE'"));
        assertInstanceOf(LocalDateTime.class, invocation.arguments()[1]);
        assertInstanceOf(LocalDateTime.class, invocation.arguments()[2]);

        ReportFieldReference contractTime = field(
                "order_date", CustomFieldType.DATE, true, 2L);
        executor.contract(TENANT, new ContractVisibility(
                DataScope.TENANT, MEMBER, Set.of(), ContractAccessMode.READ),
                query(contractTime));
        assertTrue(invocation.sql().contains(
                "report_root.status IN ('ACTIVE', 'VOIDED')"));
        assertInstanceOf(LocalDate.class, invocation.arguments()[1]);
        assertInstanceOf(LocalDate.class, invocation.arguments()[2]);

        ReportFieldReference receivableTime = field(
                "received_date", CustomFieldType.DATE, true, 3L);
        executor.receivable(TENANT, new ReceivableVisibility(
                DataScope.TENANT, MEMBER, Set.of(), ReceivableAccessMode.READ),
                query(receivableTime));
        assertTrue(invocation.sql().contains("report_root.status = 'ACTIVE'"));
        assertInstanceOf(LocalDate.class, invocation.arguments()[1]);
        assertInstanceOf(LocalDate.class, invocation.arguments()[2]);
    }

    private CustomReportQuery query(ReportFieldReference time) {
        return new CustomReportQuery(
                new ReportDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)),
                ReportGranularity.DAY, time, null, List.of(ReportMeasure.count()),
                Map.of(time.code(), time), List.of(), 500);
    }

    private ReportFieldReference field(
            String code, CustomFieldType type, boolean builtin, long definitionId) {
        return new ReportFieldReference(new FieldCode(code), type, builtin, definitionId);
    }

    private record Invocation(String sql, Object[] arguments) { }
}
