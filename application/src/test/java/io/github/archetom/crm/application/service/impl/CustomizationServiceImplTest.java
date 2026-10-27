package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EmbeddedPageCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.application.vo.FieldLayoutVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldLayoutPreference;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.MembershipStatus;
import io.github.archetom.crm.domain.repository.EmbeddedPageRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.FieldLayoutPreferenceRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.RoleFieldPolicyRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.repository.ReportDefinitionRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.AUTHORIZATION_WRITE;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.EMBEDDED_PAGES_WRITE;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.FIELDS_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.FIELDS_WRITE;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.SAVED_VIEWS_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.SAVED_VIEWS_WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomizationServiceImplTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);

    @Mock private FieldDefinitionRepository fieldRepository;
    @Mock private FileFieldRepository fileFieldRepository;
    @Mock private FieldLayoutPreferenceRepository layoutRepository;
    @Mock private RoleFieldPolicyRepository policyRepository;
    @Mock private TenantRoleRepository roleRepository;
    @Mock private CustomizationObjectLock objectLock;
    @Mock private SavedViewRepository viewRepository;
    @Mock private ApprovalDefinitionRepository approvalDefinitionRepository;
    @Mock private StageWorkflowDefinitionRepository stageWorkflowRepository;
    @Mock private MarketingFormRepository marketingFormRepository;
    @Mock private OaFormRepository oaFormRepository;
    @Mock private ReportDefinitionRepository reportDefinitionRepository;
    @Mock private EmbeddedPageRepository pageRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private TenantAuthorizationEvaluator authorizationEvaluator;
    @Mock private PlatformTransactionManager transactionManager;

    private FieldCustomizationServiceImpl fieldService;
    private SavedViewServiceImpl viewService;
    private EmbeddedPageServiceImpl pageService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        OrganizationAccessGuard guard = new OrganizationAccessGuard(new CallerGuard(), membershipRepository);
        CommandServiceTemplate commands = new CommandServiceTemplate("test-app", transactionManager);
        QueryServiceTemplate queries = new QueryServiceTemplate(
                "test-app", new TestTransactionManager());
        fieldService = new FieldCustomizationServiceImpl(fieldRepository, fileFieldRepository, layoutRepository,
                policyRepository, viewRepository, approvalDefinitionRepository,
                stageWorkflowRepository, marketingFormRepository, oaFormRepository,
                reportDefinitionRepository,
                roleRepository, objectLock, authorizationEvaluator,
                guard, commands, queries);
        viewService = new SavedViewServiceImpl(
                viewRepository, fieldRepository, objectLock, guard, commands, queries);
        pageService = new EmbeddedPageServiceImpl(pageRepository, guard, commands, queries);
    }

    @Test
    void fieldWriteRequiresActiveMembershipBeforeFieldRepositoryAccess() {
        Result<?> result = fieldService.createField(caller(FIELDS_WRITE), "CUSTOMER",
                new FieldCreateRequest().setDisplayName("Priority").setFieldType("TEXT"));

        assertFailure(result, "103");
        verifyNoInteractions(fieldRepository);
    }

    @Test
    void roleFieldPolicyRequiresAuthorizationAssignmentBeforeRoleLookup() {
        activeMembership();

        Result<?> result = fieldService.replaceRolePolicy(
                caller(FIELDS_WRITE, AUTHORIZATION_WRITE), 10L, "CUSTOMER",
                new RoleFieldPolicyReplaceRequest());

        assertFailure(result, "103");
        verifyNoInteractions(roleRepository, policyRepository, authorizationEvaluator);
    }

    @Test
    void memberLayoutRejectsUnknownFieldsBeforePersistence() {
        activeMembership();
        when(fieldRepository.findAll(TENANT, BusinessObjectType.CUSTOMER, false))
                .thenReturn(List.of(field("cf_known", CustomFieldType.TEXT, true)));

        Result<?> result = fieldService.updateLayout(caller(FIELDS_READ), "CUSTOMER",
                new FieldLayoutUpdateRequest().setOrderedCodes(List.of("cf_unknown")));

        assertFailure(result, "303");
        verify(layoutRepository, never()).save(any(), any());
    }

    @Test
    void savedViewRejectsOperatorIncompatibleWithSearchableField() {
        activeMembership();
        when(fieldRepository.findAll(TENANT, BusinessObjectType.CUSTOMER, false))
                .thenReturn(List.of(field("cf_amount", CustomFieldType.DECIMAL, true)));
        SavedViewCreateRequest request = new SavedViewCreateRequest().setObjectType("CUSTOMER")
                .setName("Invalid").setConditions(List.of(new FilterConditionRequest()
                        .setFieldCode("cf_amount").setOperator("CONTAINS").setValues(List.of("10"))));

        Result<?> result = viewService.create(caller(SAVED_VIEWS_WRITE), request);

        assertFailure(result, "303");
        verify(viewRepository, never()).save(any(), any());
    }

    @Test
    void embeddedPageRejectsUnsafeTemplateBeforePersistence() {
        activeMembership();
        EmbeddedPageCreateRequest request = new EmbeddedPageCreateRequest()
                .setObjectType("CUSTOMER").setPlatform("WEB").setName("Unsafe")
                .setUrlTemplate("http://example.invalid/{recordId}");

        Result<?> result = pageService.create(caller(EMBEDDED_PAGES_WRITE), request);

        assertFailure(result, "303");
        verify(pageRepository, never()).save(any(), any());
    }

    @Test
    void fieldLifecycleRejectsReferencedLayoutsViewsAndWorkflows() {
        activeMembership();
        FieldDefinition field = field("cf_priority", CustomFieldType.TEXT, true);
        when(fieldRepository.findById(TENANT, BusinessObjectType.CUSTOMER, field.getId()))
                .thenReturn(Optional.of(field));
        when(layoutRepository.existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode())).thenReturn(true);

        Result<?> deactivate = fieldService.deactivateField(caller(FIELDS_WRITE),
                "CUSTOMER", field.getId().getValue(), field.getVersion());

        assertFailure(deactivate, "002");
        assertTrue(field.isActive());
        verify(fieldRepository, never()).save(any(), any());
        var order = inOrder(objectLock, fieldRepository, layoutRepository);
        order.verify(objectLock).acquire(TENANT, BusinessObjectType.CUSTOMER);
        order.verify(fieldRepository).findById(
                TENANT, BusinessObjectType.CUSTOMER, field.getId());
        order.verify(layoutRepository).existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode());

        when(viewRepository.existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode())).thenReturn(true);
        FieldUpdateRequest request = new FieldUpdateRequest().setVersion(field.getVersion())
                .setDisplayName(field.getDisplayName()).setSearchable(false);

        Result<?> update = fieldService.updateField(caller(FIELDS_WRITE),
                "CUSTOMER", field.getId().getValue(), request);

        assertFailure(update, "002");
        assertTrue(field.isSearchable());
        verify(fieldRepository, never()).save(any(), any());

        when(viewRepository.existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode())).thenReturn(false);
        when(stageWorkflowRepository.existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode())).thenReturn(true);

        Result<?> workflowUpdate = fieldService.updateField(caller(FIELDS_WRITE),
                "CUSTOMER", field.getId().getValue(), request);

        assertFailure(workflowUpdate, "002");
        assertTrue(field.isSearchable());
        verify(stageWorkflowRepository).existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode());
        verify(fieldRepository, never()).save(any(), any());

        when(stageWorkflowRepository.existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode())).thenReturn(false);
        when(reportDefinitionRepository.existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode())).thenReturn(true);

        Result<?> reportUpdate = fieldService.updateField(caller(FIELDS_WRITE),
                "CUSTOMER", field.getId().getValue(), request);

        assertFailure(reportUpdate, "002");
        assertTrue(field.isSearchable());
        verify(reportDefinitionRepository).existsReferencingField(
                TENANT, BusinessObjectType.CUSTOMER, field.getCode());
    }

    @Test
    void layoutReadIntersectsStoredPreferenceWithActiveCatalog() {
        activeMembership();
        FieldDefinition active = field("cf_active", CustomFieldType.TEXT, true);
        FieldCode staleCode = new FieldCode("cf_stale");
        FieldLayoutPreference preference = FieldLayoutPreference.create(
                TENANT, MEMBER, BusinessObjectType.CUSTOMER,
                List.of(active.getCode(), staleCode), Set.of(staleCode), Set.of(active.getCode()),
                Map.of(staleCode, 180), Set.of(active.getCode(), staleCode));
        when(fieldRepository.findAll(TENANT, BusinessObjectType.CUSTOMER, false))
                .thenReturn(List.of(active));
        when(layoutRepository.findByMember(TENANT, MEMBER, BusinessObjectType.CUSTOMER))
                .thenReturn(Optional.of(preference));

        Result<?> result = fieldService.getLayout(caller(FIELDS_READ), "CUSTOMER");

        assertTrue(result.isSuccess());
        FieldLayoutVO layout = (FieldLayoutVO) result.getData();
        assertEquals(List.of(active.getCode().getValue()), layout.orderedCodes());
        assertTrue(layout.hiddenCodes().isEmpty());
        assertEquals(Set.of(active.getCode().getValue()), layout.pinnedCodes());
        assertTrue(layout.columnWidths().isEmpty());
    }

    @Test
    void savedViewReadOmitsConditionsThatNoLongerMatchSearchableCatalog() {
        activeMembership();
        FieldCode code = new FieldCode("cf_stale");
        SavedView view = SavedView.create(TENANT, MEMBER, BusinessObjectType.CUSTOMER,
                "Stale", List.of(new FilterCondition(
                        code, FilterOperator.EQ, List.of("value"))), 0, false,
                Map.of(code, CustomFieldType.TEXT));
        view.onPersisted(new SavedViewId(31L), 0L, LocalDateTime.now(), LocalDateTime.now());
        when(fieldRepository.findAll(TENANT, BusinessObjectType.CUSTOMER, false))
                .thenReturn(List.of());
        when(viewRepository.findAll(TENANT, MEMBER, BusinessObjectType.CUSTOMER, false))
                .thenReturn(List.of(view));

        Result<?> result = viewService.list(caller(SAVED_VIEWS_READ), "CUSTOMER", false);

        assertTrue(result.isSuccess());
        assertTrue(((List<?>) result.getData()).isEmpty());
    }

    @Test
    void staleSavedViewCannotBecomeDefault() {
        activeMembership();
        FieldCode code = new FieldCode("cf_stale");
        SavedView view = SavedView.create(TENANT, MEMBER, BusinessObjectType.CUSTOMER,
                "Stale", List.of(new FilterCondition(
                        code, FilterOperator.EQ, List.of("value"))), 0, false,
                Map.of(code, CustomFieldType.TEXT));
        SavedViewId id = new SavedViewId(31L);
        view.onPersisted(id, 0L, LocalDateTime.now(), LocalDateTime.now());
        when(viewRepository.findById(TENANT, MEMBER, id)).thenReturn(Optional.of(view));
        when(fieldRepository.findAll(TENANT, BusinessObjectType.CUSTOMER, false))
                .thenReturn(List.of());

        Result<?> result = viewService.setDefault(
                caller(SAVED_VIEWS_WRITE), id.getValue(), view.getVersion());

        assertFailure(result, "303");
        assertFalse(view.isDefaultView());
        verify(viewRepository, never()).saveAsDefault(any(), any());
    }

    private FieldDefinition field(String code, CustomFieldType type, boolean searchable) {
        FieldDefinition field = FieldDefinition.createCustom(TENANT, BusinessObjectType.CUSTOMER,
                new FieldCode(code), "Field", null, type, false, false,
                searchable, 0, null, List.of());
        field.onPersisted(new FieldDefinitionId(20L), 0L, LocalDateTime.now(), LocalDateTime.now());
        return field;
    }

    private void activeMembership() {
        when(membershipRepository.findByMember(TENANT, MEMBER)).thenReturn(Optional.of(
                OrganizationMembership.reconstitute(new OrganizationMembership.Snapshot(
                        new MembershipId(MEMBER.getValue()), TENANT, MEMBER, null,
                        MembershipStatus.ACTIVE, 0L, LocalDateTime.now(), LocalDateTime.now()))));
    }

    private AuthenticatedCaller caller(String... authorities) {
        return new AuthenticatedCaller(MEMBER.getValue(), TENANT.getValue(), Set.of(authorities));
    }

    private void assertFailure(Result<?> result, String specificCode) {
        assertFalse(result.isSuccess());
        assertEquals(specificCode,
                result.getErrorContext().fetchRootError().getErrorCode().getErrorSpecific());
    }
}
