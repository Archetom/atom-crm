package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaFormField;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OaFormDomainException;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.OaFormPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaFormFieldMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaFormMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OaFormFieldPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaFormPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OaFormRepositoryImpl implements OaFormRepository {

    private final OaFormMapper formMapper;
    private final OaFormFieldMapper fieldMapper;
    private final OaFormPOConverter converter;

    @Override
    public OaFormDefinition save(TenantId tenantId, OaFormDefinition form) {
        requireTenantMatch(tenantId, form);
        OaFormPO po = converter.toPO(form);
        try {
            if (po.getId() == null) {
                if (formMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "OA form insert did not affect exactly one row");
                }
            } else if (formMapper.update(po, tenantFilter(tenantId)
                    .eq(OaFormPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            OaFormId formId = new OaFormId(po.getId());
            synchronizeFields(tenantId, formId, form.getFields());
            form.onPersisted(formId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return form;
        } catch (DuplicateKeyException exception) {
            throw new OaFormDomainException("OA form conflicts with existing data");
        }
    }

    @Override
    public Optional<OaFormDefinition> findById(TenantId tenantId, OaFormId formId) {
        requireTenant(tenantId);
        if (formId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, formMapper.selectOne(tenantFilter(tenantId)
                .eq(OaFormPO::getId, formId.getValue())));
    }

    @Override
    public Optional<OaFormDefinition> findByIdForUpdate(
            TenantId tenantId, OaFormId formId) {
        requireTenant(tenantId);
        if (formId == null) {
            return Optional.empty();
        }
        return hydrate(tenantId, formMapper.selectOne(tenantFilter(tenantId)
                .eq(OaFormPO::getId, formId.getValue()).last("FOR UPDATE")));
    }

    @Override
    public PageResult<OaFormDefinition> findPage(
            TenantId tenantId, OaFormStatus status, int page, int size) {
        requireTenant(tenantId);
        LambdaQueryWrapper<OaFormPO> filter = tenantFilter(tenantId);
        if (status == null) {
            filter.ne(OaFormPO::getStatus, OaFormStatus.DELETED.name());
        } else {
            filter.eq(OaFormPO::getStatus, status.name());
        }
        IPage<OaFormPO> result = formMapper.selectPage(new Page<>(page, size),
                filter.orderByDesc(OaFormPO::getUpdatedTime)
                        .orderByDesc(OaFormPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0, result.getTotal()), hydrate(tenantId, result.getRecords()));
    }

    @Override
    public List<OaFormDefinition> findAllActive(TenantId tenantId, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("OA form limit is invalid");
        }
        return hydrate(tenantId, formMapper.selectList(tenantFilter(tenantId)
                .eq(OaFormPO::getStatus, OaFormStatus.ACTIVE.name())
                .orderByAsc(OaFormPO::getName, OaFormPO::getId)
                .last("LIMIT " + limit)));
    }

    @Override
    public long countNonDeleted(TenantId tenantId) {
        requireTenant(tenantId);
        return formMapper.selectCount(tenantFilter(tenantId)
                .ne(OaFormPO::getStatus, OaFormStatus.DELETED.name()));
    }

    @Override
    public boolean existsActiveReferencingField(TenantId tenantId, FieldCode fieldCode) {
        requireTenant(tenantId);
        if (fieldCode == null) {
            throw new IllegalArgumentException("OA form field code is required");
        }
        return formMapper.countActiveReferencingField(
                tenantId.getValue(), fieldCode.getValue()) > 0;
    }

    @Override
    public boolean existsActiveReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId definitionId) {
        requireTenant(tenantId);
        if (definitionId == null) {
            throw new IllegalArgumentException("OA form approval definition ID is required");
        }
        return formMapper.countActiveReferencingApprovalDefinition(
                tenantId.getValue(), definitionId.getValue()) > 0;
    }

    @Override
    public List<OaFormDefinition> findActiveReferencingApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId definitionId) {
        requireTenant(tenantId);
        if (definitionId == null) {
            throw new IllegalArgumentException("OA form approval definition ID is required");
        }
        return hydrate(tenantId, formMapper.selectList(tenantFilter(tenantId)
                .eq(OaFormPO::getStatus, OaFormStatus.ACTIVE.name())
                .eq(OaFormPO::getApprovalDefinitionId, definitionId.getValue())
                .orderByAsc(OaFormPO::getId)));
    }

    private Optional<OaFormDefinition> hydrate(TenantId tenantId, OaFormPO root) {
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(converter.toDomain(root, fields(tenantId, List.of(root.getId()))));
    }

    private List<OaFormDefinition> hydrate(
            TenantId tenantId, Collection<OaFormPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OaFormFieldPO>> fieldsByForm = fields(tenantId,
                roots.stream().map(OaFormPO::getId).toList()).stream()
                .collect(Collectors.groupingBy(OaFormFieldPO::getOaFormId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(root -> converter.toDomain(root,
                fieldsByForm.getOrDefault(root.getId(), List.of()))).toList();
    }

    private List<OaFormFieldPO> fields(TenantId tenantId, Collection<Long> formIds) {
        if (formIds == null || formIds.isEmpty()) {
            return List.of();
        }
        return fieldMapper.selectList(new LambdaQueryWrapper<OaFormFieldPO>()
                .eq(OaFormFieldPO::getTenantId, tenantId.getValue())
                .eq(OaFormFieldPO::getObjectType, "OA_REQUEST")
                .in(OaFormFieldPO::getOaFormId, formIds)
                .orderByAsc(OaFormFieldPO::getSortOrder, OaFormFieldPO::getFieldCode));
    }

    private void synchronizeFields(
            TenantId tenantId, OaFormId formId, Collection<OaFormField> fields) {
        fieldMapper.delete(new LambdaQueryWrapper<OaFormFieldPO>()
                .eq(OaFormFieldPO::getTenantId, tenantId.getValue())
                .eq(OaFormFieldPO::getOaFormId, formId.getValue()));
        for (OaFormField field : fields) {
            if (fieldMapper.insert(converter.toPO(tenantId, formId, field)) != 1) {
                throw new IllegalStateException(
                        "OA form field insert did not affect exactly one row");
            }
        }
    }

    private LambdaQueryWrapper<OaFormPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<OaFormPO>()
                .eq(OaFormPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, OaFormDefinition form) {
        requireTenant(tenantId);
        if (form == null || form.getTenantId() == null
                || !tenantId.sameValueAs(form.getTenantId())) {
            throw new IllegalArgumentException("OA form tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID is required");
        }
    }
}
