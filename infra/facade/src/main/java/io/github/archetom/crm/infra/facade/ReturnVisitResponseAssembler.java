package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.application.vo.ReturnVisitVO;

public final class ReturnVisitResponseAssembler {
    private ReturnVisitResponseAssembler() { }
    public static ReturnVisitResponse toResponse(ReturnVisitVO value) {
        return new ReturnVisitResponse().setId(value.id()).setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId()).setContactId(value.contactId())
                .setContactRelationState(value.contactRelationState()).setContractId(value.contractId())
                .setContractRelationState(value.contractRelationState()).setVisitNumber(value.visitNumber())
                .setVisitTime(value.visitTime()).setVisitForm(value.visitForm())
                .setSatisfaction(value.satisfaction()).setRemark(value.remark()).setStatus(value.status())
                .setApprovalStatus(value.approvalStatus())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setVersion(value.version())
                .setCreatedTime(value.createdTime()).setUpdatedTime(value.updatedTime());
    }
}
