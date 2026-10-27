package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivablePlanBatchCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanDraftRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.service.ReceivablePlanCommandService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.exception.ContractNotFoundException;
import io.github.archetom.crm.domain.exception.ReceivablePlanNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReceivablePlanCommandServiceImpl implements ReceivablePlanCommandService {
    private final ReceivablePlanRepository repository;
    private final ReceivableRepository receivableRepository;
    private final ContractRepository contractRepository;
    private final ReceivableAccessResolver accessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final ContractApplicationSupport contractSupport;
    private final ReceivableApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final ReceivableOutputSupport output;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<ReceivablePlanVO> create(
            AuthenticatedCaller caller, ReceivablePlanCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_CREATE,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private ContractAccessResolver.ContractAccess contractAccess;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_WRITE,
                                ReceivableAccessMode.WRITE);
                        require(request, "Receivable plan create request");
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        requireCallerOwner(request.getOwnerActorId(), caller);
                    }

                    @Override
                    public ReceivablePlanVO execute() {
                        requireSameTenant(access, contractAccess);
                        objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE_PLAN);
                        Contract contract = lockActiveContract(contractAccess,
                                new ContractId(request.getContractId()));
                        List<FieldDefinition> fields = support.fields(access.tenantId(),
                                BusinessObjectType.RECEIVABLE_PLAN);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
                        requireCreateWrites(fieldAccess, request);
                        requireAvailablePlanAmount(contract, null, request.getPlannedAmount());
                        ReceivablePlan value = ReceivablePlan.create(access.tenantId(), access.memberId(),
                                contract.getCustomerId(), contract.getId(), repository.nextInstallment(
                                        access.tenantId(), contract.getId()), request.getDueDate(),
                                request.getPlannedAmount(), request.getPaymentMethod(),
                                request.getReminderDays(), request.getRemark(), support.customFields(
                                        request.getCustomFields(), List.of(), true,
                                        support.fieldsByCode(fields), fieldAccess, "Receivable plan"));
                        repository.save(access.tenantId(), value, fields);
                        fileFieldBindingService.consumeCreateDraft(access.tenantId(), access.memberId(),
                                BusinessObjectType.RECEIVABLE_PLAN, request.getFileFieldDraftId(),
                                new AttachmentTarget(AttachmentTargetType.RECEIVABLE_PLAN,
                                        value.getId().getValue()), request.getFileFields(),
                                support.fieldsByCode(fields), fieldAccess);
                        return output.plan(caller, value, access, fieldAccess);
                    }
                });
    }

    @Override
    public Result<List<ReceivablePlanVO>> createBatch(
            AuthenticatedCaller caller, ReceivablePlanBatchCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_BATCH_CREATE,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private ContractAccessResolver.ContractAccess contractAccess;

                    @Override
                    public void validate() {
                        require(request, "Receivable plan batch create request");
                        if (request.getPlans() == null || request.getPlans().isEmpty()
                                || request.getPlans().size() > 100
                                || request.getPlans().stream().anyMatch(java.util.Objects::isNull)) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Receivable plan batch is invalid");
                        }
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_WRITE,
                                ReceivableAccessMode.WRITE);
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        request.getPlans().forEach(plan -> requireCallerOwner(plan.getOwnerActorId(), caller));
                    }

                    @Override
                    public List<ReceivablePlanVO> execute() {
                        requireSameTenant(access, contractAccess);
                        objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE_PLAN);
                        Contract contract = lockActiveContract(contractAccess,
                                new ContractId(request.getContractId()));
                        List<FieldDefinition> fields = support.fields(access.tenantId(),
                                BusinessObjectType.RECEIVABLE_PLAN);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
                        BigDecimal requested = BigDecimal.ZERO;
                        for (ReceivablePlanDraftRequest plan : request.getPlans()) {
                            requireCreateWrites(fieldAccess, plan);
                            if (plan.getPlannedAmount() == null) throw support.error(
                                    ApplicationErrorCode.PARAMETER_INVALID,
                                    "Receivable plan amount is required");
                            requested = requested.add(plan.getPlannedAmount());
                        }
                        requireAvailablePlanAmount(contract, null, requested);
                        int installment = repository.nextInstallment(access.tenantId(), contract.getId());
                        List<ReceivablePlan> result = new ArrayList<>();
                        for (ReceivablePlanDraftRequest plan : request.getPlans()) {
                            ReceivablePlan value = ReceivablePlan.create(access.tenantId(), access.memberId(),
                                    contract.getCustomerId(), contract.getId(), installment++, plan.getDueDate(),
                                    plan.getPlannedAmount(), plan.getPaymentMethod(), plan.getReminderDays(),
                                    plan.getRemark(), support.customFields(plan.getCustomFields(), List.of(), true,
                                            support.fieldsByCode(fields), fieldAccess, "Receivable plan"));
                            repository.save(access.tenantId(), value, fields);
                            result.add(value);
                        }
                        return output.plans(caller, result, access, fieldAccess);
                    }
                });
    }

    @Override
    public Result<ReceivablePlanVO> update(
            AuthenticatedCaller caller, Long planIdValue, ReceivablePlanUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_UPDATE,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private ContractAccessResolver.ContractAccess contractAccess;
                    private ReceivablePlanId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_WRITE,
                                ReceivableAccessMode.WRITE);
                        require(request, "Receivable plan update request");
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        id = id(planIdValue);
                    }

                    @Override
                    public ReceivablePlanVO execute() {
                        requireSameTenant(access, contractAccess);
                        objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE_PLAN);
                        ReceivablePlan observed = repository.findVisibleById(
                                access.tenantId(), id, access.visibility()).orElseThrow(ReceivablePlanNotFoundException::new);
                        Contract contract = lockActiveContract(contractAccess, observed.getContractId());
                        ReceivablePlan value = repository.findVisibleByIdForUpdate(
                                access.tenantId(), id, access.visibility()).orElseThrow(ReceivablePlanNotFoundException::new);
                        support.requireVersion(value.getVersion(), request.getVersion(), "Receivable plan");
                        if (receivableRepository.existsByPlan(access.tenantId(), id)) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A receivable plan with receipt history cannot be changed");
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId(),
                                BusinessObjectType.RECEIVABLE_PLAN);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
                        requireUpdateWrites(fieldAccess);
                        BigDecimal plannedAmount = selected(request.getPlannedAmount(),
                                value.getPlannedAmount());
                        requireAvailablePlanAmount(contract, id, plannedAmount);
                        value.replaceDetails(selected(request.getDueDate(), value.getDueDate()),
                                plannedAmount, selected(request.getPaymentMethod(),
                                value.getPaymentMethod()), selected(request.getReminderDays(),
                                value.getReminderDays()), selected(request.getRemark(), value.getRemark()),
                                support.customFields(request.getCustomFields(), value.getCustomFieldValues(), false,
                                        support.fieldsByCode(fields), fieldAccess, "Receivable plan"));
                        repository.save(access.tenantId(), value, fields);
                        fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                                BusinessObjectType.RECEIVABLE_PLAN,
                                new AttachmentTarget(AttachmentTargetType.RECEIVABLE_PLAN,
                                        value.getId().getValue()), request.getFileFields(),
                                support.fieldsByCode(fields), fieldAccess);
                        return output.plan(caller, value, access, fieldAccess);
                    }
                });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long planIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_DELETE,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private ContractAccessResolver.ContractAccess contractAccess;
                    private ReceivablePlanId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_DELETE,
                                ReceivableAccessMode.OWNER_SCOPE);
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        id = id(planIdValue);
                    }

                    @Override
                    public Void execute() {
                        requireSameTenant(access, contractAccess);
                        objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE_PLAN);
                        ReceivablePlan observed = repository.findVisibleById(
                                access.tenantId(), id, access.visibility()).orElseThrow(ReceivablePlanNotFoundException::new);
                        contractRepository.findVisibleByIdForUpdate(contractAccess.tenantId(),
                                observed.getContractId(), contractAccess.visibility())
                                .orElseThrow(ContractNotFoundException::new);
                        ReceivablePlan value = repository.findVisibleByIdForUpdate(
                                access.tenantId(), id, access.visibility()).orElseThrow(ReceivablePlanNotFoundException::new);
                        support.requireVersion(value.getVersion(), version, "Receivable plan");
                        if (receivableRepository.existsByPlan(access.tenantId(), id)) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A receivable plan with receipt history cannot be deleted");
                        }
                        value.delete();
                        attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.RECEIVABLE_PLAN, id.getValue()));
                        fileFieldBindingService.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.RECEIVABLE_PLAN, id.getValue()));
                        repository.save(access.tenantId(), value,
                                support.fields(access.tenantId(), BusinessObjectType.RECEIVABLE_PLAN));
                        return null;
                    }
                });
    }

    private Contract lockActiveContract(
            ContractAccessResolver.ContractAccess access, ContractId id) {
        Contract contract = contractRepository.findVisibleByIdForUpdate(
                access.tenantId(), id, access.visibility()).orElseThrow(ContractNotFoundException::new);
        requireContractSourceFields(contractSupport.accesses(access.tenantId(), access.memberId()));
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Only active contracts can have receivable plans");
        }
        return contract;
    }

    private void requireContractSourceFields(Map<FieldCode, EffectiveFieldAccess> accesses) {
        contractSupport.requireReadableUnmasked(accesses, ContractFieldCatalog.CUSTOMER);
        contractSupport.requireReadableUnmasked(accesses, ContractFieldCatalog.TOTAL_AMOUNT);
    }

    private void requireAvailablePlanAmount(
            Contract contract, ReceivablePlanId excluding, BigDecimal requested) {
        BigDecimal existing = repository.sumActivePlannedAmount(contract.getTenantId(),
                contract.getId(), excluding);
        if (requested == null || existing.add(requested).compareTo(contract.getTotalAmount()) > 0) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Receivable plan amount exceeds the contract amount");
        }
    }

    private void requireCreateWrites(Map<FieldCode, EffectiveFieldAccess> accesses,
                                     ReceivablePlanCreateRequest request) {
        List.of(ReceivablePlanFieldCatalog.CUSTOMER, ReceivablePlanFieldCatalog.CONTRACT,
                ReceivablePlanFieldCatalog.DUE_DATE, ReceivablePlanFieldCatalog.PLANNED_AMOUNT,
                ReceivablePlanFieldCatalog.PAYMENT_METHOD, ReceivablePlanFieldCatalog.OWNER_ACTOR)
                .forEach(code -> support.requireWrite(accesses, code));
        if (request.getReminderDays() != null) {
            support.requireWrite(accesses, ReceivablePlanFieldCatalog.REMINDER_DAYS);
        }
        if (request.getRemark() != null) support.requireWrite(accesses, ReceivablePlanFieldCatalog.REMARK);
    }

    private void requireCreateWrites(Map<FieldCode, EffectiveFieldAccess> accesses,
                                     ReceivablePlanDraftRequest request) {
        List.of(ReceivablePlanFieldCatalog.CUSTOMER, ReceivablePlanFieldCatalog.CONTRACT,
                ReceivablePlanFieldCatalog.DUE_DATE, ReceivablePlanFieldCatalog.PLANNED_AMOUNT,
                ReceivablePlanFieldCatalog.PAYMENT_METHOD, ReceivablePlanFieldCatalog.OWNER_ACTOR)
                .forEach(code -> support.requireWrite(accesses, code));
        if (request.getReminderDays() != null) support.requireWrite(accesses,
                ReceivablePlanFieldCatalog.REMINDER_DAYS);
        if (request.getRemark() != null) support.requireWrite(accesses, ReceivablePlanFieldCatalog.REMARK);
    }

    private void requireUpdateWrites(Map<FieldCode, EffectiveFieldAccess> accesses) {
        List.of(ReceivablePlanFieldCatalog.DUE_DATE, ReceivablePlanFieldCatalog.PLANNED_AMOUNT,
                ReceivablePlanFieldCatalog.PAYMENT_METHOD, ReceivablePlanFieldCatalog.REMINDER_DAYS,
                ReceivablePlanFieldCatalog.REMARK).forEach(code -> support.requireWrite(accesses, code));
    }

    private void requireCallerOwner(Long ownerActorId, AuthenticatedCaller caller) {
        if (ownerActorId != null && !ownerActorId.equals(caller.actorId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Receivable plan owner must be the verified caller");
        }
    }

    private void requireSameTenant(ReceivableAccessResolver.ReceivableAccess plan,
                                   ContractAccessResolver.ContractAccess contract) {
        if (!plan.tenantId().sameValueAs(contract.tenantId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED, "Tenant context is inconsistent");
        }
    }

    private ReceivablePlanId id(Long value) {
        try {
            return new ReceivablePlanId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable plan ID is invalid");
        }
    }

    private void require(Object value, String description) {
        if (value == null) throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                description + " is required");
    }

    private <T> T selected(T requested, T existing) {
        return requested == null ? existing : requested;
    }
}
