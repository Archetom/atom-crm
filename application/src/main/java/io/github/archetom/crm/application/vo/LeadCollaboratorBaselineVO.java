package io.github.archetom.crm.application.vo;

import java.util.List;

public record LeadCollaboratorBaselineVO(
        Long version, List<LeadCollaboratorVO> collaborators, String callerState) {
}
