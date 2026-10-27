package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable capture marker, including the valid case where a stage has no FILE values. */
@Data
@TableName("crm_stage_workflow_file_snapshot")
public class StageWorkflowFileSnapshotPO {
    private Long tenantId;
    private Long recordId;
    private String stageCode;
    private LocalDateTime createdTime;
}
