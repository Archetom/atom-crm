package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_lead_collaborator")
public class LeadCollaboratorPO {
    private Long tenantId;
    private Long leadId;
    private Long actorId;
    private String accessLevel;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
