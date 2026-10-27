package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable FILE evidence captured when a stage is advanced. */
@Data
@TableName("crm_stage_workflow_file_fact")
public class StageWorkflowFileFactPO {
    private Long tenantId;
    private Long recordId;
    private String stageCode;
    private String objectType;
    private Long fieldId;
    private Long fileId;
    private Integer ordinal;
    private Long contentVersion;
    private byte[] evidenceDigest;
    private LocalDateTime createdTime;
}
