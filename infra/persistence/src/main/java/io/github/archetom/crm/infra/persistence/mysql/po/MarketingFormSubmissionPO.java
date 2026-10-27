package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Append-only relational row for one successful public marketing-form submission. */
@Data
@TableName("crm_marketing_form_submission")
public class MarketingFormSubmissionPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long marketingFormId;
    private Long formVersion;
    private String targetType;
    private Long leadId;
    private Long customerId;
    private String idempotencyKey;
    private String submissionDigest;
    private LocalDateTime submittedTime;
}
