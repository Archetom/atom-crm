package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportMeasure;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDefinitionTest {

    private static final FieldCode TIME = new FieldCode("created_time");
    private static final FieldCode DIMENSION = new FieldCode("status");
    private static final FieldCode AMOUNT = new FieldCode("estimated_amount");
    private static final FieldCode OWNER = new FieldCode("owner_actor");
    private static final FieldCode CUSTOMER = new FieldCode("customer");
    private static final Map<FieldCode, CustomFieldType> FIELDS = Map.of(
            TIME, CustomFieldType.DATETIME,
            DIMENSION, CustomFieldType.SELECT,
            AMOUNT, CustomFieldType.DECIMAL,
            OWNER, CustomFieldType.USER,
            CUSTOMER, CustomFieldType.RELATION);

    @Test
    void createsBoundedNormalizedPersonalDefinition() {
        ReportDefinition definition = ReportDefinition.create(new TenantId(1L), new MemberId(2L),
                "  Ｓａｌｅｓ  ", " Current sales ", BusinessObjectType.OPPORTUNITY,
                TIME, ReportGranularity.MONTH, DIMENSION,
                List.of(new FilterCondition(OWNER, FilterOperator.EQ, List.of("2"))),
                List.of(ReportMeasure.count(), ReportMeasure.sum(AMOUNT)), FIELDS);

        assertEquals("Ｓａｌｅｓ", definition.getName());
        assertEquals("sales", definition.getNormalizedName());
        assertEquals("Current sales", definition.getDescription());
        assertEquals(4, definition.referencedFields().size());
        assertTrue(definition.isActive());
    }

    @Test
    void rejectsUnsupportedDimensionAndDuplicateFilterFields() {
        assertThrows(CustomizationDomainException.class, () -> ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "Invalid", null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, AMOUNT,
                List.of(), List.of(ReportMeasure.count()), FIELDS));

        assertThrows(CustomizationDomainException.class, () -> ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "Invalid", null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, null,
                List.of(new FilterCondition(OWNER, FilterOperator.EQ, List.of("2")),
                        new FilterCondition(OWNER, FilterOperator.EQ, List.of("3"))),
                List.of(ReportMeasure.count()), FIELDS));

        assertThrows(CustomizationDomainException.class, () -> ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "Invalid", null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, null,
                List.of(new FilterCondition(CUSTOMER, FilterOperator.EQ, List.of("10"))),
                List.of(ReportMeasure.count()), FIELDS));
    }

    @Test
    void rejectsMeasureAndFilterCapacityOverflow() {
        List<ReportMeasure> measures = new ArrayList<>();
        for (int index = 0; index < ReportDefinition.MAX_MEASURES + 1; index++) {
            measures.add(ReportMeasure.sum(new FieldCode("cf_amount_" + index)));
        }
        assertThrows(CustomizationDomainException.class, () -> ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "Too many", null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, null,
                List.of(), measures, FIELDS));

        List<FilterCondition> filters = new ArrayList<>();
        Map<FieldCode, CustomFieldType> fields = new java.util.HashMap<>(FIELDS);
        for (int index = 0; index < ReportDefinition.MAX_FILTERS + 1; index++) {
            FieldCode code = new FieldCode("cf_filter_" + index);
            fields.put(code, CustomFieldType.USER);
            filters.add(new FilterCondition(code, FilterOperator.EQ, List.of("2")));
        }
        assertThrows(CustomizationDomainException.class, () -> ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "Too many", null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, null,
                filters, List.of(ReportMeasure.count()), fields));
    }

    @Test
    void countsNameLimitByUnicodeCodePoint() {
        ReportDefinition definition = ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "😀".repeat(100), null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, null,
                List.of(), List.of(ReportMeasure.count()), FIELDS);

        assertEquals(100, definition.getName().codePointCount(0, definition.getName().length()));
        assertThrows(CustomizationDomainException.class, () -> ReportDefinition.create(
                new TenantId(1L), new MemberId(2L), "😀".repeat(101), null,
                BusinessObjectType.OPPORTUNITY, TIME, ReportGranularity.DAY, null,
                List.of(), List.of(ReportMeasure.count()), FIELDS));
    }
}
