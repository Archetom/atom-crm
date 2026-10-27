package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

@Data
public class ApprovalSubjectStatusPO {
    private Long subjectId;
    private String status;
}
