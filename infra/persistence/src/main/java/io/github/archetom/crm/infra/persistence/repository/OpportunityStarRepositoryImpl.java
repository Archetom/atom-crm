package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.repository.OpportunityStarRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityStarMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityStarPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OpportunityStarRepositoryImpl implements OpportunityStarRepository {

    private final OpportunityStarMapper mapper;

    @Override
    public void setStarred(
            TenantId tenantId, MemberId memberId,
            OpportunityId opportunityId, boolean starred) {
        require(tenantId, memberId, opportunityId);
        if (!starred) {
            mapper.delete(filter(tenantId, memberId).eq(
                    OpportunityStarPO::getOpportunityId, opportunityId.getValue()));
            return;
        }
        OpportunityStarPO po = new OpportunityStarPO();
        po.setTenantId(tenantId.getValue());
        po.setActorId(memberId.getValue());
        po.setOpportunityId(opportunityId.getValue());
        po.setCreatedTime(LocalDateTime.now());
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException ignored) {
            // Idempotent personal preference update.
        }
    }

    @Override
    public boolean isStarred(
            TenantId tenantId, MemberId memberId,
            OpportunityId opportunityId) {
        require(tenantId, memberId, opportunityId);
        return mapper.selectCount(filter(tenantId, memberId).eq(
                OpportunityStarPO::getOpportunityId, opportunityId.getValue())) > 0;
    }

    @Override
    public Set<OpportunityId> findStarred(
            TenantId tenantId, MemberId memberId,
            Collection<OpportunityId> opportunityIds) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException(
                    "tenant and member must not be null");
        }
        List<Long> ids = opportunityIds == null ? List.of()
                : opportunityIds.stream().filter(java.util.Objects::nonNull)
                        .map(OpportunityId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(filter(tenantId, memberId)
                        .in(OpportunityStarPO::getOpportunityId, ids))
                .stream().map(value -> new OpportunityId(value.getOpportunityId()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private LambdaQueryWrapper<OpportunityStarPO> filter(
            TenantId tenantId, MemberId memberId) {
        return new LambdaQueryWrapper<OpportunityStarPO>()
                .eq(OpportunityStarPO::getTenantId, tenantId.getValue())
                .eq(OpportunityStarPO::getActorId, memberId.getValue());
    }

    private void require(
            TenantId tenantId, MemberId memberId,
            OpportunityId opportunityId) {
        if (tenantId == null || memberId == null || opportunityId == null) {
            throw new IllegalArgumentException(
                    "tenant, member, and opportunity must not be null");
        }
    }
}
