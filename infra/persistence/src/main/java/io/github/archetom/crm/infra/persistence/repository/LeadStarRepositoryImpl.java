package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.repository.LeadStarRepository;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadStarMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadStarPO;
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
public class LeadStarRepositoryImpl implements LeadStarRepository {

    private final LeadStarMapper mapper;

    @Override
    public void setStarred(
            TenantId tenantId, MemberId memberId, LeadId leadId, boolean starred) {
        require(tenantId, memberId, leadId);
        if (!starred) {
            mapper.delete(filter(tenantId, memberId).eq(
                    LeadStarPO::getLeadId, leadId.getValue()));
            return;
        }
        LeadStarPO po = new LeadStarPO();
        po.setTenantId(tenantId.getValue());
        po.setActorId(memberId.getValue());
        po.setLeadId(leadId.getValue());
        po.setCreatedTime(LocalDateTime.now());
        try {
            mapper.insert(po);
        } catch (DuplicateKeyException ignored) {
            // Idempotent personal preference update.
        }
    }

    @Override
    public boolean isStarred(TenantId tenantId, MemberId memberId, LeadId leadId) {
        require(tenantId, memberId, leadId);
        return mapper.selectCount(filter(tenantId, memberId)
                .eq(LeadStarPO::getLeadId, leadId.getValue())) > 0;
    }

    @Override
    public Set<LeadId> findStarred(
            TenantId tenantId, MemberId memberId, Collection<LeadId> leadIds) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("tenant and member must not be null");
        }
        List<Long> ids = leadIds == null ? List.of() : leadIds.stream()
                .filter(java.util.Objects::nonNull).map(LeadId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return mapper.selectList(filter(tenantId, memberId)
                        .in(LeadStarPO::getLeadId, ids))
                .stream().map(value -> new LeadId(value.getLeadId()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private LambdaQueryWrapper<LeadStarPO> filter(TenantId tenantId, MemberId memberId) {
        return new LambdaQueryWrapper<LeadStarPO>()
                .eq(LeadStarPO::getTenantId, tenantId.getValue())
                .eq(LeadStarPO::getActorId, memberId.getValue());
    }

    private void require(TenantId tenantId, MemberId memberId, LeadId leadId) {
        if (tenantId == null || memberId == null || leadId == null) {
            throw new IllegalArgumentException("tenant, member, and lead must not be null");
        }
    }
}
