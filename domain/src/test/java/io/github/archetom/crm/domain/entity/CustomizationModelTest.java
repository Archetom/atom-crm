package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.CustomizationDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EmbeddedPageStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizationModelTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final BusinessObjectType OBJECT = BusinessObjectType.CUSTOMER;

    @Test
    void customFieldKeepsStableTypeAndOptionValues() {
        FieldDefinition field = selectField();

        assertThrows(CustomizationDomainException.class, () -> field.update(
                "Priority", null, false, false, true, 1, null,
                List.of(new FieldOption("high", "High", 1))));
        field.update("Priority", null, false, false, true, 2, "Main",
                List.of(new FieldOption("high", "High priority", 1),
                        new FieldOption("low", "Low priority", 2),
                        new FieldOption("medium", "Medium", 3)));
        assertEquals(List.of("high", "low", "medium"),
                field.getOptions().stream().map(FieldOption::value).toList());
    }

    @Test
    void fileFieldIsStructuredButCannotBeUniqueSearchableOrOptionBacked() {
        FieldDefinition file = FieldDefinition.createCustom(
                TENANT, OBJECT, new FieldCode("cf_file"), "File", null,
                CustomFieldType.FILE, true, false, false, 0, null, List.of());

        assertTrue(file.isRequired());
        assertThrows(CustomizationDomainException.class, () -> FieldDefinition.createCustom(
                TENANT, OBJECT, new FieldCode("cf_file_unique"), "File", null,
                CustomFieldType.FILE, false, true, false, 0, null, List.of()));
        assertThrows(CustomizationDomainException.class, () -> FieldDefinition.createCustom(
                TENANT, OBJECT, new FieldCode("cf_file_searchable"), "File", null,
                CustomFieldType.FILE, false, false, true, 0, null, List.of()));
        assertThrows(CustomizationDomainException.class, () -> FieldDefinition.createCustom(
                TENANT, OBJECT, new FieldCode("cf_file_options"), "File", null,
                CustomFieldType.FILE, false, false, false, 0, null,
                List.of(new FieldOption("x", "X", 0))));
    }

    @Test
    void customFieldUniquenessIsImmutableAfterCreation() {
        FieldDefinition nonUnique = selectField();
        assertThrows(CustomizationDomainException.class, () -> nonUnique.update(
                "Priority", null, false, true, true, 1, null,
                nonUnique.getOptions()));
        assertFalse(nonUnique.isUnique());

        FieldDefinition unique = FieldDefinition.createCustom(
                TENANT, OBJECT, new FieldCode("cf_registration"), "Registration", null,
                CustomFieldType.TEXT, false, true, true, 2, null, List.of());
        assertThrows(CustomizationDomainException.class, () -> unique.update(
                "Registration", null, false, false, true, 2, null, List.of()));
        assertTrue(unique.isUnique());
    }

    @Test
    void memberLayoutRejectsUnknownAndHiddenPinnedFields() {
        FieldCode code = new FieldCode("cf_priority");
        assertThrows(CustomizationDomainException.class, () -> FieldLayoutPreference.create(
                TENANT, new MemberId(1L), OBJECT, List.of(code), Set.of(code), Set.of(code),
                Map.of(code, 120), Set.of(code)));
        assertThrows(CustomizationDomainException.class, () -> FieldLayoutPreference.create(
                TENANT, new MemberId(1L), OBJECT, List.of(new FieldCode("cf_unknown")),
                Set.of(), Set.of(), Map.of(), Set.of(code)));
    }

    @Test
    void fieldAccessFailsClosedAndOwnerIsDynamic() {
        FieldDefinition field = selectField();
        field.onPersisted(new FieldDefinitionId(3L), 0L, null, null);
        TenantRole custom = TenantRole.createCustom(TENANT, "Reader", null, List.of());
        custom.onPersisted(new RoleId(5L), 0L, null, null);
        RoleFieldPolicy policy = RoleFieldPolicy.create(TENANT, custom.getId(), OBJECT,
                List.of(new RoleFieldAccess(field.getId(), field.getCode(),
                        FieldAccessLevel.READ, FieldMaskType.PARTIAL)));

        Map<FieldCode, EffectiveFieldAccess> customAccess = FieldAccessPolicy.effective(
                List.of(field), List.of(custom), List.of(policy));
        assertEquals(new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.PARTIAL),
                customAccess.get(field.getCode()));

        TenantRole owner = TenantRole.createSystem(TENANT, SystemRoleKey.TENANT_OWNER);
        owner.onPersisted(new RoleId(6L), 0L, null, null);
        assertEquals(EffectiveFieldAccess.owner(), FieldAccessPolicy.effective(
                List.of(field), List.of(owner), List.of()).get(field.getCode()));
        assertEquals(EffectiveFieldAccess.hidden(), FieldAccessPolicy.effective(
                List.of(field), List.of(), List.of()).get(field.getCode()));
    }

    @Test
    void savedViewRequiresSearchableCompatibleFields() {
        FieldCode code = new FieldCode("cf_amount");
        assertThrows(CustomizationDomainException.class, () -> SavedView.create(
                TENANT, new MemberId(1L), OBJECT, "Large", List.of(
                        new FilterCondition(code, FilterOperator.CONTAINS, List.of("10"))),
                0, false, Map.of(code, CustomFieldType.DECIMAL)));

        SavedView view = SavedView.create(TENANT, new MemberId(1L), OBJECT, "Large",
                List.of(new FilterCondition(code, FilterOperator.GT, List.of("10"))),
                0, false, Map.of(code, CustomFieldType.DECIMAL));
        view.markDefault();
        assertEquals(true, view.isDefaultView());
    }

    @Test
    void savedViewKeepsExplicitBrowserExposureOutsideQueryConditions() {
        FieldCode code = new FieldCode("cf_segment");
        FilterCondition filter = new FilterCondition(code, FilterOperator.EQ, List.of("priority"));

        SavedView view = SavedView.createWithSavedConditions(TENANT, new MemberId(1L), OBJECT,
                "Priority", List.of(new SavedViewCondition(filter, true)), 0, false,
                Map.of(code, CustomFieldType.SELECT));

        assertEquals(List.of(filter), view.getConditions());
        assertTrue(view.getSavedConditions().getFirst().exposed());
    }

    @Test
    void embeddedPageOnlyRendersControlledHttpsTemplate() {
        EmbeddedPage page = EmbeddedPage.create(TENANT, new MemberId(1L), OBJECT,
                ClientPlatform.WEB, "Account", null,
                "https://example.invalid/account/{recordId}", 0);
        assertEquals("https://example.invalid/account/42", page.render(42));
        assertThrows(CustomizationDomainException.class, () -> EmbeddedPage.create(
                TENANT, new MemberId(1L), OBJECT, ClientPlatform.WEB, "Unsafe", null,
                "http://example.invalid/{recordId}", 0));
        assertThrows(CustomizationDomainException.class, () -> EmbeddedPage.create(
                TENANT, new MemberId(1L), OBJECT, ClientPlatform.WEB, "Unsafe", null,
                "https://example.invalid/{secret}", 0));
    }

    @Test
    void failedAggregateChangesLeaveExistingStateUntouched() {
        FieldDefinition field = selectField();
        assertThrows(CustomizationDomainException.class, () -> field.update(
                "Changed", "Changed", true, false, false, -1, "Changed",
                field.getOptions()));
        assertEquals("Priority", field.getDisplayName());
        assertFalse(field.isRequired());
        assertTrue(field.isSearchable());
        assertEquals(1, field.getDisplayOrder());
        assertEquals(List.of("high", "low"),
                field.getOptions().stream().map(FieldOption::value).toList());

        FieldCode code = field.getCode();
        FieldLayoutPreference layout = FieldLayoutPreference.create(
                TENANT, new MemberId(1L), OBJECT, List.of(code), Set.of(), Set.of(code),
                Map.of(code, 180), Set.of(code));
        assertThrows(CustomizationDomainException.class, () -> layout.change(
                List.of(), Set.of(code), Set.of(code), Map.of(), Set.of(code)));
        assertEquals(List.of(code), layout.getOrderedCodes());
        assertTrue(layout.getHiddenCodes().isEmpty());
        assertEquals(Set.of(code), layout.getPinnedCodes());
        assertEquals(Map.of(code, 180), layout.getColumnWidths());

        SavedView view = SavedView.create(TENANT, new MemberId(1L), OBJECT, "Original",
                List.of(new FilterCondition(code, FilterOperator.EQ, List.of("high"))),
                1, false, Map.of(code, CustomFieldType.SELECT));
        assertThrows(CustomizationDomainException.class, () -> view.update(
                "Changed", List.of(new FilterCondition(
                        code, FilterOperator.EQ, List.of("low"))), -1, true,
                Map.of(code, CustomFieldType.SELECT)));
        assertEquals("Original", view.getName());
        assertEquals(List.of("high"), view.getConditions().getFirst().values());
        assertEquals(1, view.getSortOrder());
        assertFalse(view.isHidden());

        EmbeddedPage page = EmbeddedPage.create(TENANT, new MemberId(1L), OBJECT,
                ClientPlatform.WEB, "Original", "Original description",
                "https://example.invalid/account/{recordId}", 1);
        assertThrows(CustomizationDomainException.class, () -> page.update(
                "Changed", "Changed description",
                "https://example.invalid/changed/{recordId}", 2, null));
        assertEquals("Original", page.getName());
        assertEquals("Original description", page.getDescription());
        assertEquals("https://example.invalid/account/{recordId}", page.getUrlTemplate());
        assertEquals(1, page.getSortOrder());
        assertEquals(EmbeddedPageStatus.INACTIVE, page.getStatus());
    }

    private FieldDefinition selectField() {
        return FieldDefinition.createCustom(TENANT, OBJECT, new FieldCode("cf_priority"),
                "Priority", null, CustomFieldType.SELECT, false, false, true, 1, null,
                List.of(new FieldOption("high", "High", 1),
                        new FieldOption("low", "Low", 2)));
    }
}
