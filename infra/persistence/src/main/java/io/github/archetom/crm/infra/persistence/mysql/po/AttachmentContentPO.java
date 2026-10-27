package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_attachment_content")
public class AttachmentContentPO {
    private Long tenantId;
    private Long fileId;
    private Long contentVersion;
    private Long intentVersion;
    private byte[] bindingRef;
    private byte[] plannedLocator;
    private byte[] storedIdentity;
    private byte[] storageGeneration;
    private Long attempt;
    private Long actualBytes;
    private byte[] sha256;
    private LocalDateTime uploadDeadline;
    private LocalDateTime storedTime;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
