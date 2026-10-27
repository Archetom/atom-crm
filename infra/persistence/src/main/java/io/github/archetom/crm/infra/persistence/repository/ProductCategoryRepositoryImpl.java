package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ProductCategoryAlreadyExistsException;
import io.github.archetom.crm.domain.model.ProductCategoryStatus;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ProductCategoryPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductCategoryMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductCategoryPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductCategoryRepositoryImpl implements ProductCategoryRepository {

    private final ProductCategoryMapper mapper;
    private final ProductCategoryPOConverter converter;

    @Override
    public ProductCategory save(TenantId tenantId, ProductCategory category) {
        requireMatch(tenantId, category);
        ProductCategoryPO po = converter.toPO(category);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "Product category insert did not affect exactly one row");
                }
            } else {
                LambdaUpdateWrapper<ProductCategoryPO> update =
                        new LambdaUpdateWrapper<ProductCategoryPO>()
                                .eq(ProductCategoryPO::getTenantId, tenantId.getValue())
                                .eq(ProductCategoryPO::getId, po.getId());
                if (mapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }
            category.onPersisted(new ProductCategoryId(po.getId()), po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return category;
        } catch (DuplicateKeyException exception) {
            throw new ProductCategoryAlreadyExistsException();
        }
    }

    @Override
    public Optional<ProductCategory> findById(
            TenantId tenantId, ProductCategoryId categoryId) {
        requireTenant(tenantId);
        if (categoryId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(root(tenantId)
                        .eq(ProductCategoryPO::getId, categoryId.getValue())))
                .map(converter::toDomain);
    }

    @Override
    public Optional<ProductCategory> findActiveById(
            TenantId tenantId, ProductCategoryId categoryId) {
        requireTenant(tenantId);
        if (categoryId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(root(tenantId)
                        .eq(ProductCategoryPO::getId, categoryId.getValue())
                        .eq(ProductCategoryPO::getStatus,
                                ProductCategoryStatus.ACTIVE.name())))
                .map(converter::toDomain);
    }

    @Override
    public List<ProductCategory> findAllActive(TenantId tenantId, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 5_001) {
            throw new IllegalArgumentException("product category limit is invalid");
        }
        return mapper.selectList(root(tenantId)
                        .eq(ProductCategoryPO::getStatus,
                                ProductCategoryStatus.ACTIVE.name())
                        .orderByAsc(ProductCategoryPO::getId).last("LIMIT " + limit))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean existsActiveChild(
            TenantId tenantId, ProductCategoryId categoryId) {
        requireTenant(tenantId);
        return categoryId != null && mapper.exists(root(tenantId)
                .eq(ProductCategoryPO::getParentCategoryId, categoryId.getValue())
                .eq(ProductCategoryPO::getStatus, ProductCategoryStatus.ACTIVE.name()));
    }

    private LambdaQueryWrapper<ProductCategoryPO> root(TenantId tenantId) {
        return new LambdaQueryWrapper<ProductCategoryPO>()
                .eq(ProductCategoryPO::getTenantId, tenantId.getValue());
    }

    private void requireMatch(TenantId tenantId, ProductCategory category) {
        requireTenant(tenantId);
        if (category == null || category.getTenantId() == null
                || !tenantId.sameValueAs(category.getTenantId())) {
            throw new IllegalArgumentException(
                    "product category tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
