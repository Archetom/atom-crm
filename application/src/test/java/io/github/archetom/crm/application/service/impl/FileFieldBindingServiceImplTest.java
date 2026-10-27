package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentWorkStore;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FileFieldDraft;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.repository.AttachmentFileRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FileFieldDraftId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileFieldBindingServiceImplTest {
    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AttachmentTarget TARGET = new AttachmentTarget(AttachmentTargetType.CUSTOMER, 31L);
    private static final FieldCode FIELD = new FieldCode("cf_contract_scan");
    private static final AttachmentFileId FILE_ID = new AttachmentFileId(41L);

    @Mock private FileFieldRepository fieldRepository;
    @Mock private AttachmentFileRepository attachmentFileRepository;
    @Mock private AttachmentWorkStore workStore;
    @Mock private AttachmentRuntimePolicy runtimePolicy;
    @Mock private FieldDefinition definition;
    @Mock private AttachmentFile file;

    @Test
    void fullReplacementCanRetainAnAlreadyBoundFile() {
        when(definition.isActive()).thenReturn(true);
        when(definition.getFieldType()).thenReturn(CustomFieldType.FILE);
        when(definition.isRequired()).thenReturn(false);
        when(definition.getCode()).thenReturn(FIELD);
        when(file.getId()).thenReturn(FILE_ID);
        FileFieldRepository.BoundFile bound = new FileFieldRepository.BoundFile(
                definition, file, 0, 8L, new byte[32]);
        when(fieldRepository.findCrmValues(TENANT, TARGET)).thenReturn(Map.of(FIELD, List.of(bound)));

        new FileFieldBindingServiceImpl(fieldRepository, attachmentFileRepository, workStore,
                runtimePolicy, Clock.systemUTC()).replaceExisting(TENANT, MEMBER,
                BusinessObjectType.CUSTOMER, TARGET, Map.of(FIELD.getValue(), List.of(FILE_ID.getValue())),
                Map.of(FIELD, definition), Map.of(FIELD, EffectiveFieldAccess.owner()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<FieldCode, List<FileFieldValue>>> values = ArgumentCaptor.forClass(Map.class);
        verify(fieldRepository).replaceCrm(eq(TENANT), eq(TARGET), values.capture());
        FileFieldValue retained = values.getValue().get(FIELD).getFirst();
        assertEquals(FILE_ID, retained.fileId());
        assertEquals(8L, retained.contentVersion());
        verifyNoInteractions(attachmentFileRepository, workStore);
    }

    @Test
    void stageWorkflowDraftCannotBeConsumedByAnotherWorkflowRecord() {
        LocalDateTime now = LocalDateTime.now();
        FileFieldDraft draft = FileFieldDraft.createStageWorkflow(TENANT, MEMBER,
                BusinessObjectType.CUSTOMER, new StageWorkflowRecordId(17L), new byte[32],
                now.plusMinutes(5), now);
        draft.onPersisted(new FileFieldDraftId(61L), 0L, null, null);
        when(fieldRepository.findDraftForUpdate(TENANT, new FileFieldDraftId(61L)))
                .thenReturn(Optional.of(draft));

        FileFieldBindingServiceImpl service = new FileFieldBindingServiceImpl(fieldRepository,
                attachmentFileRepository, workStore, runtimePolicy, Clock.systemUTC());

        assertThrows(NonRetryableApplicationException.class, () -> service.consumeStageWorkflowDraft(
                TENANT, MEMBER, BusinessObjectType.CUSTOMER, new StageWorkflowRecordId(18L), 61L,
                TARGET, Map.of(), Map.of(), Map.of()));
        verifyNoInteractions(attachmentFileRepository, workStore);
    }
}
