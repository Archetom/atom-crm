package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ContactPoolAutomationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolMembership;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContactPoolRepository;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
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
public class ContactPoolAutomationServiceImpl implements ContactPoolAutomationService {

    private static final int POOL_SCAN_PAGE_SIZE = 25;
    private static final int MAX_POOLS_PER_RUN = 50;

    private final ContactPoolRepository poolRepository;
    private final ContactRepository contactRepository;
    private final ContactApplicationSupport contactSupport;
    private final OrganizationAccessGuard accessGuard;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<Integer> run(
            AuthenticatedCaller caller, LocalDateTime asOf, Integer maxContacts) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_AUTOMATION,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private int limit;
                    private LocalDateTime evaluationTime;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.CONTACT_POOLS_MANAGE);
                        evaluationTime = asOf == null ? LocalDateTime.now() : asOf;
                        limit = maxContacts == null ? 200 : maxContacts;
                        if (limit < 1 || limit > 1_000) {
                            throw contactSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Contact pool automation limit is invalid");
                        }
                    }

                    @Override
                    public Integer execute() {
                        return release(tenantId, evaluationTime, evaluationTime, limit);
                    }
                });
    }

    @Override
    public int runSystem(TenantId tenantId, LocalDateTime evaluationTime, int maxContacts,
                         Supplier<LocalDateTime> committedTimeSupplier) {
        if (tenantId == null || evaluationTime == null || committedTimeSupplier == null) {
            throw new IllegalArgumentException("contact pool automation context is invalid");
        }
        requireLimit(maxContacts);
        objectLock.acquire(tenantId, BusinessObjectType.CONTACT);
        LocalDateTime committedTime = committedTimeSupplier.get();
        if (committedTime == null) {
            throw new IllegalStateException("contact pool automation database time is unavailable");
        }
        return releaseAfterObjectGuard(tenantId, evaluationTime, committedTime, maxContacts);
    }

    private int release(TenantId tenantId, LocalDateTime evaluationTime,
                        LocalDateTime membershipEnteredTime, int limit) {
        objectLock.acquire(tenantId, BusinessObjectType.CONTACT);
        return releaseAfterObjectGuard(tenantId, evaluationTime, membershipEnteredTime, limit);
    }

    private int releaseAfterObjectGuard(TenantId tenantId, LocalDateTime evaluationTime,
                                        LocalDateTime membershipEnteredTime, int limit) {
                        List<FieldDefinition> fields = contactSupport.fields(tenantId);
                        int changed = 0;
                        ContactPoolId afterPoolId = poolRepository.lockAutomationCursor(tenantId);
                        ContactPoolId lastScannedPoolId = null;
                        Set<ContactPoolId> scannedPoolIds = new LinkedHashSet<>();
                        boolean wrapped = false;
                        boolean exhausted = false;
                        while (changed < limit && scannedPoolIds.size() < MAX_POOLS_PER_RUN
                                && !exhausted) {
                            int pageSize = Math.min(POOL_SCAN_PAGE_SIZE,
                                    MAX_POOLS_PER_RUN - scannedPoolIds.size());
                            List<ContactPool> orderedPools = poolRepository.findAutomaticReleaseAfter(
                                            tenantId, afterPoolId, pageSize).stream()
                                    .sorted(Comparator.comparing(pool -> pool.getId().getValue())).toList();
                            if (orderedPools.isEmpty()) {
                                if (wrapped || afterPoolId == null) break;
                                afterPoolId = null;
                                wrapped = true;
                                continue;
                            }
                            for (ContactPool listed : orderedPools) {
                                if (!scannedPoolIds.add(listed.getId())) {
                                    exhausted = true;
                                    break;
                                }
                                afterPoolId = listed.getId();
                                lastScannedPoolId = listed.getId();
                                if (!listed.isActive() || !listed.isAutomaticReleaseEnabled()) {
                                    continue;
                                }
                                ContactPool pool = poolRepository.findByIdForUpdate(tenantId, listed.getId())
                                        .orElse(null);
                                if (pool == null || !pool.isActive() || !pool.isAutomaticReleaseEnabled()) {
                                    continue;
                                }
                                Set<ContactId> candidates = new LinkedHashSet<>();
                                for (var rule : pool.getRules()) {
                                    int remaining = limit - changed - candidates.size();
                                    if (remaining <= 0) break;
                                    candidates.addAll(poolRepository.findAutomationCandidates(
                                            tenantId, pool, rule,
                                            evaluationTime.minusDays(rule.inactiveDays()), remaining));
                                }
                                for (ContactId id : candidates) {
                                    if (changed >= limit) break;
                                    Contact contact = contactRepository.findById(tenantId, id).orElse(null);
                                    if (contact == null || !contact.isActive()) continue;
                                    MemberId previousOwner = contact.getOwnerMemberId();
                                    contact.enterPool();
                                    contactRepository.save(tenantId, contact, fields);
                                    poolRepository.addMembership(new ContactPoolMembership(
                                            tenantId, pool.getId(), contact.getId(), previousOwner,
                                            membershipEnteredTime));
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
            throw contactSupport.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact pool automation limit is invalid");
        }
    }
}
