package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.EmbeddedPage;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldLayoutPreference;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.RoleFieldAccess;
import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.valueobject.EmbeddedPageId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleFieldAccessPO;
import io.github.archetom.crm.infra.persistence.mysql.po.SavedViewPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomizationPOConverterTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final BusinessObjectType OBJECT = BusinessObjectType.CUSTOMER;
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 15, 12, 0);

    @Test
    void fieldAndLayoutRoundTripsPreserveBoundedMetadata() {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldDefinition field = FieldDefinition.createCustom(TENANT, OBJECT,
                new FieldCode("cf_priority"), "Priority", "Sales priority",
                CustomFieldType.SELECT, true, false, true, 4, "Main",
                List.of(new FieldOption("high", "High", 1)));
        field.onPersisted(new FieldDefinitionId(12L), 3L, CREATED, CREATED.plusHours(1));
        CustomFieldDefinitionPOConverter fieldConverter =
                new CustomFieldDefinitionPOConverter(objectMapper);
        FieldDefinition restoredField = fieldConverter.toDomain(fieldConverter.toPO(field));

        assertEquals(12L, restoredField.getId().getValue());
        assertEquals(List.of(new FieldOption("high", "High", 1)), restoredField.getOptions());
        assertEquals(3L, restoredField.getVersion());
        assertFalse(restoredField.hasDomainEvents());

        FieldCode code = field.getCode();
        FieldLayoutPreference layout = FieldLayoutPreference.create(TENANT, new MemberId(7L), OBJECT,
                List.of(code), Set.of(), Set.of(code), Map.of(code, 180), Set.of(code));
        layout.onPersisted(2L, CREATED, CREATED.plusHours(1));
        FieldLayoutPreferencePOConverter layoutConverter =
                new FieldLayoutPreferencePOConverter(objectMapper);
        FieldLayoutPreference restoredLayout = layoutConverter.toDomain(layoutConverter.toPO(layout));

        assertEquals(List.of(code), restoredLayout.getOrderedCodes());
        assertEquals(Map.of(code, 180), restoredLayout.getColumnWidths());
        assertEquals(2L, restoredLayout.getVersion());
    }

    @Test
    void policySavedViewAndEmbeddedPageRoundTripWithoutCreatingEvents() {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldCode code = new FieldCode("cf_amount");
        RoleFieldPolicy policy = RoleFieldPolicy.create(TENANT, new RoleId(5L), OBJECT,
                List.of(new RoleFieldAccess(new FieldDefinitionId(12L), code,
                        FieldAccessLevel.READ, FieldMaskType.PARTIAL)));
        policy.onPersisted(4L, CREATED, CREATED.plusHours(1));
        RoleFieldPolicyPOConverter policyConverter = new RoleFieldPolicyPOConverter();
        RoleFieldAccessPO accessPO = policyConverter.toAccessPO(policy, policy.getAccesses().getFirst());
        RoleFieldPolicy restoredPolicy = policyConverter.toDomain(policyConverter.toPO(policy),
                List.of(accessPO), Map.of(12L, code));
        assertEquals(policy.getAccesses(), restoredPolicy.getAccesses());
        assertEquals(4L, restoredPolicy.getVersion());

        SavedView view = SavedView.create(TENANT, new MemberId(7L), OBJECT, "Large",
                List.of(new FilterCondition(code, FilterOperator.GT, List.of("100"))),
                1, false, Map.of(code, CustomFieldType.DECIMAL));
        view.onPersisted(new SavedViewId(22L), 2L, CREATED, CREATED.plusHours(1));
        SavedViewPOConverter viewConverter = new SavedViewPOConverter(objectMapper);
        SavedView restoredView = viewConverter.toDomain(viewConverter.toPO(view));
        assertEquals(view.getConditions(), restoredView.getConditions());
        assertEquals(2L, restoredView.getVersion());

        SavedViewPO legacyView = viewConverter.toPO(view);
        legacyView.setConditionsJson("[{\"fieldCode\":\"cf_amount\",\"operator\":\"GT\",\"values\":[\"100\"]}]");
        assertFalse(viewConverter.toDomain(legacyView).getSavedConditions().getFirst().exposed());

        EmbeddedPage page = EmbeddedPage.create(TENANT, new MemberId(7L), OBJECT,
                ClientPlatform.WEB, "Account", null,
                "https://example.invalid/accounts/{recordId}", 3);
        page.onPersisted(new EmbeddedPageId(32L), 1L, CREATED, CREATED.plusHours(1));
        EmbeddedPagePOConverter pageConverter = new EmbeddedPagePOConverter();
        EmbeddedPage restoredPage = pageConverter.toDomain(pageConverter.toPO(page));
        assertEquals("https://example.invalid/accounts/99", restoredPage.render(99));
        assertEquals(1L, restoredPage.getVersion());
        assertFalse(restoredPage.hasDomainEvents());
    }
}
