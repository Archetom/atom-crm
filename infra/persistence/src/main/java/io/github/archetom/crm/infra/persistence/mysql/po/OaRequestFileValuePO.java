package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_oa_request_file_value")
public class OaRequestFileValuePO {
    private Long tenantId;
    private Long oaRequestId;
    private String objectType;
    private Long fieldId;
    private Long fileId;
    private Integer ordinal;
    private Long contentVersion;
    private byte[] evidenceDigest;
    private LocalDateTime createdTime;
}
