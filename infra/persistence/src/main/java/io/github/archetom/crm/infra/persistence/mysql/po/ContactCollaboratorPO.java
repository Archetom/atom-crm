package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact_collaborator")
public class ContactCollaboratorPO {
    private Long tenantId;
    private Long contactId;
    private Long actorId;
    private String accessLevel;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
