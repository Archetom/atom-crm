package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionRetentionWorkStore;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.EnterpriseDataAdoptionRetentionWorkMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataAdoptionRetentionWorkPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** System-owned durable queue; a claimed row carries the exact tenant before any tenant data work. */
@Repository
@RequiredArgsConstructor
public class EnterpriseDataAdoptionRetentionWorkRepositoryImpl implements EnterpriseDataAdoptionRetentionWorkStore {
    private final EnterpriseDataAdoptionRetentionWorkMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(TenantId tenantId, long adoptionId, Instant deleteAfter) {
        if (tenantId == null || adoptionId < 1 || deleteAfter == null) {
            throw new IllegalArgumentException("enterprise data adoption retention work is invalid");
        }
        EnterpriseDataAdoptionRetentionWorkPO value = new EnterpriseDataAdoptionRetentionWorkPO();
        value.setTenantId(tenantId.getValue());
        value.setAdoptionId(adoptionId);
        value.setDeleteAfter(utc(deleteAfter));
        value.setWorkState("READY");
        if (mapper.insert(value) != 1 || value.getId() == null) {
            throw new IllegalStateException("adoption retention work was not scheduled");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Lease> claimDue(Instant now, Duration leaseDuration, UUID owner) {
        if (now == null || leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                || owner == null) {
            throw new IllegalArgumentException("enterprise data adoption retention claim is invalid");
        }
        EnterpriseDataAdoptionRetentionWorkPO candidate = mapper.selectDueForUpdate(utc(now));
        if (candidate == null) return Optional.empty();
        if (candidate.getId() == null || candidate.getTenantId() == null || candidate.getAdoptionId() == null
                || candidate.getLeaseFence() == null) {
            throw new IllegalStateException("adoption retention work is malformed");
        }
        if (mapper.claim(candidate.getId(), owner.toString(), utc(now.plus(leaseDuration))) != 1) {
            throw new IllegalStateException("adoption retention work claim was lost");
        }
        return Optional.of(new Lease(candidate.getId(), new TenantId(candidate.getTenantId()),
                candidate.getAdoptionId(), candidate.getLeaseFence() + 1, owner));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Lease lease) {
        if (lease == null) throw new IllegalArgumentException("adoption retention lease is required");
        mapper.release(lease.workId(), lease.owner().toString(), lease.fence());
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
