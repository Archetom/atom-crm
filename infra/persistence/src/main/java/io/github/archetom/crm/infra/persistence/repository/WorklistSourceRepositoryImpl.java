package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorklistSource;
import io.github.archetom.crm.domain.model.WorklistSourceRecord;
import io.github.archetom.crm.domain.repository.WorklistSourceRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.WorklistSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorklistSourceRepositoryImpl implements WorklistSourceRepository {
    private final WorklistSourceMapper mapper;

    @Override
    public List<WorklistSourceRecord> findDue(
            TenantId tenantId, MemberId memberId, WorklistSource source,
            LocalDateTime fromInclusive, LocalDateTime untilExclusive,
            LocalDateTime afterTime, Long afterId, int limit) {
        if (tenantId == null || source == null || fromInclusive == null || untilExclusive == null
                || !fromInclusive.isBefore(untilExclusive) || (afterTime == null) != (afterId == null)
                || afterId != null && afterId <= 0 || limit < 1 || limit > 200
                || source == WorklistSource.APPROVAL && memberId == null) {
            throw new IllegalArgumentException("worklist source query is invalid");
        }
        return mapper.selectDue(tenantId.getValue(), memberId == null ? null : memberId.getValue(),
                        source.name(), fromInclusive, untilExclusive, afterTime, afterId, limit)
                .stream().map(value -> new WorklistSourceRecord(
                        WorklistSource.valueOf(value.getSource()), value.getSourceId(),
                        value.getSourceVersion(), BusinessObjectType.valueOf(value.getSubjectType()),
                        value.getSubjectId(), value.getDueTime(), value.getSummary())).toList();
    }
}
