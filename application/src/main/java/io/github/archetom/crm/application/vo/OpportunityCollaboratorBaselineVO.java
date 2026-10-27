package io.github.archetom.crm.application.vo;

import java.util.List;

public record OpportunityCollaboratorBaselineVO(
        Long version, List<OpportunityCollaboratorVO> collaborators, String callerState) {
}
