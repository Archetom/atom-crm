package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.ReturnVisitFieldValue;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnVisitApplicationSupportTest {
    private final OrganizationMembershipRepository membershipRepository = mock(OrganizationMembershipRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final ReturnVisitApplicationSupport support = new ReturnVisitApplicationSupport(
            mock(FieldDefinitionRepository.class), mock(EffectiveFieldAccessResolver.class),
            mock(BusinessRecordAccessGuard.class), membershipRepository, departmentRepository,
            mock(ApprovalInstanceRepository.class));

    @Test
    void customFieldPatchPreservesUntouchedValuesWithoutTheirWritePermission() {
        FieldDefinition editable = field("editable", CustomFieldType.TEXT);
        FieldDefinition readOnly = field("read_only", CustomFieldType.TEXT);
        FieldCode editableCode = editable.getCode();
        FieldCode readOnlyCode = readOnly.getCode();

        List<ReturnVisitFieldValue> result = support.customFields(Map.of("editable", List.of("new")),
                List.of(new ReturnVisitFieldValue(editableCode, List.of("old")),
                        new ReturnVisitFieldValue(readOnlyCode, List.of("preserved"))),
                Map.of(editableCode, editable, readOnlyCode, readOnly),
                Map.of(editableCode, EffectiveFieldAccess.owner()));

        assertEquals(Map.of("editable", List.of("new"), "read_only", List.of("preserved")),
                result.stream().collect(java.util.stream.Collectors.toMap(
                        value -> value.fieldCode().getValue(), ReturnVisitFieldValue::values)));
    }

    @Test
    void clearingAFieldStillRequiresWritePermission() {
        FieldDefinition field = field("read_only", CustomFieldType.TEXT);

        assertThrows(NonRetryableApplicationException.class, () -> support.customFields(
                Map.of("read_only", List.of()), List.of(new ReturnVisitFieldValue(field.getCode(),
                        List.of("preserved"))), Map.of(field.getCode(), field), Map.of()));
    }

    @Test
    void omittedCustomFieldsStillRequireEveryRequiredField() {
        FieldDefinition required = FieldDefinition.createCustom(new TenantId(1L),
                BusinessObjectType.RETURN_VISIT, new FieldCode("required_note"), "required_note",
                null, CustomFieldType.TEXT, true, false, false, 10, null, List.of());

        assertThrows(NonRetryableApplicationException.class, () -> support.customFields(
                null, List.of(), Map.of(required.getCode(), required), Map.of()));
    }

    @Test
    void relationCustomFieldIsRejectedWithoutAnOwningBusinessModule() {
        FieldDefinition field = field("unsupported_relation", CustomFieldType.RELATION);

        assertThrows(NonRetryableApplicationException.class, () -> support.customFields(
                Map.of("unsupported_relation", List.of("42")), List.of(),
                Map.of(field.getCode(), field), Map.of(field.getCode(), EffectiveFieldAccess.owner())));
    }

    @Test
    void userCustomFieldRequiresAnActiveTenantMembership() {
        FieldDefinition field = field("assigned_member", CustomFieldType.USER);
        OrganizationMembership inactiveMembership = mock(OrganizationMembership.class);
        when(inactiveMembership.getMemberId()).thenReturn(new io.github.archetom.crm.domain.valueobject.MemberId(42L));
        when(inactiveMembership.isActive()).thenReturn(false);
        when(membershipRepository.findByMembers(any(), any())).thenReturn(List.of(inactiveMembership));

        assertThrows(NonRetryableApplicationException.class, () -> support.customFields(
                Map.of("assigned_member", List.of("42")), List.of(),
                Map.of(field.getCode(), field), Map.of(field.getCode(), EffectiveFieldAccess.owner())));
    }

    @Test
    void departmentCustomFieldRequiresAnActiveTenantDepartment() {
        FieldDefinition field = field("assigned_department", CustomFieldType.DEPARTMENT);
        Department inactiveDepartment = mock(Department.class);
        when(inactiveDepartment.getId()).thenReturn(new io.github.archetom.crm.domain.valueobject.DepartmentId(42L));
        when(inactiveDepartment.isActive()).thenReturn(false);
        when(departmentRepository.findByIds(any(), any())).thenReturn(List.of(inactiveDepartment));

        assertThrows(NonRetryableApplicationException.class, () -> support.customFields(
                Map.of("assigned_department", List.of("42")), List.of(),
                Map.of(field.getCode(), field), Map.of(field.getCode(), EffectiveFieldAccess.owner())));
    }

    private FieldDefinition field(String code, CustomFieldType type) {
        return FieldDefinition.createCustom(new TenantId(1L), BusinessObjectType.RETURN_VISIT,
                new FieldCode(code), code, null, type, false, false, false, 10, null, List.of());
    }
}
