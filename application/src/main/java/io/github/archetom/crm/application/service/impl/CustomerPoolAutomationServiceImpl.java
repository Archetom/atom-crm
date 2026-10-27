package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.CustomerPoolAutomationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolMembership;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CustomerPoolAutomationServiceImpl implements CustomerPoolAutomationService {

    private static final int POOL_SCAN_PAGE_SIZE = 25;
    private static final int MAX_POOLS_PER_RUN = 50;

    private final CustomerPoolRepository poolRepository;
    private final CustomerRepository customerRepository;
    private final CustomerApplicationSupport customerSupport;
    private final OrganizationAccessGuard accessGuard;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<Integer> run(
            AuthenticatedCaller caller, LocalDateTime asOf, Integer maxCustomers) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_AUTOMATION,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private int limit;
                    private LocalDateTime evaluationTime;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE);
                        evaluationTime = asOf == null ? LocalDateTime.now() : asOf;
                        limit = maxCustomers == null ? 200 : maxCustomers;
                        if (limit < 1 || limit > 1_000) {
                            throw customerSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Customer pool automation limit is invalid");
                        }
                    }

                    @Override
                    public Integer execute() {
                        return release(tenantId, evaluationTime, evaluationTime, limit);
                    }
                });
    }

    @Override
    public int runSystem(TenantId tenantId, LocalDateTime evaluationTime, int maxCustomers,
                         Supplier<LocalDateTime> committedTimeSupplier) {
        if (tenantId == null || evaluationTime == null || committedTimeSupplier == null) {
            throw new IllegalArgumentException("customer pool automation context is invalid");
        }
        requireLimit(maxCustomers);
        objectLock.acquire(tenantId, BusinessObjectType.CUSTOMER);
        LocalDateTime committedTime = committedTimeSupplier.get();
        if (committedTime == null) {
            throw new IllegalStateException("customer pool automation database time is unavailable");
        }
        return releaseAfterObjectGuard(tenantId, evaluationTime, committedTime, maxCustomers);
    }

    private int release(TenantId tenantId, LocalDateTime evaluationTime,
                        LocalDateTime membershipEnteredTime, int limit) {
        objectLock.acquire(tenantId, BusinessObjectType.CUSTOMER);
        return releaseAfterObjectGuard(tenantId, evaluationTime, membershipEnteredTime, limit);
    }

    private int releaseAfterObjectGuard(TenantId tenantId, LocalDateTime evaluationTime,
                                        LocalDateTime membershipEnteredTime, int limit) {
                        List<FieldDefinition> fields = customerSupport.fields(tenantId);
                        int changed = 0;
                        CustomerPoolId afterPoolId = poolRepository.lockAutomationCursor(tenantId);
                        CustomerPoolId lastScannedPoolId = null;
                        Set<CustomerPoolId> scannedPoolIds = new LinkedHashSet<>();
                        boolean wrapped = false;
                        boolean exhausted = false;
                        while (changed < limit && scannedPoolIds.size() < MAX_POOLS_PER_RUN
                                && !exhausted) {
                            int pageSize = Math.min(POOL_SCAN_PAGE_SIZE,
                                    MAX_POOLS_PER_RUN - scannedPoolIds.size());
                            List<CustomerPool> orderedPools = poolRepository.findAutomaticReleaseAfter(
                                            tenantId, afterPoolId, pageSize).stream()
                                    .sorted(Comparator.comparing(pool -> pool.getId().getValue())).toList();
                            if (orderedPools.isEmpty()) {
                                if (wrapped || afterPoolId == null) break;
                                afterPoolId = null;
                                wrapped = true;
                                continue;
                            }
                            for (CustomerPool listed : orderedPools) {
                                if (!scannedPoolIds.add(listed.getId())) {
                                    exhausted = true;
                                    break;
                                }
                                afterPoolId = listed.getId();
                                lastScannedPoolId = listed.getId();
                                if (!listed.isActive() || !listed.isAutomaticReleaseEnabled()) {
                                    continue;
                                }
                                CustomerPool pool = poolRepository.findByIdForUpdate(tenantId, listed.getId())
                                        .orElse(null);
                                if (pool == null || !pool.isActive() || !pool.isAutomaticReleaseEnabled()) {
                                    continue;
                                }
                                Set<CustomerId> candidates = new LinkedHashSet<>();
                                for (var rule : pool.getRules()) {
                                    int remaining = limit - changed - candidates.size();
                                    if (remaining <= 0) break;
                                    candidates.addAll(poolRepository.findAutomationCandidates(
                                            tenantId, pool, rule,
                                            evaluationTime.minusDays(rule.inactiveDays()), remaining));
                                }
                                for (CustomerId id : candidates) {
                                    if (changed >= limit) break;
                                    Customer customer = customerRepository.findById(tenantId, id).orElse(null);
                                    if (customer == null || !customer.isActive()) continue;
                                    MemberId previousOwner = customer.getOwnerMemberId();
                                    customer.enterPool();
                                    customerRepository.save(tenantId, customer, fields);
                                    poolRepository.addMembership(new CustomerPoolMembership(
                                            tenantId, pool.getId(), customer.getId(), previousOwner,
                                            membershipEnteredTime));
                                    poolRepository.recordRelease(tenantId, pool.getId(), customer.getId(),
                                            previousOwner, membershipEnteredTime);
                                    changed++;
                                }
                                if (changed >= limit || scannedPoolIds.size() >= MAX_POOLS_PER_RUN) {
                                    break;
                                }
                            }
                        }
                        if (lastScannedPoolId != null) {
                            poolRepository.saveAutomationCursor(tenantId, lastScannedPoolId);
                        }
                        return changed;
    }

    private void requireLimit(int limit) {
        if (limit < 1 || limit > 1_000) {
            throw customerSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer pool automation limit is invalid");
        }
    }
}
