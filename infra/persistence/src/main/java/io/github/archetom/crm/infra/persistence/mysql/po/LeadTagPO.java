package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_lead_tag")
public class LeadTagPO {
    private Long tenantId;
    private Long leadId;
    private String tagName;
    private String tagKey;
    private LocalDateTime createdTime;
}
