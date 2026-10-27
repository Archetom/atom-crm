package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.InvoiceProfileResponse;
import io.github.archetom.crm.api.dto.response.InvoiceResponse;
import io.github.archetom.crm.application.vo.InvoiceProfileVO;
import io.github.archetom.crm.application.vo.InvoiceVO;

final class InvoiceResponseAssembler {
    private InvoiceResponseAssembler() {
    }

    static InvoiceResponse toResponse(InvoiceVO value) {
        return new InvoiceResponse().setId(value.id()).setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId()).setContractId(value.contractId())
                .setApplicationNumber(value.applicationNumber()).setAmount(value.amount())
                .setRequestedDate(value.requestedDate()).setInvoiceType(value.invoiceType())
                .setTitleType(value.titleType()).setTitle(value.title())
                .setTaxNumber(value.taxNumber()).setBankName(value.bankName())
                .setBankAccount(value.bankAccount())
                .setRegisteredAddress(value.registeredAddress())
                .setRegisteredPhone(value.registeredPhone())
                .setContactName(value.contactName()).setContactPhone(value.contactPhone())
                .setShippingAddress(value.shippingAddress()).setRemark(value.remark())
                .setStatus(value.status()).setInvoiceNumber(value.invoiceNumber())
                .setIssuedDate(value.issuedDate()).setLogisticsNumber(value.logisticsNumber())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setVersion(value.version())
                .setCreatedTime(value.createdTime()).setUpdatedTime(value.updatedTime());
    }

    static InvoiceProfileResponse toResponse(InvoiceProfileVO value) {
        return new InvoiceProfileResponse().setId(value.id())
                .setCustomerId(value.customerId()).setTitleType(value.titleType())
                .setTitle(value.title()).setTaxNumber(value.taxNumber())
                .setBankName(value.bankName()).setBankAccount(value.bankAccount())
                .setRegisteredAddress(value.registeredAddress())
                .setRegisteredPhone(value.registeredPhone()).setRemark(value.remark())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
