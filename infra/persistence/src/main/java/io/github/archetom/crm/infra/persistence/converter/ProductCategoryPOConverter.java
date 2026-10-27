package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.model.ProductCategoryStatus;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductCategoryPO;
import org.springframework.stereotype.Component;

@Component
public class ProductCategoryPOConverter {

    public ProductCategoryPO toPO(ProductCategory category) {
        ProductCategoryPO po = new ProductCategoryPO();
        po.setId(category.getId() == null ? null : category.getId().getValue());
        po.setTenantId(category.getTenantId().getValue());
        po.setParentCategoryId(category.getParentCategoryId() == null ? null
                : category.getParentCategoryId().getValue());
        po.setName(category.getName());
        po.setNameKey(category.getNameKey());
        po.setStatus(category.getStatus().name());
        po.setVersion(category.getVersion());
        po.setCreatedTime(category.getCreatedTime());
        po.setUpdatedTime(category.getUpdatedTime());
        return po;
    }

    public ProductCategory toDomain(ProductCategoryPO po) {
        return ProductCategory.reconstitute(new ProductCategory.Snapshot(
                new ProductCategoryId(po.getId()), new TenantId(po.getTenantId()),
                po.getParentCategoryId() == null ? null
                        : new ProductCategoryId(po.getParentCategoryId()),
                po.getName(), po.getNameKey(),
                ProductCategoryStatus.valueOf(po.getStatus()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime()));
    }
}
