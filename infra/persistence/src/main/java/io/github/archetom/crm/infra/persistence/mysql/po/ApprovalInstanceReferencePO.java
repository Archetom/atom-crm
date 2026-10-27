package io.github.archetom.crm.infra.persistence.mysql.po;

import lombok.Data;

/** Minimal approval row used while scanning business-record visibility. */
@Data
public class ApprovalInstanceReferencePO {
    private Long id;
    private String objectType;
    private Long subjectId;
}
