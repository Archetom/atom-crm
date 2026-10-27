package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.StageWorkflowRecord;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageProgress;
import io.github.archetom.crm.domain.model.StageProgressStatus;
import io.github.archetom.crm.domain.model.StageWorkflowRecordStatus;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.StageWorkflowRecordId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.StageWorkflowRecordPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class StageWorkflowRecordPOConverter {

    private final ObjectMapper objectMapper;

    public StageWorkflowRecordPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StageWorkflowRecordPO toPO(StageWorkflowRecord record) {
        if (record == null) {
            return null;
        }
        try {
            StageWorkflowRecordPO po = new StageWorkflowRecordPO();
            po.setId(record.getId() == null ? null : record.getId().getValue());
            po.setTenantId(record.getTenantId().getValue());
            po.setWorkflowId(record.getWorkflowId().getValue());
            po.setWorkflowVersion(record.getWorkflowVersion());
            po.setObjectType(record.getObjectType().name());
            po.setSubjectId(record.getSubjectId());
            po.setCurrentStageCode(record.getCurrentStageCode());
            po.setStatus(record.getStatus().name());
            po.setPendingApprovalInstanceId(record.getPendingApprovalInstanceId() == null ? null
                    : record.getPendingApprovalInstanceId().getValue());
            po.setProgressJson(objectMapper.writeValueAsString(record.getProgressByStage().values()
                    .stream().map(this::store).toList()));
            po.setOutcomeRemark(record.getOutcomeRemark());
            po.setCompletedTime(record.getCompletedTime());
            po.setVersion(record.getVersion());
            po.setCreatedTime(record.getCreatedTime());
            po.setUpdatedTime(record.getUpdatedTime());
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException("Stage workflow record cannot be serialized", exception);
        }
    }

    public StageWorkflowRecord toDomain(StageWorkflowRecordPO po) {
        if (po == null) {
            return null;
        }
        try {
            List<StoredProgress> stored = objectMapper.readValue(
                    po.getProgressJson(), new TypeReference<List<StoredProgress>>() { });
            Map<String, StageProgress> progress = stored.stream().map(this::restore)
                    .collect(Collectors.toMap(StageProgress::stageCode, value -> value,
                            (left, right) -> left, LinkedHashMap::new));
            return StageWorkflowRecord.reconstitute(new StageWorkflowRecord.Snapshot(
                    new StageWorkflowRecordId(po.getId()), new TenantId(po.getTenantId()),
                    new StageWorkflowId(po.getWorkflowId()), po.getWorkflowVersion(),
                    BusinessObjectType.valueOf(po.getObjectType()), po.getSubjectId(),
                    po.getCurrentStageCode(), StageWorkflowRecordStatus.valueOf(po.getStatus()),
                    po.getPendingApprovalInstanceId() == null ? null
                            : new ApprovalInstanceId(po.getPendingApprovalInstanceId()),
                    progress, po.getOutcomeRemark(), po.getVersion(), po.getCompletedTime(),
                    po.getCreatedTime(), po.getUpdatedTime()));
        } catch (Exception exception) {
            throw new IllegalStateException("Stored stage workflow record is invalid", exception);
        }
    }

    private StoredProgress store(StageProgress progress) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        progress.fieldValues().forEach((code, values) -> fields.put(code.getValue(), values));
        return new StoredProgress(progress.stageCode(), progress.status().name(), fields,
                progress.completedTaskCodes(), progress.approvalAttempt(),
                progress.startedTime(), progress.completedTime());
    }

    private StageProgress restore(StoredProgress progress) {
        Map<FieldCode, List<String>> fields = progress.fieldValues().entrySet().stream()
                .collect(Collectors.toMap(entry -> new FieldCode(entry.getKey()), Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
        return new StageProgress(progress.stageCode(), StageProgressStatus.valueOf(progress.status()),
                fields, progress.completedTaskCodes(), progress.approvalAttempt(),
                progress.startedTime(), progress.completedTime());
    }

    private record StoredProgress(
            String stageCode,
            String status,
            Map<String, List<String>> fieldValues,
            Set<String> completedTaskCodes,
            int approvalAttempt,
            LocalDateTime startedTime,
            LocalDateTime completedTime) {
    }
}
