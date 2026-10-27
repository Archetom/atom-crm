package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.FieldLayoutPreference;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.FieldLayoutPreferenceRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.FieldLayoutPreferencePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.FieldLayoutPreferenceMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.FieldLayoutPreferencePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FieldLayoutPreferenceRepositoryImpl implements FieldLayoutPreferenceRepository {

    private final FieldLayoutPreferenceMapper mapper;
    private final FieldLayoutPreferencePOConverter converter;

    @Override
    public FieldLayoutPreference save(TenantId tenantId, FieldLayoutPreference preference) {
        requireTenantMatch(tenantId, preference);
        FieldLayoutPreferencePO po = converter.toPO(preference);
        try {
            FieldLayoutPreferencePO existing = mapper.selectOne(filter(
                    tenantId, preference.getMemberId(), preference.getObjectType()));
            if (existing == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Field layout insert did not affect exactly one row");
                }
            } else if (mapper.update(po, filter(tenantId, preference.getMemberId(),
                    preference.getObjectType()).eq(FieldLayoutPreferencePO::getVersion,
                    preference.getVersion())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new AggregateVersionConflictException();
        }
        preference.onPersisted(po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return preference;
    }

    @Override
    public Optional<FieldLayoutPreference> findByMember(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType) {
        if (memberId == null) {
            requireObject(tenantId, objectType);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(filter(
                tenantId, memberId, objectType))));
    }

    @Override
    public boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode) {
        requireObject(tenantId, objectType);
        if (fieldCode == null) {
            throw new IllegalArgumentException("field code must not be null");
        }
        return mapper.existsReferencingField(
                tenantId.getValue(), objectType.name(), fieldCode.getValue());
    }

    private LambdaQueryWrapper<FieldLayoutPreferencePO> filter(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType) {
        requireObject(tenantId, objectType);
        if (memberId == null) {
            throw new IllegalArgumentException("member ID must not be null");
        }
        return new LambdaQueryWrapper<FieldLayoutPreferencePO>()
                .eq(FieldLayoutPreferencePO::getTenantId, tenantId.getValue())
                .eq(FieldLayoutPreferencePO::getActorId, memberId.getValue())
                .eq(FieldLayoutPreferencePO::getObjectType, objectType.name());
    }

    private void requireTenantMatch(TenantId tenantId, FieldLayoutPreference preference) {
        requireObject(tenantId, preference == null ? null : preference.getObjectType());
        if (preference.getTenantId() == null || !tenantId.sameValueAs(preference.getTenantId())) {
            throw new IllegalArgumentException("field layout tenant does not match repository tenant");
        }
    }

    private void requireObject(TenantId tenantId, BusinessObjectType objectType) {
        if (tenantId == null || objectType == null) {
            throw new IllegalArgumentException("tenant and object type must not be null");
        }
    }
}
