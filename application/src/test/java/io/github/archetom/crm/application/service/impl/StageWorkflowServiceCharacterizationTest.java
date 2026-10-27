package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.StageAdvanceRequest;
import io.github.archetom.crm.api.dto.request.StageCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.StageOutcomeRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowQueryRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowRequest;
import io.github.archetom.crm.api.dto.request.StageWorkflowStatusRequest;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ContactQueryService;
import io.github.archetom.crm.application.service.CustomerQueryService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.FileFieldDraftService;
import io.github.archetom.crm.application.service.LeadQueryService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowCommentRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowRecordRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pins what every public entry point of the stage-workflow service does before it does anything
 * else, so the class can be split apart safely.
 *
 * <p>The service is one 1,888-line class carrying three responsibilities that the REST layer
 * already separates, and the plan is to follow that seam. A move like that is mostly mechanical and
 * mostly caught by the compiler — except for the parts that are not: an operation that ends up in
 * the new class wired to a different collaborator, or one whose authorization check is dropped
 * while the method still compiles and still returns a result.</p>
 *
 * <p>So this records the authorization contract of all eighteen entry points as a table: which
 * authority each one demands, and that a refused authority becomes a failed {@code Result} rather
 * than an escaping exception. Both properties are invisible to the compiler and are exactly what a
 * careless move breaks. The table is also the inventory — if an entry point is added or moved, the
 * count assertion notices.</p>
 *
 * <p>This deliberately stops at the boundary rather than reproducing each operation's business
 * logic: the goal is to make a structural move verifiable, not to re-specify the service.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StageWorkflowServiceCharacterizationTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final long WORKFLOW_ID = 20L;
    private static final long RECORD_ID = 40L;

    @Mock private StageWorkflowDefinitionRepository workflowRepository;
    @Mock private StageWorkflowRecordRepository recordRepository;
    @Mock private StageWorkflowCommentRepository commentRepository;
    @Mock private ApprovalDefinitionRepository approvalDefinitionRepository;
    @Mock private ApprovalInstanceRepository approvalInstanceRepository;
    @Mock private ApprovalSubmissionCoordinator submissionCoordinator;
    @Mock private ApprovalApplicationSupport support;
    @Mock private BusinessRecordFactResolver factResolver;
    @Mock private WorkflowFieldAccessSupport fieldAccessSupport;
    @Mock private OrganizationAccessGuard accessGuard;
    @Mock private BusinessRecordAccessGuard recordAccessGuard;
    @Mock private FileFieldRepository fileFieldRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private CustomerQueryService customerQueryService;
    @Mock private ContactQueryService contactQueryService;
    @Mock private LeadQueryService leadQueryService;
    @Mock private FileFieldBindingService fileFieldBindingService;
    @Mock private FileFieldDraftService fileFieldDraftService;
    @Mock private FileFieldSnapshotOutput fileFieldSnapshotOutput;
    @Mock private CustomizationObjectLock objectLock;
    @Mock private PlatformTransactionManager transactionManager;

    private StageWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(support.error(any(), any())).thenAnswer(invocation ->
                new NonRetryableApplicationException(
                        invocation.getArgument(0, ApplicationErrorCode.class),
                        invocation.getArgument(1, String.class)));
        service = new StageWorkflowServiceImpl(definitionOperations(), recordOperations());
    }

    /**
     * Every entry point, with the authority it demands. The three groups are the seam the split
     * follows: workflow definitions, record lifecycle, and comments.
     */
    private Map<String, EntryPoint> entryPoints() {
        Map<String, EntryPoint> entries = new LinkedHashMap<>();

        // Definition management.
        entries.put("query", new EntryPoint(AuthorityCatalog.WORKFLOWS_READ,
                caller -> service.query(caller, new StageWorkflowQueryRequest())));
        entries.put("applicable", new EntryPoint(AuthorityCatalog.WORKFLOWS_READ,
                caller -> service.applicable(caller, "CUSTOMER", 30L)));
        entries.put("get", new EntryPoint(AuthorityCatalog.WORKFLOWS_READ,
                caller -> service.get(caller, WORKFLOW_ID)));
        entries.put("configurationOptions", new EntryPoint(AuthorityCatalog.WORKFLOWS_MANAGE,
                caller -> service.configurationOptions(caller, "CUSTOMER")));
        entries.put("configurationCandidates", new EntryPoint(AuthorityCatalog.WORKFLOWS_MANAGE,
                caller -> service.configurationCandidates(caller, "ACTOR", null, 1, 20)));
        entries.put("create", new EntryPoint(AuthorityCatalog.WORKFLOWS_MANAGE,
                caller -> service.create(caller, new StageWorkflowRequest())));
        entries.put("update", new EntryPoint(AuthorityCatalog.WORKFLOWS_MANAGE,
                caller -> service.update(caller, WORKFLOW_ID, new StageWorkflowRequest())));
        entries.put("changeStatus", new EntryPoint(AuthorityCatalog.WORKFLOWS_MANAGE,
                caller -> service.changeStatus(caller, WORKFLOW_ID, new StageWorkflowStatusRequest())));

        // Record lifecycle.
        entries.put("start", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.start(caller, WORKFLOW_ID, 30L)));
        entries.put("getRecord", new EntryPoint(AuthorityCatalog.WORKFLOWS_READ,
                caller -> service.getRecord(caller, RECORD_ID)));
        entries.put("advanceSelectionCandidates", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.advanceSelectionCandidates(caller, RECORD_ID, "reviewer", 1, 20)));
        entries.put("fieldCandidates", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.fieldCandidates(caller, RECORD_ID, "cf_owner", null, 1, 20)));
        entries.put("createFileDraft", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.createFileDraft(caller, RECORD_ID, "cf_file")));
        entries.put("advance", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.advance(caller, RECORD_ID, new StageAdvanceRequest())));
        entries.put("outcome", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.outcome(caller, RECORD_ID, new StageOutcomeRequest())));

        // Comments.
        entries.put("comments", new EntryPoint(AuthorityCatalog.WORKFLOWS_READ,
                caller -> service.comments(caller, RECORD_ID, 1, 20)));
        entries.put("comment", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.comment(caller, RECORD_ID, new StageCommentCreateRequest())));
        entries.put("deleteComment", new EntryPoint(AuthorityCatalog.WORKFLOWS_WRITE,
                caller -> service.deleteComment(caller, RECORD_ID, 51L, 0L)));

        return entries;
    }

    @Test
    void everyEntryPointDemandsItsDocumentedAuthority() {
        List<String> wrong = new ArrayList<>();
        entryPoints().forEach((name, entry) -> {
            org.mockito.Mockito.reset(accessGuard);
            when(accessGuard.requireActiveMembership(any(), any())).thenReturn(TENANT);

            entry.invoke(caller());

            try {
                org.mockito.Mockito.verify(accessGuard)
                        .requireActiveMembership(any(), org.mockito.ArgumentMatchers.eq(entry.authority()));
            } catch (AssertionError error) {
                wrong.add(name + " did not require " + entry.authority());
            }
        });
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    /**
     * A refused authority must come back as a failed result. If it escaped as an exception instead,
     * the caller would see a server error rather than the access-denied contract.
     */
    @Test
    void aRefusedAuthorityBecomesAFailedResultAtEveryEntryPoint() {
        List<String> escaped = new ArrayList<>();
        List<String> succeeded = new ArrayList<>();
        entryPoints().forEach((name, entry) -> {
            org.mockito.Mockito.reset(accessGuard);
            when(accessGuard.requireActiveMembership(any(), any())).thenThrow(
                    new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                            "Caller is not an active member of this tenant"));

            Result<?> result;
            try {
                result = entry.invoke(caller());
            } catch (RuntimeException exception) {
                escaped.add(name + " threw " + exception.getClass().getSimpleName());
                return;
            }
            if (result.isSuccess()) {
                succeeded.add(name + " succeeded despite a refused authority");
            }
        });
        assertTrue(escaped.isEmpty(), () -> String.join("\n", escaped));
        assertTrue(succeeded.isEmpty(), () -> String.join("\n", succeeded));
    }

    /** The inventory itself: an entry point added or removed must be a deliberate edit here. */
    @Test
    void theServiceExposesExactlyTheseEntryPoints() {
        Set<String> declared = entryPoints().keySet();
        Set<String> actual = new java.util.TreeSet<>();
        for (java.lang.reflect.Method method : StageWorkflowServiceImpl.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())
                    && method.getReturnType() == Result.class) {
                actual.add(method.getName());
            }
        }
        assertEquals(new java.util.TreeSet<>(declared), actual,
                "the characterization table must list every public entry point");
        assertEquals(18, actual.size());
    }

    private AuthenticatedCaller caller() {
        return new AuthenticatedCaller(MEMBER.getValue(), TENANT.getValue(),
                Set.of(AuthorityCatalog.WORKFLOWS_READ, AuthorityCatalog.WORKFLOWS_WRITE,
                        AuthorityCatalog.WORKFLOWS_MANAGE));
    }

    /** One entry point: the authority it must demand, and how to call it. */
    private record EntryPoint(String authority, Function<AuthenticatedCaller, Result<?>> call) {
        Result<?> invoke(AuthenticatedCaller caller) {
            return call.apply(caller);
        }
    }

    /** The real shared support over the same mocks, so the extracted checks still run. */
    private StageWorkflowSharedSupport sharedSupport() {
        return new StageWorkflowSharedSupport(workflowRepository, approvalDefinitionRepository,
                support, memberProfileRepository, departmentRepository);
    }

    /** The real definition half, so its entry points behave exactly as before the split. */
    private StageWorkflowDefinitionOperations definitionOperations() {
        return new StageWorkflowDefinitionOperations(workflowRepository, recordRepository,
                approvalDefinitionRepository, support, fieldAccessSupport, accessGuard,
                recordAccessGuard, objectLock,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()),
                sharedSupport());
    }

    /** The real record half, so its entry points behave exactly as before the split. */
    private StageWorkflowRecordOperations recordOperations() {
        return new StageWorkflowRecordOperations(recordRepository, commentRepository,
                approvalDefinitionRepository, approvalInstanceRepository, submissionCoordinator,
                support, factResolver, fieldAccessSupport, accessGuard, recordAccessGuard,
                fileFieldRepository, memberProfileRepository, departmentRepository,
                customerQueryService, contactQueryService, leadQueryService,
                fileFieldBindingService, fileFieldDraftService, fileFieldSnapshotOutput, objectLock,
                new CommandServiceTemplate("test-app", transactionManager),
                new QueryServiceTemplate("test-app", new TestTransactionManager()), sharedSupport());
    }
}
