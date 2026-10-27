package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ReceivableBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.service.ReceivableCommandService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.exception.ContractNotFoundException;
import io.github.archetom.crm.domain.exception.ReceivableNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceivableCommandServiceImpl implements ReceivableCommandService {
    private final ReceivableRepository repository;
    private final ReceivablePlanRepository planRepository;
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
    public Result<ReceivableVO> create(
            AuthenticatedCaller caller, ReceivableCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_CREATE, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private ContractAccessResolver.ContractAccess contractAccess;
            private ReceivableAccessResolver.ReceivableAccess planAccess;
            private ReceivableAccessResolver.ReceivableAccess assignAccess;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_WRITE,
                        ReceivableAccessMode.WRITE);
                require(request, "Receivable create request");
                contractAccess = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                        ContractAccessMode.READ);
                if (request.getReceivablePlanId() != null) {
                    planAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                            ReceivableAccessMode.READ);
                }
                if (request.getOwnerActorId() != null
                        && !request.getOwnerActorId().equals(caller.actorId())) {
                    assignAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_ASSIGN,
                            ReceivableAccessMode.OWNER_SCOPE);
                }
            }

            @Override
            public ReceivableVO execute() {
                requireSameTenant(access, contractAccess);
                objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE);
                Contract contract = lockContract(contractAccess, new ContractId(request.getContractId()));
                List<FieldDefinition> fields = support.fields(access.tenantId(), BusinessObjectType.RECEIVABLE);
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                        access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE);
                requireCreateWrites(fieldAccess, request);
                MemberId owner = request.getOwnerActorId() == null ? access.memberId()
                        : new MemberId(request.getOwnerActorId());
                OrganizationMembership membership = support.requireActiveMember(access.tenantId(), owner);
                if (assignAccess != null) requireAssignableOwner(membership, assignAccess);
                ReceivablePlanId planId = request.getReceivablePlanId() == null ? null
                        : new ReceivablePlanId(request.getReceivablePlanId());
                validatePlan(planAccess, contract, planId, planFieldAccess(access, planId));
                requireAvailableAmount(contract, null, planId, request.getAmount());
                Receivable value = Receivable.create(access.tenantId(), owner, contract.getCustomerId(),
                        contract.getId(), planId, receiptNumber(), request.getReceivedDate(),
                        request.getPaymentMethod(), request.getAmount(), request.getRemark(),
                        support.customFields(request.getCustomFields(), List.of(), true,
                                support.fieldsByCode(fields), fieldAccess, "Receivable"));
                repository.save(access.tenantId(), value, fields);
                fileFieldBindingService.consumeCreateDraft(access.tenantId(), access.memberId(),
                        BusinessObjectType.RECEIVABLE, request.getFileFieldDraftId(),
                        new AttachmentTarget(AttachmentTargetType.RECEIVABLE, value.getId().getValue()),
                        request.getFileFields(), support.fieldsByCode(fields), fieldAccess);
                return output.receivable(caller, value, access, fieldAccess);
            }
        });
    }

    @Override
    public Result<ReceivableVO> update(
            AuthenticatedCaller caller, Long receivableIdValue, ReceivableUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_UPDATE, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private ContractAccessResolver.ContractAccess contractAccess;
            private ReceivableAccessResolver.ReceivableAccess planAccess;
            private ReceivableId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_WRITE,
                        ReceivableAccessMode.WRITE);
                require(request, "Receivable update request");
                contractAccess = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                        ContractAccessMode.READ);
                if (request.getReceivablePlanId() != null) {
                    planAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                            ReceivableAccessMode.READ);
                }
                id = id(receivableIdValue);
            }

            @Override
            public ReceivableVO execute() {
                requireSameTenant(access, contractAccess);
                objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE);
                Receivable observed = repository.findVisibleById(access.tenantId(), id, access.visibility())
                        .orElseThrow(ReceivableNotFoundException::new);
                Contract contract = lockContract(contractAccess, observed.getContractId());
                Receivable value = repository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility()).orElseThrow(ReceivableNotFoundException::new);
                if (!contract.getId().sameValueAs(value.getContractId())) {
                    throw support.error(ApplicationErrorCode.VERSION_CONFLICT,
                            "Receivable contract changed concurrently");
                }
                support.requireVersion(value.getVersion(), request.getVersion(), "Receivable");
                List<FieldDefinition> fields = support.fields(access.tenantId(), BusinessObjectType.RECEIVABLE);
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                        access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE);
                requireUpdateWrites(fieldAccess);
                boolean planRequested = request.getReceivablePlanId() != null;
                ReceivablePlanId planId = planRequested
                        ? new ReceivablePlanId(request.getReceivablePlanId()) : value.getPlanId();
                if (planRequested) {
                    requirePlanAssociationImmutable(value, planId);
                    validatePlan(planAccess, contract, planId, planFieldAccess(access, planId));
                }
                BigDecimal amount = selected(request.getAmount(), value.getAmount());
                requireAvailableAmount(contract, id, planId, amount);
                value.replaceDetails(planId,
                        selected(request.getReceivedDate(), value.getReceivedDate()),
                        selected(request.getPaymentMethod(), value.getPaymentMethod()),
                        amount, selected(request.getRemark(), value.getRemark()), support.customFields(
                                request.getCustomFields(), value.getCustomFieldValues(), false,
                                support.fieldsByCode(fields), fieldAccess, "Receivable"));
                repository.save(access.tenantId(), value, fields);
                fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                        BusinessObjectType.RECEIVABLE,
                        new AttachmentTarget(AttachmentTargetType.RECEIVABLE, value.getId().getValue()),
                        request.getFileFields(), support.fieldsByCode(fields), fieldAccess);
                return output.receivable(caller, value, access, fieldAccess);
            }
        });
    }

    @Override
    public Result<ReceivableVO> updateOwner(
            AuthenticatedCaller caller, Long receivableIdValue, ReceivableOwnerUpdateRequest request) {
        require(request, "Receivable owner request");
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_OWNER_UPDATE, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private ReceivableId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_ASSIGN,
                        ReceivableAccessMode.OWNER_SCOPE);
                id = id(receivableIdValue);
            }

            @Override
            public ReceivableVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE);
                Receivable value = repository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility()).orElseThrow(ReceivableNotFoundException::new);
                support.requireVersion(value.getVersion(), request.getVersion(), "Receivable");
                Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                        access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE);
                support.requireWrite(fields, ReceivableFieldCatalog.OWNER_ACTOR);
                MemberId owner = new MemberId(request.getOwnerActorId());
                requireAssignableOwner(support.requireActiveMember(access.tenantId(), owner), access);
                value.changeOwner(owner);
                repository.save(access.tenantId(), value,
                        support.fields(access.tenantId(), BusinessObjectType.RECEIVABLE));
                return output.receivable(caller, value, access, fields);
            }
        });
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, ReceivableBatchOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_BATCH_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private List<ReceivableBatchVersionRequest> targets;
                    private MemberId owner;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_ASSIGN,
                                ReceivableAccessMode.OWNER_SCOPE);
                        require(request, "Receivable batch owner request");
                        targets = batchTargets(request.getReceivables(), "Receivable batch owner targets");
                        owner = new MemberId(request.getOwnerActorId());
                        requireAssignableOwner(support.requireActiveMember(access.tenantId(), owner), access);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE);
                        Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE);
                        support.requireWrite(fields, ReceivableFieldCatalog.OWNER_ACTOR);
                        List<Receivable> receivables = batchReceivables(access, targets);
                        for (int index = 0; index < receivables.size(); index++) {
                            support.requireVersion(receivables.get(index).getVersion(),
                                    targets.get(index).getVersion(), "Receivable");
                        }
                        List<FieldDefinition> definitions = support.fields(
                                access.tenantId(), BusinessObjectType.RECEIVABLE);
                        for (Receivable receivable : receivables) {
                            receivable.changeOwner(owner);
                            repository.save(access.tenantId(), receivable, definitions);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long receivableIdValue, Long version) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_DELETE, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private ReceivableId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_DELETE,
                        ReceivableAccessMode.OWNER_SCOPE);
                id = id(receivableIdValue);
            }

            @Override
            public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE);
                Receivable value = repository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility()).orElseThrow(ReceivableNotFoundException::new);
                support.requireVersion(value.getVersion(), version, "Receivable");
                value.delete();
                attachmentParentLifecycle.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.RECEIVABLE, id.getValue()));
                fileFieldBindingService.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.RECEIVABLE, id.getValue()));
                repository.save(access.tenantId(), value,
                        support.fields(access.tenantId(), BusinessObjectType.RECEIVABLE));
                return null;
            }
        });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, ReceivableBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.RECEIVABLE_BATCH_DELETE,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private List<ReceivableBatchVersionRequest> targets;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_DELETE,
                                ReceivableAccessMode.OWNER_SCOPE);
                        require(request, "Receivable batch delete request");
                        targets = batchTargets(request.getReceivables(), "Receivable batch delete targets");
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.RECEIVABLE);
                        List<Receivable> receivables = batchReceivables(access, targets);
                        for (int index = 0; index < receivables.size(); index++) {
                            support.requireVersion(receivables.get(index).getVersion(),
                                    targets.get(index).getVersion(), "Receivable");
                        }
                        List<FieldDefinition> definitions = support.fields(
                                access.tenantId(), BusinessObjectType.RECEIVABLE);
                        for (Receivable receivable : receivables) {
                            ReceivableId receivableId = receivable.getId();
                            receivable.delete();
                            attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.RECEIVABLE,
                                            receivableId.getValue()));
                            fileFieldBindingService.withdrawAll(access.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.RECEIVABLE,
                                            receivableId.getValue()));
                            repository.save(access.tenantId(), receivable, definitions);
                        }
                        return null;
                    }
                });
    }

    private List<ReceivableBatchVersionRequest> batchTargets(
            List<ReceivableBatchVersionRequest> requests, String description) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, description + " are invalid");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (ReceivableBatchVersionRequest request : requests) {
            if (request == null || request.getReceivableId() == null || request.getVersion() == null
                    || request.getVersion() < 0 || !ids.add(request.getReceivableId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID, description + " are invalid");
            }
            id(request.getReceivableId());
        }
        return requests.stream().sorted(Comparator.comparing(ReceivableBatchVersionRequest::getReceivableId))
                .toList();
    }

    private List<Receivable> batchReceivables(
            ReceivableAccessResolver.ReceivableAccess access,
            List<ReceivableBatchVersionRequest> targets) {
        return targets.stream().map(target -> repository.findVisibleByIdForUpdate(
                        access.tenantId(), id(target.getReceivableId()), access.visibility())
                .orElseThrow(ReceivableNotFoundException::new)).toList();
    }

    private Contract lockContract(
            ContractAccessResolver.ContractAccess access, ContractId id) {
        Contract contract = contractRepository.findVisibleByIdForUpdate(
                access.tenantId(), id, access.visibility()).orElseThrow(ContractNotFoundException::new);
        requireContractSourceFields(contractSupport.accesses(access.tenantId(), access.memberId()));
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Only active contracts can receive payments");
        }
        return contract;
    }

    private void requireContractSourceFields(Map<FieldCode, EffectiveFieldAccess> accesses) {
        contractSupport.requireReadableUnmasked(accesses, ContractFieldCatalog.CUSTOMER);
        contractSupport.requireReadableUnmasked(accesses, ContractFieldCatalog.TOTAL_AMOUNT);
    }

    private void validatePlan(ReceivableAccessResolver.ReceivableAccess access,
                              Contract contract, ReceivablePlanId planId,
                              Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (planId == null) return;
        if (access == null) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Receivable plan access is unavailable");
        }
        List.of(ReceivablePlanFieldCatalog.CUSTOMER, ReceivablePlanFieldCatalog.CONTRACT,
                        ReceivablePlanFieldCatalog.PLANNED_AMOUNT,
                        ReceivablePlanFieldCatalog.RECEIVED_AMOUNT,
                        ReceivablePlanFieldCatalog.REMAINING_AMOUNT)
                .forEach(code -> support.requireReadableUnmasked(accesses, code));
        ReceivablePlan plan = planRepository.findVisibleByIdForUpdate(
                access.tenantId(), planId, access.visibility()).orElseThrow(() ->
                support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Receivable plan was not found"));
        if (!plan.getContractId().sameValueAs(contract.getId())
                || !plan.getCustomerId().sameValueAs(contract.getCustomerId())) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan does not belong to the contract");
        }
    }

    private Map<FieldCode, EffectiveFieldAccess> planFieldAccess(
            ReceivableAccessResolver.ReceivableAccess access, ReceivablePlanId planId) {
        return planId == null ? Map.of() : support.accesses(
                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
    }

    private void requirePlanAssociationImmutable(Receivable value, ReceivablePlanId requestedPlanId) {
        if (value.getPlanId() != null && (requestedPlanId == null
                || !value.getPlanId().sameValueAs(requestedPlanId))) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "A receivable plan association cannot be changed");
        }
    }

    private void requireAvailableAmount(
            Contract contract, ReceivableId excluding, ReceivablePlanId planId, BigDecimal amount) {
        BigDecimal committed = repository.sumActiveAmount(contract.getTenantId(), contract.getId(), excluding);
        if (amount == null || committed.add(amount).compareTo(contract.getTotalAmount()) > 0) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Receivable amount exceeds the remaining contract amount");
        }
        if (planId != null) {
            ReceivablePlan plan = planRepository.findById(contract.getTenantId(), planId)
                    .orElseThrow(() -> support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Receivable plan was not found"));
            BigDecimal applied = repository.sumActiveAmountByPlan(contract.getTenantId(), planId, excluding);
            if (applied.add(amount).compareTo(plan.getPlannedAmount()) > 0) {
                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Receivable amount exceeds the remaining plan amount");
            }
        }
    }

    private void requireCreateWrites(Map<FieldCode, EffectiveFieldAccess> accesses,
                                     ReceivableCreateRequest request) {
        List.of(ReceivableFieldCatalog.CUSTOMER, ReceivableFieldCatalog.CONTRACT,
                ReceivableFieldCatalog.RECEIVED_DATE, ReceivableFieldCatalog.PAYMENT_METHOD,
                ReceivableFieldCatalog.AMOUNT, ReceivableFieldCatalog.OWNER_ACTOR)
                .forEach(code -> support.requireWrite(accesses, code));
        if (request.getReceivablePlanId() != null) support.requireWrite(accesses, ReceivableFieldCatalog.PLAN);
        if (request.getRemark() != null) support.requireWrite(accesses, ReceivableFieldCatalog.REMARK);
    }

    private void requireUpdateWrites(Map<FieldCode, EffectiveFieldAccess> accesses) {
        List.of(ReceivableFieldCatalog.PLAN, ReceivableFieldCatalog.RECEIVED_DATE,
                ReceivableFieldCatalog.PAYMENT_METHOD, ReceivableFieldCatalog.AMOUNT,
                ReceivableFieldCatalog.REMARK).forEach(code -> support.requireWrite(accesses, code));
    }

    private void requireAssignableOwner(OrganizationMembership owner,
                                        ReceivableAccessResolver.ReceivableAccess access) {
        if (access.visibility().dataScope() == DataScope.TENANT) return;
        if (access.visibility().dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(access.memberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Receivable owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !access.visibility().ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Receivable owner is outside the assignment scope");
        }
    }

    private void requireSameTenant(ReceivableAccessResolver.ReceivableAccess receipt,
                                   ContractAccessResolver.ContractAccess contract) {
        if (!receipt.tenantId().sameValueAs(contract.tenantId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED, "Tenant context is inconsistent");
        }
    }

    private ReceivableId id(Long value) {
        try {
            return new ReceivableId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable ID is invalid");
        }
    }

    private String receiptNumber() {
        return "RCV-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    private void require(Object value, String description) {
        if (value == null) throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                description + " is required");
    }

    private <T> T selected(T requested, T existing) {
        return requested == null ? existing : requested;
    }
}
