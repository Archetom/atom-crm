package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_attachment_read_lease")
public class AttachmentReadLeasePO {
    @TableId(type = IdType.INPUT)
    private byte[] leaseId;
    private Long tenantId;
    private Long fileId;
    private Long contentVersion;
    private Long leaseFence;
    private String state;
    private LocalDateTime expiresAt;
    private LocalDateTime releasedTime;
    private LocalDateTime createdTime;
}
