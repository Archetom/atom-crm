package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_attachment_tenant_usage")
public class AttachmentTenantUsagePO {
    @TableId(type = IdType.INPUT)
    private Long tenantId;
    private Long readyBytes;
    private Long quarantineBytes;
    private Long pendingBytes;
    private Long pendingIntents;
    private Long activeUploads;
    private Long activeInspections;
    private Long activeDownloads;
    private Long version;
    private LocalDateTime updatedTime;
}
