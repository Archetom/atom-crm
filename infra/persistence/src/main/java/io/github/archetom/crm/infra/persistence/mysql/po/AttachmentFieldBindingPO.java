package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_attachment_field_binding")
public class AttachmentFieldBindingPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long fileId;
    private String objectType;
    private Long fieldId;
    private Long customerId;
    private Long contactId;
    private Long leadId;
    private Long opportunityId;
    private Long contractId;
    private Long invoiceId;
    private Long receivableId;
    private Long receivablePlanId;
    private Long productId;
    private Long returnVisitId;
    private Integer ordinal;
    private Long contentVersion;
    private byte[] evidenceDigest;
    private String state;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
