package io.github.archetom.crm.application.vo;

import java.util.List;

public record ContactCollaboratorBaselineVO(
        Long version, List<ContactCollaboratorVO> collaborators, String callerState) {
}
