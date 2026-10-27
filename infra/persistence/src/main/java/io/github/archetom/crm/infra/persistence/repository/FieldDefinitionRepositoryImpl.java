package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.CustomizationAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldStatus;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FieldDefinitionRepositoryImpl implements FieldDefinitionRepository {

    private final CustomFieldDefinitionMapper mapper;
    private final CustomFieldDefinitionPOConverter converter;

    @Override
    public FieldDefinition save(TenantId tenantId, FieldDefinition field) {
        requireTenantMatch(tenantId, field);
        CustomFieldDefinitionPO po = converter.toPO(field);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Custom field insert did not affect exactly one row");
                }
            } else if (mapper.update(po, objectFilter(tenantId, field.getObjectType())
                    .eq(CustomFieldDefinitionPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomizationAlreadyExistsException();
        }
        field.onPersisted(new FieldDefinitionId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return field;
    }

    @Override
    public Optional<FieldDefinition> findById(
            TenantId tenantId, BusinessObjectType objectType, FieldDefinitionId fieldId) {
        if (fieldId == null) {
            requireObject(tenantId, objectType);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(objectFilter(tenantId, objectType)
                .eq(CustomFieldDefinitionPO::getId, fieldId.getValue()))));
    }

    @Override
    public Optional<FieldDefinition> findByCode(
            TenantId tenantId, BusinessObjectType objectType, FieldCode code) {
        if (code == null) {
            requireObject(tenantId, objectType);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(objectFilter(tenantId, objectType)
                .eq(CustomFieldDefinitionPO::getFieldCode, code.getValue()))));
    }

    @Override
    public List<FieldDefinition> findByCodes(
            TenantId tenantId, BusinessObjectType objectType, Collection<FieldCode> codes) {
        requireObject(tenantId, objectType);
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> values = codes.stream().filter(java.util.Objects::nonNull)
                .map(FieldCode::getValue).distinct().toList();
        if (values.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(objectFilter(tenantId, objectType)
                        .in(CustomFieldDefinitionPO::getFieldCode, values)
                        .orderByAsc(CustomFieldDefinitionPO::getDisplayOrder,
                                CustomFieldDefinitionPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public List<FieldDefinition> findAll(TenantId tenantId, BusinessObjectType objectType,
                                         boolean includeInactive) {
        LambdaQueryWrapper<CustomFieldDefinitionPO> query = objectFilter(tenantId, objectType);
        if (!includeInactive) {
            query.eq(CustomFieldDefinitionPO::getStatus, CustomFieldStatus.ACTIVE.name());
        }
        return mapper.selectList(query.orderByAsc(CustomFieldDefinitionPO::getDisplayOrder,
                        CustomFieldDefinitionPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    private LambdaQueryWrapper<CustomFieldDefinitionPO> objectFilter(
            TenantId tenantId, BusinessObjectType objectType) {
        requireObject(tenantId, objectType);
        return new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, objectType.name());
    }

    private void requireTenantMatch(TenantId tenantId, FieldDefinition field) {
        requireObject(tenantId, field == null ? null : field.getObjectType());
        if (field.getTenantId() == null || !tenantId.sameValueAs(field.getTenantId())) {
            throw new IllegalArgumentException("field tenant does not match repository tenant");
        }
    }

    private void requireObject(TenantId tenantId, BusinessObjectType objectType) {
        if (tenantId == null || objectType == null) {
            throw new IllegalArgumentException("tenant and object type must not be null");
        }
    }
}
