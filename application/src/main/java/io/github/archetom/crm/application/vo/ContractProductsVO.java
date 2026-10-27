package io.github.archetom.crm.application.vo;

import java.util.List;

/** Complete server-authoritative input for replacing contract product lines. */
public record ContractProductsVO(
        Long contractId,
        List<ContractProductLineVO> lines,
        Long version) {
}
