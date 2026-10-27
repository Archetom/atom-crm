package io.github.archetom.crm.application.vo;

import java.util.List;

/** Complete relationship values safe for an authorized opportunity contact replacement. */
public record OpportunityContactsBaselineVO(
        Long id,
        Long version,
        boolean editable,
        Long customerId,
        List<OpportunityContactVO> contacts) {
}
