package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.WorklistItemFingerprint;
import io.github.archetom.crm.domain.repository.WorklistAcknowledgementRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorklistAcknowledgementMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.WorklistAcknowledgementPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class WorklistAcknowledgementRepositoryImpl implements WorklistAcknowledgementRepository {
    private static final int MAX_BATCH = 1_000;
    private final WorklistAcknowledgementMapper mapper;

    @Override
    public Set<WorklistItemFingerprint> findAcknowledged(
            TenantId tenantId, MemberId memberId, Collection<WorklistItemFingerprint> items) {
        require(tenantId, memberId, items);
        if (items.isEmpty()) return Set.of();
        return mapper.selectAcknowledged(tenantId.getValue(), memberId.getValue(), items).stream()
                .map(value -> new WorklistItemFingerprint(value.getKind(), value.getSourceId(),
                        value.getSourceVersion(), value.getDueTime()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void saveAll(TenantId tenantId, MemberId memberId,
                        Collection<WorklistItemFingerprint> items) {
        require(tenantId, memberId, items);
        for (WorklistItemFingerprint item : new LinkedHashSet<>(items)) {
            WorklistAcknowledgementPO po = new WorklistAcknowledgementPO();
            po.setTenantId(tenantId.getValue());
            po.setActorId(memberId.getValue());
            po.setKind(item.kind());
            po.setSourceId(item.sourceId());
            po.setSourceVersion(item.sourceVersion());
            po.setDueTime(item.dueTime());
            try {
                mapper.insert(po);
            } catch (DuplicateKeyException ignored) {
                // Acknowledgement is idempotent for the exact source fingerprint.
            }
        }
    }

    private void require(TenantId tenantId, MemberId memberId,
                         Collection<WorklistItemFingerprint> items) {
        if (tenantId == null || memberId == null || items == null || items.size() > MAX_BATCH
                || items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("worklist acknowledgement batch is invalid");
        }
    }
}
