package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_opportunity_contact")
public class OpportunityContactPO {
    private Long tenantId;
    private Long opportunityId;
    private Long contactId;
    private Long customerId;
    @TableField("is_primary")
    private Boolean primaryContact;
    private LocalDateTime createdTime;
}
