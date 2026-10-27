package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_approval_instance_file_fact")
public class ApprovalInstanceFileFactPO {
    private Long tenantId;
    private Long instanceId;
    private String objectType;
    private Long fieldId;
    private Long fileId;
    private Integer ordinal;
    private Long contentVersion;
    private byte[] evidenceDigest;
    private LocalDateTime createdTime;
}
