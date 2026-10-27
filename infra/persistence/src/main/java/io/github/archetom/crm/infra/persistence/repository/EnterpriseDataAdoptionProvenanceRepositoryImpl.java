package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionProvenanceStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionRetentionWorkStore;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.EnterpriseDataAdoptionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.EnterpriseDataAdoptionSelectionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataAdoptionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.EnterpriseDataAdoptionSelectionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/** Stores only value-free adoption metadata and physically removes it after its fixed deadline. */
@Repository
@RequiredArgsConstructor
public class EnterpriseDataAdoptionProvenanceRepositoryImpl implements EnterpriseDataAdoptionProvenanceStore {
    private final EnterpriseDataAdoptionMapper adoptionMapper;
    private final EnterpriseDataAdoptionSelectionMapper selectionMapper;
    private final EnterpriseDataAdoptionRetentionWorkStore retentionWorkStore;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Adoption adoption) {
        if (adoption == null) throw new IllegalArgumentException("adoption evidence is required");
        EnterpriseDataAdoptionPO header = new EnterpriseDataAdoptionPO();
        header.setOperationId(adoption.operationId().toString());
        header.setTenantId(adoption.tenantId().getValue());
        header.setCustomerId(adoption.customerId().getValue());
        header.setActorId(adoption.actorId().getValue());
        header.setPrimaryIdentityAssociationId(adoption.primaryIdentityAssociationId());
        header.setSourceKind(adoption.sourceKind().name());
        header.setSourceBindingRef(adoption.sourceBindingRef());
        header.setAdapterEvidenceRef(adoption.adapterEvidenceRef());
        header.setPolicyVersion(adoption.policyVersion());
        header.setAttributionVersion(adoption.attributionVersion());
        header.setMappingVersion(adoption.mappingVersion());
        header.setObservedAt(utc(adoption.observedAt()));
        header.setExpiresAt(utc(adoption.expiresAt()));
        header.setExpectedCustomerVersion(adoption.expectedCustomerVersion());
        header.setCommittedCustomerVersion(adoption.committedCustomerVersion());
        header.setDeleteAfter(utc(adoption.deleteAfter()));
        if (adoptionMapper.insert(header) != 1 || header.getId() == null) {
            throw new IllegalStateException("adoption evidence insert did not produce a header");
        }
        List<Selection> selections = adoption.selections().stream()
                .sorted(Comparator.comparing((Selection value) -> value.fact().name())
                        .thenComparing(Selection::targetLocator)).toList();
        for (int index = 0; index < selections.size(); index++) {
            Selection value = selections.get(index);
            EnterpriseDataAdoptionSelectionPO line = new EnterpriseDataAdoptionSelectionPO();
            line.setAdoptionId(header.getId());
            line.setLineNo(index + 1);
            line.setFactCode(value.fact().name());
            line.setTargetLocator(value.targetLocator());
            line.setChangedValue(value.changed());
            if (selectionMapper.insert(line) != 1) {
                throw new IllegalStateException("adoption evidence insert did not produce a selection");
            }
        }
        retentionWorkStore.schedule(adoption.tenantId(), header.getId(), adoption.deleteAfter());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpired(TenantId tenantId, Instant now, int limit) {
        if (tenantId == null || now == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("adoption retention request is invalid");
        }
        List<Long> ids = adoptionMapper.selectExpiredIdsForUpdate(tenantId.getValue(), utc(now), limit);
        if (ids == null || ids.isEmpty()) return 0;
        selectionMapper.deleteByAdoptionIds(ids);
        return adoptionMapper.deleteByTenantAndIds(tenantId.getValue(), ids);
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
