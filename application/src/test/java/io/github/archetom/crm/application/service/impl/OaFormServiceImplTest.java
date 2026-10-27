package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaFormField;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalFallback;
import io.github.archetom.crm.domain.model.ApprovalRepeatMode;
import io.github.archetom.crm.domain.model.ApprovalStepDefinition;
import io.github.archetom.crm.domain.model.ApprovalStepMode;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.OA_FORMS_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.OA_REQUESTS_SUBMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaFormServiceImplTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of(OA_FORMS_READ, OA_REQUESTS_SUBMIT));

    @Test
    void availableLoadsOnlyBoundedFormReferences() {
        OaFormRepository forms = mock(OaFormRepository.class);
        OaRequestRepository requests = mock(OaRequestRepository.class);
        FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
        ApprovalDefinitionRepository approvals = mock(ApprovalDefinitionRepository.class);
        OrganizationMembershipRepository memberships =
                mock(OrganizationMembershipRepository.class);
        MemberProfileRepository profiles = mock(MemberProfileRepository.class);
        DepartmentRepository departments = mock(DepartmentRepository.class);
        EffectiveFieldAccessResolver fieldAccess = mock(EffectiveFieldAccessResolver.class);
        OrganizationAccessGuard access = mock(OrganizationAccessGuard.class);
        OaFormDefinition form = form();
        FieldDefinition title = titleField();
        ApprovalDefinition approval = approval();

        when(access.requireActiveMembership(CALLER, OA_FORMS_READ)).thenReturn(TENANT);
        when(access.requireActiveMembership(CALLER, OA_REQUESTS_SUBMIT)).thenReturn(TENANT);
        when(forms.findAllActive(TENANT, 200)).thenReturn(List.of(form));
        when(fields.findByCodes(eq(TENANT), eq(BusinessObjectType.OA_REQUEST),
                anyCollection())).thenReturn(List.of(title));
        when(approvals.findByIds(eq(TENANT), anyCollection()))
                .thenReturn(List.of(approval));
        when(fieldAccess.resolve(eq(TENANT), eq(MEMBER),
                eq(BusinessObjectType.OA_REQUEST), anyCollection()))
                .thenReturn(java.util.Map.of(title.getCode(), EffectiveFieldAccess.owner()));
        when(memberships.findByMember(TENANT, MEMBER))
                .thenReturn(java.util.Optional.of(
                        OrganizationMembership.activate(TENANT, MEMBER, null)));
        OaFormServiceImpl service = new OaFormServiceImpl(forms, requests, fields, approvals,
                memberships, profiles, departments, fieldAccess, access,
                mock(CustomizationObjectLock.class), null,
                new QueryServiceTemplate("test-app", new TestTransactionManager()));

        var result = service.available(CALLER);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().size());
        assertEquals("request_title",
                result.getData().getFirst().fields().getFirst().fieldCode());
        verify(forms).findAllActive(TENANT, 200);
        verify(fields, never()).findAll(any(), any(), anyBoolean());
        verify(approvals, never()).findActiveByObjectType(any(), any());
        verify(fieldAccess).resolve(eq(TENANT), eq(MEMBER),
                eq(BusinessObjectType.OA_REQUEST), anyCollection());
    }

    private OaFormDefinition form() {
        LocalDateTime now = LocalDateTime.now();
        return OaFormDefinition.reconstitute(new OaFormDefinition.Snapshot(
                new OaFormId(21L), TENANT, MEMBER, "leave", "Leave", null,
                new ApprovalDefinitionId(31L), OaFormStatus.ACTIVE,
                List.of(new OaFormField(new FieldCode("request_title"), true, 10)),
                1L, now, now));
    }

    private FieldDefinition titleField() {
        return FieldDefinition.createBuiltin(TENANT, BusinessObjectType.OA_REQUEST,
                new FieldCode("request_title"), "Request title", null,
                CustomFieldType.TEXT, true, false, true, 10, "Basic", List.of());
    }

    private ApprovalDefinition approval() {
        LocalDateTime now = LocalDateTime.now();
        ApprovalStepDefinition step = new ApprovalStepDefinition(
                "review", "Review", 0, ApprovalStepType.MEMBER, ApprovalStepMode.ALL,
                ApprovalFallback.AUTO_APPROVE, List.of(MEMBER), List.of(), List.of());
        return ApprovalDefinition.reconstitute(new ApprovalDefinition.Snapshot(
                new ApprovalDefinitionId(31L), TENANT, BusinessObjectType.OA_REQUEST,
                "Leave approval", null, ApprovalDefinitionStatus.ACTIVE,
                ApprovalRepeatMode.ONCE, Set.of(), Set.of(), Set.of(), List.of(step),
                1L, now, now));
    }
}
