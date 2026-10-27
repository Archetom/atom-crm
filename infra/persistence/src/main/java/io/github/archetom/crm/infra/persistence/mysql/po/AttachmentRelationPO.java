package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_attachment_relation")
public class AttachmentRelationPO {
    private Long tenantId;
    private Long fileId;
    private String targetType;
    private Long workTaskId;
    private Long customerId;
    private Long contactId;
    private Long leadId;
    private Long opportunityId;
    private Long contractId;
    private Long invoiceId;
    private Long receivableId;
    private Long receivablePlanId;
    private Long productId;
    private String productImageType;
    private Long returnVisitId;
    private Long marketingActivityId;
    private String marketingActivityImageType;
    private Long oaAnnouncementId;
    private Long oaRequestId;
    private Long importDraftId;
    private String relationState;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
