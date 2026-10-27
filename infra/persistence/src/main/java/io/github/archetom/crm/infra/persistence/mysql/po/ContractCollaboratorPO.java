package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contract_collaborator")
public class ContractCollaboratorPO {
    private Long tenantId;
    private Long contractId;
    private Long actorId;
    private String accessLevel;
    private java.time.LocalDate effectiveUntil;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
