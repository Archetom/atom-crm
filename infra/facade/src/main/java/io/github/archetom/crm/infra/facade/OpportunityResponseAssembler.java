package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.OpportunityCollaboratorResponse;
import io.github.archetom.crm.api.dto.response.OpportunityCollaboratorBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityCallerCollaboratorStateResponse;
import io.github.archetom.crm.api.dto.response.OpportunityContactResponse;
import io.github.archetom.crm.api.dto.response.OpportunityContactsBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityEditableBaselineResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.application.vo.OpportunityCollaboratorVO;
import io.github.archetom.crm.application.vo.OpportunityCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityContactVO;
import io.github.archetom.crm.application.vo.OpportunityContactsBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityEditableBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityVO;

final class OpportunityResponseAssembler {

    private OpportunityResponseAssembler() {
    }

    static OpportunityResponse toResponse(OpportunityVO value) {
        if (value == null) {
            return null;
        }
        return new OpportunityResponse().setId(value.id())
                .setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId())
                .setPrimaryContactId(value.primaryContactId())
                .setName(value.name()).setPipelineCode(value.pipelineCode())
                .setStageCode(value.stageCode())
                .setExpectedCloseDate(value.expectedCloseDate())
                .setEstimatedAmount(value.estimatedAmount())
                .setNextContactTime(value.nextContactTime())
                .setDescription(value.description()).setOutcome(value.outcome())
                .setOutcomeTime(value.outcomeTime())
                .setOutcomeRemark(value.outcomeRemark()).setStatus(value.status())
                .setContactIds(value.contactIds())
                .setCollaborators(value.collaborators().stream()
                        .map(OpportunityResponseAssembler::toResponse).toList())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setStarred(value.starred())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime()).setLastActivityTime(value.lastActivityTime());
    }

    static OpportunityContactResponse toResponse(OpportunityContactVO value) {
        return new OpportunityContactResponse().setId(value.id()).setName(value.name())
                .setPrimary(value.primary());
    }

    static OpportunityContactsBaselineResponse toResponse(
            OpportunityContactsBaselineVO value) {
        OpportunityContactsBaselineResponse response = new OpportunityContactsBaselineResponse()
                .setId(value.id()).setVersion(value.version()).setEditable(value.editable());
        if (!value.editable()) {
            return response;
        }
        return response.setCustomerId(value.customerId()).setContacts(value.contacts().stream()
                .map(OpportunityResponseAssembler::toResponse).toList());
    }

    static OpportunityEditableBaselineResponse toResponse(
            OpportunityEditableBaselineVO value) {
        OpportunityEditableBaselineResponse response = new OpportunityEditableBaselineResponse()
                .setId(value.id()).setVersion(value.version()).setEditable(value.editable());
        if (!value.editable()) {
            return response;
        }
        return response.setName(value.name()).setCustomerId(value.customerId())
                .setPipelineCode(value.pipelineCode()).setStageCode(value.stageCode())
                .setExpectedCloseDate(value.expectedCloseDate())
                .setEstimatedAmount(value.estimatedAmount())
                .setNextContactTime(value.nextContactTime()).setDescription(value.description());
    }

    static OpportunityCollaboratorBaselineResponse toResponse(
            OpportunityCollaboratorBaselineVO value) {
        return new OpportunityCollaboratorBaselineResponse().setVersion(value.version())
                .setCollaborators(value.collaborators().stream()
                        .map(OpportunityResponseAssembler::toResponse).toList())
                .setCallerState(OpportunityCallerCollaboratorStateResponse
                        .valueOf(value.callerState()));
    }

    private static OpportunityCollaboratorResponse toResponse(
            OpportunityCollaboratorVO value) {
        return new OpportunityCollaboratorResponse().setActorId(value.actorId())
                .setAccess(value.access());
    }
}
