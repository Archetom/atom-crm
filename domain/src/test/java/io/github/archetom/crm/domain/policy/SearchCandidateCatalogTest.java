package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchCandidateCatalogTest {

    @Test
    void exposesTheCompleteB16CandidateMatrix() {
        assertEquals(Map.of(
                        code("customer_name"), SearchMatchMode.LITERAL_PREFIX,
                        code("phone_number"), SearchMatchMode.LITERAL_PREFIX,
                        code("telephone"), SearchMatchMode.LITERAL_PREFIX,
                        code("email"), SearchMatchMode.LITERAL_PREFIX),
                CustomerFieldCatalog.searchCandidates());
        assertEquals(Map.of(
                        code("contact_name"), SearchMatchMode.LITERAL_PREFIX,
                        code("phone_number"), SearchMatchMode.LITERAL_PREFIX,
                        code("telephone"), SearchMatchMode.LITERAL_PREFIX,
                        code("email"), SearchMatchMode.LITERAL_PREFIX),
                ContactFieldCatalog.searchCandidates());
        assertEquals(Map.of(
                        code("lead_name"), SearchMatchMode.LITERAL_PREFIX,
                        code("phone_number"), SearchMatchMode.LITERAL_PREFIX,
                        code("telephone"), SearchMatchMode.LITERAL_PREFIX,
                        code("email"), SearchMatchMode.LITERAL_PREFIX),
                LeadFieldCatalog.searchCandidates());
        assertEquals(Map.of(code("opportunity_name"), SearchMatchMode.LITERAL_PREFIX),
                OpportunityFieldCatalog.searchCandidates());
        assertEquals(Map.of(
                        code("product_name"), SearchMatchMode.LITERAL_PREFIX,
                        code("product_code"), SearchMatchMode.NORMALIZED_PREFIX),
                ProductFieldCatalog.searchCandidates());
        assertEquals(Map.of(
                        code("contract_name"), SearchMatchMode.LITERAL_PREFIX,
                        code("contract_number"), SearchMatchMode.NORMALIZED_PREFIX),
                ContractFieldCatalog.searchCandidates());
        assertEquals(Map.of(
                        code("application_number"), SearchMatchMode.LITERAL_PREFIX,
                        code("title"), SearchMatchMode.LITERAL_PREFIX,
                        code("invoice_number"), SearchMatchMode.LITERAL_PREFIX),
                InvoiceFieldCatalog.searchCandidates());
        assertEquals(Map.of(code("receipt_number"), SearchMatchMode.LITERAL_PREFIX),
                ReceivableFieldCatalog.searchCandidates());
        assertEquals(Map.of(code("plan_number"), SearchMatchMode.EXACT),
                ReceivablePlanFieldCatalog.searchCandidates());
        assertEquals(Map.of(code("visit_number"), SearchMatchMode.LITERAL_PREFIX),
                ReturnVisitFieldCatalog.searchCandidates());
    }

    @Test
    void planNumberUsesAnExactPositiveIntegerAndAcceptsLeadingZeroes() {
        assertEquals(42, ReceivablePlanFieldCatalog.parseExactPlanNumber(new SearchKeyword(" 42 ")));
        assertEquals(1, ReceivablePlanFieldCatalog.parseExactPlanNumber(new SearchKeyword("01")));
        assertThrows(IllegalArgumentException.class, () ->
                ReceivablePlanFieldCatalog.parseExactPlanNumber(new SearchKeyword("0")));
        assertThrows(IllegalArgumentException.class, () ->
                ReceivablePlanFieldCatalog.parseExactPlanNumber(new SearchKeyword("-1")));
        assertThrows(IllegalArgumentException.class, () ->
                ReceivablePlanFieldCatalog.parseExactPlanNumber(new SearchKeyword("1.0")));
        assertThrows(IllegalArgumentException.class, () ->
                ReceivablePlanFieldCatalog.parseExactPlanNumber(
                        new SearchKeyword("999999999999999999999999")));
    }

    @Test
    void intersectsCandidatesWithActiveSearchableReadableUnmaskedFields() {
        TenantId tenant = new TenantId(9L);
        FieldDefinition readable = field(tenant, "readable", true);
        FieldDefinition hidden = field(tenant, "hidden", true);
        FieldDefinition masked = field(tenant, "masked", true);
        FieldDefinition unsearchable = field(tenant, "unsearchable", false);
        FieldDefinition inactive = field(tenant, "inactive", true);
        inactive.deactivate();
        Map<FieldCode, SearchMatchMode> catalog = Map.of(
                readable.getCode(), SearchMatchMode.LITERAL_PREFIX,
                hidden.getCode(), SearchMatchMode.LITERAL_PREFIX,
                masked.getCode(), SearchMatchMode.LITERAL_PREFIX,
                unsearchable.getCode(), SearchMatchMode.LITERAL_PREFIX,
                inactive.getCode(), SearchMatchMode.LITERAL_PREFIX);
        Map<FieldCode, EffectiveFieldAccess> accesses = Map.of(
                readable.getCode(), new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE),
                hidden.getCode(), EffectiveFieldAccess.hidden(),
                masked.getCode(), new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.PARTIAL),
                unsearchable.getCode(), EffectiveFieldAccess.owner(),
                inactive.getCode(), EffectiveFieldAccess.owner());

        assertEquals(Map.of(readable.getCode(), SearchMatchMode.LITERAL_PREFIX),
                FieldAccessPolicy.searchableCandidates(
                        List.of(readable, hidden, masked, unsearchable, inactive),
                        accesses, catalog));
    }

    private static FieldDefinition field(TenantId tenant, String code, boolean searchable) {
        return FieldDefinition.createCustom(tenant, BusinessObjectType.CUSTOMER,
                new FieldCode(code), code, null, CustomFieldType.TEXT,
                false, false, searchable, 10, null, List.of());
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
