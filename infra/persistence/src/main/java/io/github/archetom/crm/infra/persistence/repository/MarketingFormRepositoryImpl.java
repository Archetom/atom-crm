package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.MarketingFormDomainException;
import io.github.archetom.crm.domain.model.MarketingFormStatus;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.MarketingFormPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingFormFieldMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingFormMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormFieldPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormPO;
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
public class MarketingFormRepositoryImpl implements MarketingFormRepository {

    private final MarketingFormMapper formMapper;
    private final MarketingFormFieldMapper fieldMapper;
    private final MarketingFormPOConverter converter;

    @Override
    public MarketingForm save(TenantId tenantId, MarketingForm form) {
        requireTenantMatch(tenantId, form);
        MarketingFormPO po = converter.toPO(form);
        try {
            if (po.getId() == null) {
                if (formMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "Marketing form insert did not affect exactly one row");
                }
            } else if (formMapper.update(po, tenantFilter(tenantId)
                    .eq(MarketingFormPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            MarketingFormId formId = new MarketingFormId(po.getId());
            synchronizeFields(tenantId, formId, form.getFields());
            form.onPersisted(formId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return form;
        } catch (DuplicateKeyException exception) {
            throw new MarketingFormDomainException("marketing form conflicts with existing data");
        }
    }

    @Override
    public Optional<MarketingForm> findById(
            TenantId tenantId, MarketingFormId formId) {
        requireTenant(tenantId);
        if (formId == null) {
            return Optional.empty();
        }
        MarketingFormPO root = formMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingFormPO::getId, formId.getValue()));
        return hydrate(tenantId, root);
    }

    @Override
    public Optional<MarketingForm> findByIdForUpdate(
            TenantId tenantId, MarketingFormId formId) {
        requireTenant(tenantId);
        if (formId == null) {
            return Optional.empty();
        }
        MarketingFormPO root = formMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingFormPO::getId, formId.getValue())
                .last("FOR UPDATE"));
        return hydrate(tenantId, root);
    }

    @Override
    public Optional<MarketingForm> findActiveByPublicKey(
            TenantId tenantId, String publicKey) {
        requireTenant(tenantId);
        if (publicKey == null || !publicKey.matches("[A-Za-z0-9_-]{16,64}")) {
            return Optional.empty();
        }
        MarketingFormPO root = formMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingFormPO::getPublicKey, publicKey)
                .eq(MarketingFormPO::getStatus, MarketingFormStatus.ACTIVE.name()));
        return hydrate(tenantId, root);
    }

    @Override
    public Optional<MarketingForm> findByPublicKeyForUpdate(
            TenantId tenantId, String publicKey) {
        requireTenant(tenantId);
        if (publicKey == null || !publicKey.matches("[A-Za-z0-9_-]{16,64}")) {
            return Optional.empty();
        }
        MarketingFormPO root = formMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingFormPO::getPublicKey, publicKey).last("FOR UPDATE"));
        return hydrate(tenantId, root);
    }

    @Override
    public Optional<MarketingForm> findActiveByPublicKeyForUpdate(
            TenantId tenantId, String publicKey) {
        requireTenant(tenantId);
        if (publicKey == null || !publicKey.matches("[A-Za-z0-9_-]{16,64}")) {
            return Optional.empty();
        }
        MarketingFormPO root = formMapper.selectOne(tenantFilter(tenantId)
                .eq(MarketingFormPO::getPublicKey, publicKey)
                .eq(MarketingFormPO::getStatus, MarketingFormStatus.ACTIVE.name())
                .last("FOR UPDATE"));
        return hydrate(tenantId, root);
    }

    @Override
    public PageResult<MarketingForm> findPage(
            TenantId tenantId, MarketingFormStatus status, int page, int size) {
        requireTenant(tenantId);
        LambdaQueryWrapper<MarketingFormPO> filter = tenantFilter(tenantId);
        if (status == null) {
            filter.ne(MarketingFormPO::getStatus, MarketingFormStatus.DELETED.name());
        } else {
            filter.eq(MarketingFormPO::getStatus, status.name());
        }
        IPage<MarketingFormPO> result = formMapper.selectPage(new Page<>(page, size),
                filter.orderByDesc(MarketingFormPO::getUpdatedTime)
                        .orderByDesc(MarketingFormPO::getId));
        List<MarketingForm> forms = hydrate(tenantId, result.getRecords());
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0, result.getTotal()), forms);
    }

    @Override
    public boolean existsActiveReferencingField(
            TenantId tenantId, FieldCode fieldCode) {
        requireTenant(tenantId);
        if (fieldCode == null) {
            throw new IllegalArgumentException("marketing form field code is required");
        }
        return formMapper.countActiveReferencingField(
                tenantId.getValue(), fieldCode.getValue()) > 0;
    }

    private Optional<MarketingForm> hydrate(TenantId tenantId, MarketingFormPO root) {
        if (root == null) {
            return Optional.empty();
        }
        List<MarketingFormFieldPO> fields = fields(tenantId, List.of(root.getId()));
        return Optional.of(converter.toDomain(root, fields));
    }

    private List<MarketingForm> hydrate(
            TenantId tenantId, Collection<MarketingFormPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MarketingFormFieldPO>> fieldsByForm = fields(tenantId,
                roots.stream().map(MarketingFormPO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MarketingFormFieldPO::getMarketingFormId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(root -> converter.toDomain(root,
                fieldsByForm.getOrDefault(root.getId(), List.of()))).toList();
    }

    private List<MarketingFormFieldPO> fields(
            TenantId tenantId, Collection<Long> formIds) {
        if (formIds == null || formIds.isEmpty()) {
            return List.of();
        }
        return fieldMapper.selectList(new LambdaQueryWrapper<MarketingFormFieldPO>()
                .eq(MarketingFormFieldPO::getTenantId, tenantId.getValue())
                .in(MarketingFormFieldPO::getMarketingFormId, formIds)
                .orderByAsc(MarketingFormFieldPO::getSortOrder,
                        MarketingFormFieldPO::getFieldCode));
    }

    private void synchronizeFields(
            TenantId tenantId, MarketingFormId formId,
            Collection<io.github.archetom.crm.domain.entity.MarketingFormField> fields) {
        fieldMapper.delete(new LambdaQueryWrapper<MarketingFormFieldPO>()
                .eq(MarketingFormFieldPO::getTenantId, tenantId.getValue())
                .eq(MarketingFormFieldPO::getMarketingFormId, formId.getValue()));
        for (var field : fields) {
            if (fieldMapper.insert(converter.toPO(tenantId, formId, field)) != 1) {
                throw new IllegalStateException(
                        "Marketing form field insert did not affect exactly one row");
            }
        }
    }

    private LambdaQueryWrapper<MarketingFormPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<MarketingFormPO>()
                .eq(MarketingFormPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, MarketingForm form) {
        requireTenant(tenantId);
        if (form == null || form.getTenantId() == null
                || !tenantId.sameValueAs(form.getTenantId())) {
            throw new IllegalArgumentException(
                    "marketing form tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
