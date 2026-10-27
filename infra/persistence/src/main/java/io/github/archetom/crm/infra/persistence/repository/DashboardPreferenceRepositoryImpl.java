package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.DashboardPreference;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.repository.DashboardPreferenceRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.DashboardPreferencePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.DashboardPreferenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.DashboardPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Relational dashboard preference repository keyed by explicit tenant and member. */
@Repository
@RequiredArgsConstructor
public class DashboardPreferenceRepositoryImpl implements DashboardPreferenceRepository {

    private final DashboardPreferenceMapper mapper;
    private final DashboardPreferencePOConverter converter;

    @Override
    public DashboardPreference save(TenantId tenantId, DashboardPreference preference) {
        requireTenantMatch(tenantId, preference);
        DashboardPreferencePO po = converter.toPO(preference);
        try {
            DashboardPreferencePO existing = mapper.selectOne(memberFilter(
                    tenantId, preference.getMemberId()));
            if (existing == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "Dashboard preference insert did not affect exactly one row");
                }
            } else if (mapper.update(po, memberFilter(tenantId, preference.getMemberId())
                    .eq(DashboardPreferencePO::getVersion, preference.getVersion())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new AggregateVersionConflictException();
        }
        preference.onPersisted(po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return preference;
    }

    @Override
    public Optional<DashboardPreference> findByMember(TenantId tenantId, MemberId memberId) {
        if (memberId == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(
                mapper.selectOne(memberFilter(tenantId, memberId))));
    }

    private LambdaQueryWrapper<DashboardPreferencePO> memberFilter(
            TenantId tenantId, MemberId memberId) {
        requireTenant(tenantId);
        if (memberId == null) {
            throw new IllegalArgumentException("member ID must not be null");
        }
        return new LambdaQueryWrapper<DashboardPreferencePO>()
                .eq(DashboardPreferencePO::getTenantId, tenantId.getValue())
                .eq(DashboardPreferencePO::getActorId, memberId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, DashboardPreference preference) {
        requireTenant(tenantId);
        if (preference == null || preference.getTenantId() == null
                || !tenantId.sameValueAs(preference.getTenantId())) {
            throw new IllegalArgumentException(
                    "dashboard preference tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
