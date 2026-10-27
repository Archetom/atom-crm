package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_marketing_file_draft_candidate")
public class MarketingFileDraftCandidatePO {
    private Long tenantId;
    private Long draftId;
    private String objectType;
    private Long fieldId;
    private Long fileId;
    private Integer ordinal;
    private String state;
    private Long contentVersion;
    private byte[] evidenceDigest;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
