package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Singleton durable arbitration row for deployment-wide attachment byte capacity. */
@Data
@TableName("crm_attachment_global_usage")
public class AttachmentGlobalUsagePO {
    @TableId(type = IdType.INPUT)
    private Long singletonId;
    private Long readyBytes;
    private Long quarantineBytes;
    private Long pendingBytes;
    private Long version;
    private LocalDateTime updatedTime;
}
