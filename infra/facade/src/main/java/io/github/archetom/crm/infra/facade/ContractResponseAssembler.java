package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ContractCollaboratorResponse;
import io.github.archetom.crm.api.dto.response.ContractProductLineResponse;
import io.github.archetom.crm.api.dto.response.ContractResponse;
import io.github.archetom.crm.application.vo.ContractCollaboratorVO;
import io.github.archetom.crm.application.vo.ContractProductLineVO;
import io.github.archetom.crm.application.vo.ContractVO;

final class ContractResponseAssembler {
    private ContractResponseAssembler() {
    }

    static ContractResponse toResponse(ContractVO value) {
        return new ContractResponse().setId(value.id())
                .setOwnerActorId(value.ownerActorId()).setCustomerId(value.customerId())
                .setOpportunityId(value.opportunityId()).setContactId(value.contactId())
                .setName(value.name()).setNumber(value.number())
                .setOrderDate(value.orderDate()).setStartDate(value.startDate())
                .setEndDate(value.endDate()).setContractType(value.contractType())
                .setPaymentType(value.paymentType()).setRemark(value.remark())
                .setSubtotal(value.subtotal()).setDiscountRate(value.discountRate())
                .setTotalAmount(value.totalAmount()).setStatus(value.status())
                .setProducts(value.products().stream().map(
                        ContractResponseAssembler::toResponse).toList())
                .setCollaborators(value.collaborators().stream().map(
                        ContractResponseAssembler::toResponse).toList())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setVersion(value.version())
                .setCreatedTime(value.createdTime()).setUpdatedTime(value.updatedTime());
    }

    private static ContractProductLineResponse toResponse(ContractProductLineVO value) {
        return new ContractProductLineResponse().setProductId(value.productId())
                .setProductName(value.productName()).setProductCode(value.productCode())
                .setCategoryName(value.categoryName()).setUnit(value.unit())
                .setListPrice(value.listPrice()).setUnitPrice(value.unitPrice())
                .setQuantity(value.quantity()).setDiscountRate(value.discountRate())
                .setLineAmount(value.lineAmount()).setDisplayOrder(value.displayOrder());
    }

    private static ContractCollaboratorResponse toResponse(ContractCollaboratorVO value) {
        return new ContractCollaboratorResponse().setActorId(value.actorId())
                .setAccess(value.access()).setEffectiveUntil(value.effectiveUntil());
    }
}
