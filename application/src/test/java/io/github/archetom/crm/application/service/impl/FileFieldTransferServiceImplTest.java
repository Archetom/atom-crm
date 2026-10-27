package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.application.port.out.attachment.StoredObjectIdentity;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.PreCreateFileFieldDraftAuthorizer;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FileFieldDraftState;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileFieldTransferServiceImplTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private static final FileFieldDraftId DRAFT_ID = new FileFieldDraftId(3L);
    private static final AttachmentFileId FILE_ID = new AttachmentFileId(4L);
    private static final FieldCode FIELD_CODE = new FieldCode("cf_file");
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final FileFieldRepository drafts = mock(FileFieldRepository.class);
    private final AttachmentFileRepository files = mock(AttachmentFileRepository.class);
    private final AttachmentWorkStore workStore = mock(AttachmentWorkStore.class);
    private final AttachmentStorage storage = mock(AttachmentStorage.class);
    private final AttachmentStorageReadinessResolver readiness = mock(AttachmentStorageReadinessResolver.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final PreCreateFileFieldDraftAuthorizer preCreateAuthorizer =
            mock(PreCreateFileFieldDraftAuthorizer.class);
    private final EffectiveFieldAccessResolver fieldAccess = mock(EffectiveFieldAccessResolver.class);
    private final CommandServiceTemplate commands = mock(CommandServiceTemplate.class);
    private FileFieldTransferServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileFieldTransferServiceImpl(drafts, files, workStore, storage, readiness, policy(), accessGuard,
                preCreateAuthorizer, fieldAccess, mock(AttachmentTargetAccessRegistry.class),
                new AttachmentApplicationSupport(), commands, Clock.fixed(NOW, ZoneOffset.UTC));
        when(caller.actorId()).thenReturn(MEMBER.getValue());
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenReturn(TENANT);
        when(commands.execute(any(UseCaseOperation.class), any())).thenAnswer(invocation -> {
            ServiceOperation<Object> operation = invocation.getArgument(1);
            operation.validate();
            Result<Object> result = new Result<>();
            result.setData(operation.execute());
            result.setSuccess(true);
            return result;
        });
    }

    @Test
    void revokedProductWriteRejectsPreCreateUploadAdmissionBeforeStorage() {
        FileFieldDraft draft = draft();
        when(drafts.findDraft(TENANT, DRAFT_ID)).thenReturn(Optional.of(draft));
        doThrow(denied()).when(preCreateAuthorizer)
                .requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);

        assertThrows(NonRetryableApplicationException.class,
                () -> service.upload(caller, DRAFT_ID.getValue(), FIELD_CODE.getValue(), FILE_ID.getValue(), 0L, source()));

        verify(preCreateAuthorizer).requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);
        verifyNoInteractions(storage, files, workStore, fieldAccess);
    }

    @Test
    void revokedProductWriteRejectsPreCreateUploadStatus() {
        FileFieldDraft draft = draft();
        when(drafts.findDraft(TENANT, DRAFT_ID)).thenReturn(Optional.of(draft));
        doThrow(denied()).when(preCreateAuthorizer)
                .requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);

        assertThrows(NonRetryableApplicationException.class,
                () -> service.uploadStatus(caller, DRAFT_ID.getValue(), FIELD_CODE.getValue(), FILE_ID.getValue()));

        verify(preCreateAuthorizer).requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);
        verifyNoInteractions(files, workStore, fieldAccess);
    }

    @Test
    void revokedProductWriteAfterStorageRejectsUploadFinalization() throws Exception {
        FileFieldDraft draft = draft();
        AttachmentFile file = file();
        FieldDefinition field = FieldDefinition.createCustom(TENANT, BusinessObjectType.PRODUCT, FIELD_CODE,
                "File", null, CustomFieldType.FILE, false, false, false, 0, null, List.of());
        AttachmentWorkStore.ContentPlan plan = new AttachmentWorkStore.ContentPlan(TENANT, FILE_ID, 1L, 0L,
                new StorageBindingRef(new byte[] {1}), new PlannedObjectLocator(new byte[] {2}), new byte[] {3}, 1L,
                null, null, NOW.plus(Duration.ofMinutes(1)));
        AttachmentContentEvidence evidence = new AttachmentContentEvidence(4L, new byte[32], 1L);
        AttachmentByteSource source = source();

        when(drafts.findDraft(TENANT, DRAFT_ID)).thenReturn(Optional.of(draft));
        when(drafts.findDraftForUpdate(TENANT, DRAFT_ID)).thenReturn(Optional.of(draft));
        when(drafts.findStagedFilesForUpdate(TENANT, DRAFT_ID))
                .thenReturn(List.of(new FileFieldRepository.StagedFile(field, file, 0)));
        when(fieldAccess.resolve(TENANT, MEMBER, BusinessObjectType.PRODUCT))
                .thenReturn(Map.of(FIELD_CODE, new EffectiveFieldAccess(FieldAccessLevel.WRITE, FieldMaskType.NONE)));
        when(workStore.findContentPlan(TENANT, FILE_ID)).thenReturn(Optional.of(plan));
        when(readiness.resolve(TENANT, AttachmentOperation.STORE_SINGLE_STREAM)).thenReturn(new AttachmentStorageReadinessResolver.Snapshot(
                AttachmentReadiness.READY, plan.binding(), Set.of(AttachmentOperation.STORE_SINGLE_STREAM),
                NOW.plus(Duration.ofMinutes(1)), "test"));
        when(storage.store(any(), same(source))).thenReturn(new AttachmentStorage.StoreReceipt(
                new StoredObjectIdentity(new byte[] {4}), evidence));
        doNothing().doThrow(denied()).when(preCreateAuthorizer)
                .requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);

        assertThrows(NonRetryableApplicationException.class,
                () -> service.upload(caller, DRAFT_ID.getValue(), FIELD_CODE.getValue(), FILE_ID.getValue(), 0L, source));

        verify(preCreateAuthorizer, times(2))
                .requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);
        verify(storage).store(any(), same(source));
        verify(files, never()).save(any(), any());
        verify(workStore, never()).recordStored(any(), any(), any(Long.class), any(Long.class), any(), any(), any());
    }

    private static FileFieldDraft draft() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return FileFieldDraft.reconstitute(new FileFieldDraft.Snapshot(DRAFT_ID, TENANT, MEMBER,
                BusinessObjectType.PRODUCT, null, null, null, null, new byte[32], now.plusMinutes(5),
                FileFieldDraftState.ACTIVE, 0L, now, now));
    }

    private static AttachmentFile file() {
        AttachmentFile file = AttachmentFile.createIntent(TENANT, MEMBER, "file.pdf", "application/pdf", 4L,
                null, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        file.onPersisted(FILE_ID, 0L, null, null);
        return file;
    }

    private static AttachmentByteSource source() {
        return new AttachmentByteSource() {
            @Override public InputStream openStream() {
                return new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
            }
            @Override public long declaredLength() {
                return 4L;
            }
        };
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
