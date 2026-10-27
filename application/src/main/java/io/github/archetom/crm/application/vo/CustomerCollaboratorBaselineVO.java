package io.github.archetom.crm.application.vo;

import java.util.List;

public record CustomerCollaboratorBaselineVO(
        Long version, List<CustomerCollaboratorVO> collaborators, String callerState) {
}
