package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_attachment_job")
public class AttachmentJobPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long fileId;
    private Long contentVersion;
    private String kind;
    private String state;
    private Long generation;
    private Long attempt;
    private LocalDateTime notBefore;
    private LocalDateTime deadline;
    private byte[] leaseOwner;
    private Long leaseFence;
    private LocalDateTime leaseUntil;
    private byte[] resultDigest;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
