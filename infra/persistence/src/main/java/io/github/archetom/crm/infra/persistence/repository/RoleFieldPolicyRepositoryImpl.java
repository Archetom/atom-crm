package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.RoleFieldPolicyRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.RoleFieldPolicyPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.RoleFieldAccessMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.RoleFieldPolicyMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleFieldAccessPO;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleFieldPolicyPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleFieldPolicyRepositoryImpl implements RoleFieldPolicyRepository {

    private final RoleFieldPolicyMapper policyMapper;
    private final RoleFieldAccessMapper accessMapper;
    private final CustomFieldDefinitionMapper fieldMapper;
    private final RoleFieldPolicyPOConverter converter;

    @Override
    public RoleFieldPolicy save(TenantId tenantId, RoleFieldPolicy policy) {
        requireTenantMatch(tenantId, policy);
        RoleFieldPolicyPO po = converter.toPO(policy);
        try {
            RoleFieldPolicyPO existing = policyMapper.selectOne(filter(
                    tenantId, policy.getRoleId(), policy.getObjectType()));
            if (existing == null) {
                if (policyMapper.insert(po) != 1) {
                    throw new IllegalStateException("Role field policy insert did not affect exactly one row");
                }
            } else if (policyMapper.update(po, filter(tenantId, policy.getRoleId(),
                    policy.getObjectType()).eq(RoleFieldPolicyPO::getVersion,
                    policy.getVersion())) != 1) {
                throw new AggregateVersionConflictException();
            }
            replaceAccesses(policy);
        } catch (DuplicateKeyException exception) {
            throw new AggregateVersionConflictException();
        }
        policy.onPersisted(po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return policy;
    }

    @Override
    public Optional<RoleFieldPolicy> find(
            TenantId tenantId, RoleId roleId, BusinessObjectType objectType) {
        if (roleId == null) {
            requireObject(tenantId, objectType);
            return Optional.empty();
        }
        RoleFieldPolicyPO po = policyMapper.selectOne(filter(tenantId, roleId, objectType));
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public List<RoleFieldPolicy> findByRoles(
            TenantId tenantId, Collection<RoleId> roleIds, BusinessObjectType objectType) {
        return findByRoles(tenantId, roleIds, objectType, null);
    }

    @Override
    public List<RoleFieldPolicy> findByRolesAndFields(
            TenantId tenantId, Collection<RoleId> roleIds, BusinessObjectType objectType,
            Collection<FieldCode> fieldCodes) {
        if (fieldCodes == null) {
            requireObject(tenantId, objectType);
            throw new IllegalArgumentException("field codes must not be null");
        }
        return findByRoles(tenantId, roleIds, objectType, fieldCodes);
    }

    private List<RoleFieldPolicy> findByRoles(
            TenantId tenantId, Collection<RoleId> roleIds, BusinessObjectType objectType,
            Collection<FieldCode> requestedFieldCodes) {
        requireObject(tenantId, objectType);
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roleIds.stream().filter(java.util.Objects::nonNull)
                .map(RoleId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<RoleFieldPolicyPO> policies = policyMapper.selectList(objectFilter(tenantId, objectType)
                .in(RoleFieldPolicyPO::getRoleId, ids).orderByAsc(RoleFieldPolicyPO::getRoleId));
        if (policies.isEmpty()) {
            return List.of();
        }
        Map<Long, FieldCode> requestedFields = requestedFieldCodes == null
                ? null : requestedFields(tenantId, objectType, requestedFieldCodes);
        List<RoleFieldAccessPO> accesses = requestedFields != null && requestedFields.isEmpty()
                ? List.of() : accessMapper.selectList(accessFilter(tenantId, objectType)
                        .in(RoleFieldAccessPO::getRoleId, ids)
                        .in(requestedFields != null, RoleFieldAccessPO::getFieldId,
                                requestedFields == null ? List.of() : requestedFields.keySet())
                        .orderByAsc(RoleFieldAccessPO::getRoleId,
                                RoleFieldAccessPO::getFieldId));
        Map<Long, List<RoleFieldAccessPO>> byRole = new LinkedHashMap<>();
        accesses.forEach(access -> byRole.computeIfAbsent(access.getRoleId(), ignored ->
                new java.util.ArrayList<>()).add(access));
        Map<Long, FieldCode> fieldCodes = requestedFields == null
                ? fieldCodes(tenantId, objectType, accesses) : requestedFields;
        return policies.stream().map(policy -> converter.toDomain(policy,
                byRole.getOrDefault(policy.getRoleId(), List.of()), fieldCodes)).toList();
    }

    private Map<Long, FieldCode> requestedFields(
            TenantId tenantId, BusinessObjectType objectType,
            Collection<FieldCode> fieldCodes) {
        List<String> codes = fieldCodes.stream().filter(java.util.Objects::nonNull)
                .map(FieldCode::getValue).distinct().toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<Long, FieldCode> result = new LinkedHashMap<>();
        fieldMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                        .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                        .eq(CustomFieldDefinitionPO::getObjectType, objectType.name())
                        .in(CustomFieldDefinitionPO::getFieldCode, codes))
                .forEach(field -> result.put(
                        field.getId(), new FieldCode(field.getFieldCode())));
        return Map.copyOf(result);
    }

    private RoleFieldPolicy toDomain(RoleFieldPolicyPO po) {
        if (po == null) {
            return null;
        }
        TenantId tenantId = new TenantId(po.getTenantId());
        BusinessObjectType objectType = BusinessObjectType.valueOf(po.getObjectType());
        List<RoleFieldAccessPO> accesses = accessMapper.selectList(accessFilter(tenantId, objectType)
                .eq(RoleFieldAccessPO::getRoleId, po.getRoleId())
                .orderByAsc(RoleFieldAccessPO::getFieldId));
        return converter.toDomain(po, accesses, fieldCodes(tenantId, objectType, accesses));
    }

    private Map<Long, FieldCode> fieldCodes(TenantId tenantId, BusinessObjectType objectType,
                                             Collection<RoleFieldAccessPO> accesses) {
        List<Long> fieldIds = accesses.stream().map(RoleFieldAccessPO::getFieldId).distinct().toList();
        if (fieldIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, FieldCode> result = new LinkedHashMap<>();
        fieldMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                        .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                        .eq(CustomFieldDefinitionPO::getObjectType, objectType.name())
                        .in(CustomFieldDefinitionPO::getId, fieldIds))
                .forEach(field -> result.put(field.getId(), new FieldCode(field.getFieldCode())));
        return Map.copyOf(result);
    }

    private void replaceAccesses(RoleFieldPolicy policy) {
        accessMapper.delete(accessFilter(policy.getTenantId(), policy.getObjectType())
                .eq(RoleFieldAccessPO::getRoleId, policy.getRoleId().getValue()));
        policy.getAccesses().forEach(access -> {
            if (accessMapper.insert(converter.toAccessPO(policy, access)) != 1) {
                throw new IllegalStateException("Role field access insert did not affect exactly one row");
            }
        });
    }

    private LambdaQueryWrapper<RoleFieldPolicyPO> filter(
            TenantId tenantId, RoleId roleId, BusinessObjectType objectType) {
        requireObject(tenantId, objectType);
        if (roleId == null) {
            throw new IllegalArgumentException("role ID must not be null");
        }
        return objectFilter(tenantId, objectType)
                .eq(RoleFieldPolicyPO::getRoleId, roleId.getValue());
    }

    private LambdaQueryWrapper<RoleFieldPolicyPO> objectFilter(
            TenantId tenantId, BusinessObjectType objectType) {
        requireObject(tenantId, objectType);
        return new LambdaQueryWrapper<RoleFieldPolicyPO>()
                .eq(RoleFieldPolicyPO::getTenantId, tenantId.getValue())
                .eq(RoleFieldPolicyPO::getObjectType, objectType.name());
    }

    private LambdaQueryWrapper<RoleFieldAccessPO> accessFilter(
            TenantId tenantId, BusinessObjectType objectType) {
        requireObject(tenantId, objectType);
        return new LambdaQueryWrapper<RoleFieldAccessPO>()
                .eq(RoleFieldAccessPO::getTenantId, tenantId.getValue())
                .eq(RoleFieldAccessPO::getObjectType, objectType.name());
    }

    private void requireTenantMatch(TenantId tenantId, RoleFieldPolicy policy) {
        requireObject(tenantId, policy == null ? null : policy.getObjectType());
        if (policy.getTenantId() == null || !tenantId.sameValueAs(policy.getTenantId())) {
            throw new IllegalArgumentException("role field policy tenant does not match repository tenant");
        }
    }

    private void requireObject(TenantId tenantId, BusinessObjectType objectType) {
        if (tenantId == null || objectType == null) {
            throw new IllegalArgumentException("tenant and object type must not be null");
        }
    }
}
