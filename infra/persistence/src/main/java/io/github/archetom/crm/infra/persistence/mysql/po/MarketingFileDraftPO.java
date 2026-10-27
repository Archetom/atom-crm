package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_marketing_file_draft")
public class MarketingFileDraftPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long marketingFormId;
    private Long formVersion;
    private byte[] credentialDigest;
    private LocalDateTime deadline;
    private String state;
    private Long reservedBytes;
    private Long actualBytes;
    @Version private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
