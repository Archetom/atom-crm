package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductFieldValue;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductPO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class ProductPOConverter {

    private final ObjectMapper objectMapper;

    public ProductPOConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductPO toPO(Product product) {
        ProductPO po = new ProductPO();
        po.setId(product.getId() == null ? null : product.getId().getValue());
        po.setTenantId(product.getTenantId().getValue());
        po.setCategoryId(product.getCategoryId() == null ? null
                : product.getCategoryId().getValue());
        po.setOwnerActorId(product.getOwnerMemberId().getValue());
        po.setName(product.getName());
        po.setCode(product.getCode());
        po.setCodeKey(product.getCodeKey());
        po.setUnit(product.getUnit());
        po.setListPrice(product.getListPrice());
        po.setDescription(product.getDescription());
        po.setAvailability(product.getAvailability().name());
        po.setStatus(product.getStatus().name());
        po.setVersion(product.getVersion());
        po.setCreatedTime(product.getCreatedTime());
        po.setUpdatedTime(product.getUpdatedTime());
        return po;
    }

    public Product toDomain(
            ProductPO po, List<ProductFieldValuePO> values,
            Map<Long, FieldDefinition> fieldsById) {
        return Product.reconstitute(new Product.Snapshot(
                new ProductId(po.getId()), new TenantId(po.getTenantId()),
                po.getCategoryId() == null ? null
                        : new ProductCategoryId(po.getCategoryId()),
                new MemberId(po.getOwnerActorId()), po.getName(), po.getCode(),
                po.getCodeKey(), po.getUnit(), po.getListPrice(), po.getDescription(),
                ProductAvailability.valueOf(po.getAvailability()),
                ProductStatus.valueOf(po.getStatus()),
                values.stream().map(value -> toFieldValue(value, fieldsById)).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public ProductFieldValuePO toPO(
            TenantId tenantId, ProductId productId,
            ProductFieldValue value, FieldDefinition field) {
        try {
            ProductFieldValuePO po = new ProductFieldValuePO();
            po.setTenantId(tenantId.getValue());
            po.setProductId(productId.getValue());
            po.setObjectType(field.getObjectType().name());
            po.setFieldId(field.getId().getValue());
            po.setValuesJson(objectMapper.writeValueAsString(value.values()));
            po.setScalarValue(value.values().size() == 1
                    ? value.values().getFirst() : null);
            LocalDateTime now = LocalDateTime.now();
            po.setCreatedTime(now);
            po.setUpdatedTime(now);
            return po;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Product field value cannot be serialized", exception);
        }
    }

    private ProductFieldValue toFieldValue(
            ProductFieldValuePO po, Map<Long, FieldDefinition> fieldsById) {
        FieldDefinition field = fieldsById.get(po.getFieldId());
        if (field == null) {
            throw new IllegalStateException(
                    "Stored product field definition is missing");
        }
        try {
            List<String> values = objectMapper.readValue(
                    po.getValuesJson(), new TypeReference<List<String>>() { });
            return new ProductFieldValue(field.getCode(), values);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Stored product field value is invalid", exception);
        }
    }
}
