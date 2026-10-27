package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.assembler.ReceivableAssembler;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.ApprovalInstanceRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ReceivableOutputSupport {
    private final CustomerAccessResolver customerAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final ReceivablePlanRepository planRepository;
    private final ReceivableRepository receivableRepository;
    private final ApprovalInstanceRepository approvalInstanceRepository;

    ReceivableVO receivable(
            AuthenticatedCaller caller, Receivable value,
            ReceivableAccessResolver.ReceivableAccess access,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return ReceivableAssembler.toVO(value, accesses,
                customerVisible(caller, access, value), contractVisible(caller, access, value),
                planVisible(caller, access, value), approvalStatus(access, List.of(value))
                        .get(value.getId().getValue()));
    }

    ReceivablePlanVO plan(
            AuthenticatedCaller caller, ReceivablePlan value,
            ReceivableAccessResolver.ReceivableAccess access,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        return ReceivableAssembler.toPlanVO(value, receivableRepository.sumActiveAmountByPlan(
                        access.tenantId(), value.getId(), null), accesses,
                customerVisible(caller, access, value), contractVisible(caller, access, value));
    }

    List<ReceivableVO> receivables(
            AuthenticatedCaller caller, List<Receivable> values,
            ReceivableAccessResolver.ReceivableAccess access,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (values == null || values.isEmpty()) return List.of();
        Set<CustomerId> customers = visibleCustomers(caller, access,
                values.stream().map(Receivable::getCustomerId).toList());
        Set<ContractId> contracts = visibleContracts(caller, access,
                values.stream().map(Receivable::getContractId).toList());
        Set<ReceivablePlanId> plans = visiblePlans(caller, access,
                values.stream().map(Receivable::getPlanId).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> approvalStatuses = approvalStatus(access, values);
        return values.stream().map(value -> ReceivableAssembler.toVO(value, accesses,
                customers.contains(value.getCustomerId()), contracts.contains(value.getContractId()),
                value.getPlanId() != null && plans.contains(value.getPlanId()),
                approvalStatuses.get(value.getId().getValue()))).toList();
    }

    List<ReceivablePlanVO> plans(
            AuthenticatedCaller caller, List<ReceivablePlan> values,
            ReceivableAccessResolver.ReceivableAccess access,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (values == null || values.isEmpty()) return List.of();
        Map<ReceivablePlanId, BigDecimal> receivedAmounts = receivableRepository
                .sumActiveAmountsByPlan(access.tenantId(), values.stream()
                        .map(ReceivablePlan::getId).toList());
        Set<CustomerId> customers = visibleCustomers(caller, access,
                values.stream().map(ReceivablePlan::getCustomerId).toList());
        Set<ContractId> contracts = visibleContracts(caller, access,
                values.stream().map(ReceivablePlan::getContractId).toList());
        return values.stream().map(value -> ReceivableAssembler.toPlanVO(value,
                receivedAmounts.getOrDefault(value.getId(), BigDecimal.ZERO), accesses,
                customers.contains(value.getCustomerId()), contracts.contains(value.getContractId()))).toList();
    }

    List<ReceivablePlanVO> plans(
            List<ReceivablePlan> values, Map<ReceivablePlanId, BigDecimal> receivedAmounts,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean customerVisible, boolean contractVisible) {
        if (values == null || values.isEmpty()) return List.of();
        Map<ReceivablePlanId, BigDecimal> amounts = receivedAmounts == null ? Map.of() : receivedAmounts;
        return values.stream().map(value -> ReceivableAssembler.toPlanVO(value,
                amounts.getOrDefault(value.getId(), BigDecimal.ZERO), accesses,
                customerVisible, contractVisible)).toList();
    }

    private boolean customerVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            Receivable value) {
        return customerVisible(caller, receiptAccess, value.getCustomerId());
    }

    boolean customerVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            ReceivablePlan value) {
        return customerVisible(caller, receiptAccess, value.getCustomerId());
    }

    private boolean customerVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            io.github.archetom.crm.domain.valueobject.CustomerId customerId) {
        try {
            var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                    CustomerAccessMode.READ);
            return access.tenantId().sameValueAs(receiptAccess.tenantId())
                    && customerRepository.findVisibleById(access.tenantId(), customerId,
                    access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean contractVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            Receivable value) {
        return contractVisible(caller, receiptAccess, value.getContractId());
    }

    private boolean contractVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            ReceivablePlan value) {
        return contractVisible(caller, receiptAccess, value.getContractId());
    }

    private boolean contractVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            io.github.archetom.crm.domain.valueobject.ContractId contractId) {
        try {
            var access = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                    ContractAccessMode.READ);
            return access.tenantId().sameValueAs(receiptAccess.tenantId())
                    && contractRepository.findVisibleById(access.tenantId(), contractId,
                    access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private boolean planVisible(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            Receivable value) {
        if (value.getPlanId() == null) return false;
        try {
            var access = receivableAccessResolver.access(caller,
                    AuthorityCatalog.RECEIVABLE_PLANS_READ, ReceivableAccessMode.READ);
            return access.tenantId().sameValueAs(receiptAccess.tenantId())
                    && planRepository.findVisibleById(access.tenantId(), value.getPlanId(),
                    access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private Map<Long, String> approvalStatus(
            ReceivableAccessResolver.ReceivableAccess access, Collection<Receivable> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return approvalInstanceRepository.findLatestSubjectStatuses(access.tenantId(),
                        BusinessObjectType.RECEIVABLE, values.stream()
                                .map(value -> value.getId().getValue()).toList())
                .stream().collect(Collectors.toMap(approval -> approval.subjectId(),
                        approval -> approval.status().name()));
    }

    private Set<CustomerId> visibleCustomers(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            Collection<CustomerId> customerIds) {
        if (customerIds == null || customerIds.isEmpty()
                || !caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)) return Set.of();
        try {
            var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                    CustomerAccessMode.READ);
            if (!access.tenantId().sameValueAs(receiptAccess.tenantId())) return Set.of();
            List<CustomerId> ids = customerIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
            return ids.isEmpty() ? Set.of() : customerRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size()).stream()
                    .map(io.github.archetom.crm.domain.entity.Customer::getId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private Set<ContractId> visibleContracts(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            Collection<ContractId> contractIds) {
        if (contractIds == null || contractIds.isEmpty()
                || !caller.hasAuthority(AuthorityCatalog.CONTRACTS_READ)) return Set.of();
        try {
            var access = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                    ContractAccessMode.READ);
            if (!access.tenantId().sameValueAs(receiptAccess.tenantId())) return Set.of();
            List<ContractId> ids = contractIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
            return ids.isEmpty() ? Set.of() : contractRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size()).stream()
                    .map(io.github.archetom.crm.domain.entity.Contract::getId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private Set<ReceivablePlanId> visiblePlans(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess receiptAccess,
            Collection<ReceivablePlanId> planIds) {
        if (planIds == null || planIds.isEmpty()
                || !caller.hasAuthority(AuthorityCatalog.RECEIVABLE_PLANS_READ)) return Set.of();
        try {
            var access = receivableAccessResolver.access(caller,
                    AuthorityCatalog.RECEIVABLE_PLANS_READ, ReceivableAccessMode.READ);
            if (!access.tenantId().sameValueAs(receiptAccess.tenantId())) return Set.of();
            List<ReceivablePlanId> ids = planIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
            return ids.isEmpty() ? Set.of() : planRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size()).stream()
                    .map(ReceivablePlan::getId).collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }
}
