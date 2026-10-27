package io.github.archetom.crm.application.vo;

import java.util.List;

public record ContractCollaboratorBaselineVO(
        Long version, List<ContractCollaboratorVO> collaborators, String callerState) {
}
