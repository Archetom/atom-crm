package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.AttachmentUploadIntentCreateRequest;
import io.github.archetom.crm.api.dto.request.FileFieldDraftCreateRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.PreCreateFileFieldDraftAuthorizer;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FileFieldDraftState;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileFieldDraftServiceImplTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final FileFieldRepository drafts = mock(FileFieldRepository.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final PreCreateFileFieldDraftAuthorizer preCreateDraftAuthorizer = mock(PreCreateFileFieldDraftAuthorizer.class);
    private final CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
    private final CommandServiceTemplate commands = mock(CommandServiceTemplate.class);
    private final AttachmentStorageReadinessResolver storageReadiness = mock(AttachmentStorageReadinessResolver.class);
    private final AttachmentInspectionReadinessResolver inspectionReadiness = mock(AttachmentInspectionReadinessResolver.class);
    private FileFieldDraftServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileFieldDraftServiceImpl(drafts, mock(AttachmentFileRepository.class),
                mock(FieldDefinitionRepository.class), mock(OaFormRepository.class), mock(ApprovalDefinitionRepository.class),
                mock(OrganizationMembershipRepository.class), mock(AttachmentWorkStore.class), mock(AttachmentStorage.class),
                storageReadiness, inspectionReadiness, policy(), accessGuard, preCreateDraftAuthorizer,
                mock(EffectiveFieldAccessResolver.class), mock(AttachmentTargetAccessRegistry.class), objectLock,
                new AttachmentApplicationSupport(), commands, Clock.fixed(NOW, ZoneOffset.UTC));
        when(caller.actorId()).thenReturn(MEMBER.getValue());
        when(commands.execute(any(), any())).thenAnswer(invocation -> {
            ServiceOperation<Object> operation = invocation.getArgument(1);
            operation.validate();
            Result<Object> result = new Result<>();
            result.setData(operation.execute());
            result.setSuccess(true);
            return result;
        });
    }

    @Test
    void returnVisitDraftCreationRequiresCurrentWriteDataScope() {
        FileFieldDraftCreateRequest request = new FileFieldDraftCreateRequest();
        request.setObjectType("RETURN_VISIT");
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenReturn(TENANT);
        org.mockito.Mockito.doThrow(denied()).when(preCreateDraftAuthorizer)
                .requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.RETURN_VISIT);

        assertThrows(NonRetryableApplicationException.class, () -> service.createCrmDraft(caller, request));

        verify(preCreateDraftAuthorizer).requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.RETURN_VISIT);
        verifyNoInteractions(drafts, objectLock);
    }

    @Test
    void returnVisitPreCreateDraftCandidateRevalidatesCurrentWriteDataScope() {
        FileFieldDraft draft = FileFieldDraft.reconstitute(new FileFieldDraft.Snapshot(new FileFieldDraftId(9L),
                TENANT, MEMBER, BusinessObjectType.RETURN_VISIT, null, null, null, null, new byte[32],
                LocalDateTime.ofInstant(NOW.plus(Duration.ofMinutes(5)), ZoneOffset.UTC), FileFieldDraftState.ACTIVE,
                0L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenReturn(TENANT);
        when(storageReadiness.resolve(TENANT, AttachmentOperation.ALLOCATE_WRITE)).thenReturn(storageSnapshot());
        when(inspectionReadiness.resolve(TENANT)).thenReturn(new AttachmentInspectionReadinessResolver.Snapshot(
                AttachmentReadiness.READY, Set.of("application/pdf"), NOW.plus(Duration.ofMinutes(1)), "test"));
        when(drafts.findDraft(TENANT, new FileFieldDraftId(9L))).thenReturn(Optional.of(draft));
        org.mockito.Mockito.doThrow(denied()).when(preCreateDraftAuthorizer)
                .requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.RETURN_VISIT);

        assertThrows(NonRetryableApplicationException.class, () -> service.createDraftIntent(caller, 9L, "cf_file",
                new AttachmentUploadIntentCreateRequest().setDisplayName("draft.pdf")
                        .setDeclaredMediaType("application/pdf").setExpectedBytes(4L)));

        verify(preCreateDraftAuthorizer).requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.RETURN_VISIT);
        verifyNoInteractions(objectLock);
    }

    private static AttachmentStorageReadinessResolver.Snapshot storageSnapshot() {
        return new AttachmentStorageReadinessResolver.Snapshot(AttachmentReadiness.READY,
                new StorageBindingRef(new byte[] {1}), Set.of(AttachmentOperation.ALLOCATE_WRITE,
                AttachmentOperation.STORE_SINGLE_STREAM, AttachmentOperation.PROBE_EXACT,
                AttachmentOperation.ABORT_INCOMPLETE), NOW.plus(Duration.ofMinutes(1)), "test");
    }

    private static AttachmentRuntimePolicy policy() {
        Duration second = Duration.ofSeconds(1);
        return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2, 128, 1,
                second, second, second, second, second, second, second, second,
                Duration.ofSeconds(5), Duration.ofMillis(20), 2, second, second, 1);
    }

    private static NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED);
    }
}
