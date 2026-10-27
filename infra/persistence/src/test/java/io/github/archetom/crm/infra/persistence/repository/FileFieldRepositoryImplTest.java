package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.AttachmentFilePOConverter;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalInstanceFileFactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ApprovalInstanceFileSnapshotMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFieldBindingMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.FileFieldDraftFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.FileFieldDraftMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaRequestFileValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowFileFactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.StageWorkflowFileSnapshotMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceFileFactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ApprovalInstanceFileSnapshotPO;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowFileFactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowFileSnapshotPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileFieldRepositoryImplTest {
    private static final TenantId TENANT = new TenantId(88L);

    @Mock private FileFieldDraftMapper draftMapper;
    @Mock private FileFieldDraftFileMapper draftFileMapper;
    @Mock private AttachmentFieldBindingMapper bindingMapper;
    @Mock private OaRequestFileValueMapper oaValueMapper;
    @Mock private ApprovalInstanceFileFactMapper approvalFactMapper;
    @Mock private ApprovalInstanceFileSnapshotMapper approvalSnapshotMapper;
    @Mock private StageWorkflowFileFactMapper stageFactMapper;
    @Mock private StageWorkflowFileSnapshotMapper stageSnapshotMapper;
    @Mock private AttachmentFileMapper fileMapper;
    @Mock private CustomFieldDefinitionMapper fieldMapper;
    @Mock private AttachmentFilePOConverter fileConverter;
    @Mock private CustomFieldDefinitionPOConverter fieldConverter;

    private FileFieldRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new FileFieldRepositoryImpl(draftMapper, draftFileMapper, bindingMapper,
                oaValueMapper, approvalFactMapper, approvalSnapshotMapper, stageFactMapper,
                stageSnapshotMapper, fileMapper, fieldMapper, fileConverter, fieldConverter);
    }

    @Test
    void emptyApprovalSnapshotWritesOneHeaderAndRejectsLaterReCapture() {
        when(approvalSnapshotMapper.selectCount(any())).thenReturn(0L, 1L);
        when(approvalSnapshotMapper.insert(any(ApprovalInstanceFileSnapshotPO.class))).thenReturn(1);
        ApprovalInstanceId instanceId = new ApprovalInstanceId(41L);

        repository.snapshotApprovalInstance(TENANT, instanceId, BusinessObjectType.CUSTOMER, Map.of());
        repository.snapshotApprovalInstance(TENANT, instanceId, BusinessObjectType.CUSTOMER, Map.of());

        verify(approvalSnapshotMapper, times(1)).insert(any(ApprovalInstanceFileSnapshotPO.class));
        verify(approvalFactMapper, times(0)).insert(any(ApprovalInstanceFileFactPO.class));
    }

    @Test
    void emptyStageSnapshotWritesOneStageScopedHeaderAndRejectsLaterReCapture() {
        when(stageSnapshotMapper.selectCount(any())).thenReturn(0L, 1L);
        when(stageSnapshotMapper.insert(any(StageWorkflowFileSnapshotPO.class))).thenReturn(1);
        StageWorkflowRecordId recordId = new StageWorkflowRecordId(52L);

        repository.snapshotStageWorkflowRecord(TENANT, recordId, BusinessObjectType.CUSTOMER,
                "review", Map.of());
        repository.snapshotStageWorkflowRecord(TENANT, recordId, BusinessObjectType.CUSTOMER,
                "review", Map.of());

        verify(stageSnapshotMapper, times(1)).insert(any(StageWorkflowFileSnapshotPO.class));
        verify(stageFactMapper, times(0)).insert(any(StageWorkflowFileFactPO.class));
    }
}
