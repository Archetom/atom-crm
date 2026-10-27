package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ReceivablePlanResponse;
import io.github.archetom.crm.api.dto.response.ReceivableResponse;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.application.vo.ReceivableVO;

public final class ReceivableResponseAssembler {
    private ReceivableResponseAssembler() {
    }

    public static ReceivableResponse toResponse(ReceivableVO value) {
        return new ReceivableResponse().setId(value.id()).setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId()).setContractId(value.contractId())
                .setReceivablePlanId(value.receivablePlanId()).setReceiptNumber(value.receiptNumber())
                .setReceivedDate(value.receivedDate()).setPaymentMethod(value.paymentMethod())
                .setAmount(value.amount()).setRemark(value.remark()).setStatus(value.status())
                .setApprovalStatus(value.approvalStatus())
                .setCustomFields(value.customFields()).setFileFields(java.util.Map.of()).setVersion(value.version())
                .setCreatedTime(value.createdTime()).setUpdatedTime(value.updatedTime());
    }

    public static ReceivablePlanResponse toResponse(ReceivablePlanVO value) {
        return new ReceivablePlanResponse().setId(value.id()).setOwnerActorId(value.ownerActorId())
                .setCustomerId(value.customerId()).setContractId(value.contractId())
                .setInstallment(value.installment()).setDueDate(value.dueDate())
                .setPlannedAmount(value.plannedAmount()).setPaymentMethod(value.paymentMethod())
                .setReminderDays(value.reminderDays()).setRemark(value.remark())
                .setReceivedAmount(value.receivedAmount()).setRemainingAmount(value.remainingAmount())
                .setProgress(value.progress()).setStatus(value.status()).setCustomFields(value.customFields())
                .setFileFields(java.util.Map.of())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
