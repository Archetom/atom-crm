package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** A department selected as a marketing activity participant without expanding it into members. */
@Data
@TableName("crm_marketing_activity_participant_department")
public class MarketingActivityParticipantDepartmentPO {
    private Long tenantId;
    private Long marketingActivityId;
    private Long departmentId;
}
