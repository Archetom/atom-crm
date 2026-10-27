package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable capture marker, including the valid case where an approval has no FILE values. */
@Data
@TableName("crm_approval_instance_file_snapshot")
public class ApprovalInstanceFileSnapshotPO {
    private Long tenantId;
    private Long instanceId;
    private LocalDateTime createdTime;
}
