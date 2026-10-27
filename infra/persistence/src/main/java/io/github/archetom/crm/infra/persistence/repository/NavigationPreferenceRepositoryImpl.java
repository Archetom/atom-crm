package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.repository.NavigationPreferenceRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.NavigationPreferencePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.NavigationPreferenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NavigationPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Relational preference repository keyed by explicit tenant and member. */
@Repository
@RequiredArgsConstructor
public class NavigationPreferenceRepositoryImpl implements NavigationPreferenceRepository {

    private final NavigationPreferenceMapper preferenceMapper;
    private final NavigationPreferencePOConverter preferenceConverter;

    @Override
    public NavigationPreference save(TenantId tenantId, NavigationPreference preference) {
        requireTenantMatch(tenantId, preference);
        NavigationPreferencePO po = preferenceConverter.toPO(preference);
        try {
            NavigationPreferencePO existing = preferenceMapper.selectOne(memberFilter(
                    tenantId, preference.getMemberId()));
            if (existing == null) {
                if (preferenceMapper.insert(po) != 1) {
                    throw new IllegalStateException("Navigation preference insert did not affect exactly one row");
                }
            } else if (preferenceMapper.update(po, memberFilter(tenantId, preference.getMemberId())
                    .eq(NavigationPreferencePO::getVersion, preference.getVersion())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new AggregateVersionConflictException();
        }
        preference.onPersisted(po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return preference;
    }

    @Override
    public Optional<NavigationPreference> findByMember(TenantId tenantId, MemberId memberId) {
        if (memberId == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(preferenceConverter.toDomain(
                preferenceMapper.selectOne(memberFilter(tenantId, memberId))));
    }

    private LambdaQueryWrapper<NavigationPreferencePO> memberFilter(TenantId tenantId, MemberId memberId) {
        requireTenant(tenantId);
        if (memberId == null) {
            throw new IllegalArgumentException("member ID must not be null");
        }
        return new LambdaQueryWrapper<NavigationPreferencePO>()
                .eq(NavigationPreferencePO::getTenantId, tenantId.getValue())
                .eq(NavigationPreferencePO::getActorId, memberId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, NavigationPreference preference) {
        requireTenant(tenantId);
        if (preference == null || preference.getTenantId() == null
                || !tenantId.sameValueAs(preference.getTenantId())) {
            throw new IllegalArgumentException("preference tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
