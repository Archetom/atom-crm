package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductFieldValue;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ProductAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ProductQuery;
import io.github.archetom.crm.domain.model.ProductSortDirection;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.ProductPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private static final Map<FieldCode, String> BUILTIN_COLUMNS = builtinColumns();

    private final ProductMapper productMapper;
    private final ProductFieldValueMapper fieldValueMapper;
    private final ProductUniqueFieldValueMapper uniqueFieldValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final ProductPOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final ObjectMapper objectMapper;

    @Override
    public Product save(
            TenantId tenantId, Product product,
            Collection<FieldDefinition> fieldDefinitions) {
        requireMatch(tenantId, product);
        ProductPO po = converter.toPO(product);
        Map<FieldCode, FieldDefinition> fieldsByCode = definitionsByCode(fieldDefinitions);
        try {
            if (po.getId() == null) {
                if (productMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "Product insert did not affect exactly one row");
                }
            } else {
                LambdaUpdateWrapper<ProductPO> update = new LambdaUpdateWrapper<ProductPO>()
                        .eq(ProductPO::getTenantId, tenantId.getValue())
                        .eq(ProductPO::getId, po.getId());
                if (productMapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }
            ProductId productId = new ProductId(po.getId());
            synchronizeValues(tenantId, productId, product, fieldsByCode);
            product.onPersisted(productId, po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return product;
        } catch (DuplicateKeyException exception) {
            throw new ProductAlreadyExistsException();
        }
    }

    @Override
    public Optional<Product> findById(TenantId tenantId, ProductId productId) {
        requireTenant(tenantId);
        if (productId == null) {
            return Optional.empty();
        }
        ProductPO po = productMapper.selectOne(root(tenantId)
                .eq(ProductPO::getId, productId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public Optional<Product> findActiveById(
            TenantId tenantId, ProductId productId) {
        requireTenant(tenantId);
        if (productId == null) {
            return Optional.empty();
        }
        ProductPO po = productMapper.selectOne(activeRoot(tenantId)
                .eq(ProductPO::getId, productId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public boolean existsActiveByCode(TenantId tenantId, String codeKey) {
        requireTenant(tenantId);
        return codeKey != null && !codeKey.isBlank() && productMapper.exists(activeRoot(tenantId)
                .eq(ProductPO::getCodeKey, codeKey));
    }

    @Override
    public Optional<Product> findByIdForAttachmentMutation(
            TenantId tenantId, ProductId productId) {
        requireTenant(tenantId);
        if (productId == null) {
            return Optional.empty();
        }
        ProductPO po = productMapper.selectOne(root(tenantId)
                .eq(ProductPO::getId, productId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public PageResult<Product> findProducts(TenantId tenantId, ProductQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("product query must not be null");
        }
        QueryWrapper<ProductPO> wrapper = new QueryWrapper<ProductPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", ProductStatus.ACTIVE.name());
        applyKeyword(wrapper, query.keyword(), query.keywordCandidates());
        if (query.name() != null) {
            wrapper.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'",
                    SqlLikePattern.literal(query.name()));
        }
        if (query.code() != null) {
            wrapper.apply("code LIKE CONCAT({0}, '%') ESCAPE '!'",
                    SqlLikePattern.literal(query.code()));
        }
        if (query.categoryId() != null) {
            wrapper.eq("category_id", query.categoryId().getValue());
        }
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.availability() != null) {
            wrapper.eq("availability", query.availability().name());
        }
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        boolean ascending = query.sort().direction() == ProductSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<ProductPO> page = productMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0L, page.getTotal()), hydrated(tenantId, page.getRecords()));
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.ProductSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("product custom sort is invalid");
        }
        String scalar = """
                (SELECT product_field_sort.scalar_value
                FROM crm_product_field_value product_field_sort
                WHERE product_field_sort.tenant_id = crm_product.tenant_id
                  AND product_field_sort.product_id = crm_product.id
                  AND product_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private void applyKeyword(
            QueryWrapper<ProductPO> query, String keyword,
            Map<FieldCode, io.github.archetom.crm.domain.model.SearchMatchMode> candidates) {
        if (keyword == null) {
            return;
        }
        if (candidates.isEmpty()) {
            query.apply("1 = 0");
            return;
        }
        boolean name = candidates.containsKey(new FieldCode(ProductFieldCatalog.NAME));
        boolean code = candidates.containsKey(new FieldCode(ProductFieldCatalog.CODE));
        query.and(nested -> {
            if (name) {
                nested.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(keyword));
            }
            if (code) {
                if (name) nested.or();
                nested.apply("code_key LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.normalizedIdentifierPrefixLiteral(
                                new SearchKeyword(keyword)));
            }
        });
    }

    @Override
    public List<Product> findAvailableByIdsForUpdate(
            TenantId tenantId, Collection<ProductId> productIds, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("product lock limit is invalid");
        }
        List<Long> ids = productIds == null ? List.of() : productIds.stream()
                .filter(java.util.Objects::nonNull).map(ProductId::getValue)
                .distinct().sorted().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductPO> roots = productMapper.selectList(activeRoot(tenantId)
                .eq(ProductPO::getAvailability, "AVAILABLE")
                .in(ProductPO::getId, ids).orderByAsc(ProductPO::getId)
                .last("LIMIT " + limit + " FOR UPDATE"));
        return hydrated(tenantId, roots);
    }

    @Override
    public boolean existsActiveByCategory(
            TenantId tenantId, ProductCategoryId categoryId) {
        requireTenant(tenantId);
        return categoryId != null && productMapper.exists(activeRoot(tenantId)
                .eq(ProductPO::getCategoryId, categoryId.getValue()));
    }

    private void synchronizeValues(
            TenantId tenantId, ProductId productId, Product product,
            Map<FieldCode, FieldDefinition> fieldsByCode) {
        fieldValueMapper.delete(new LambdaQueryWrapper<ProductFieldValuePO>()
                .eq(ProductFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ProductFieldValuePO::getProductId, productId.getValue()));
        uniqueFieldValueMapper.delete(new LambdaQueryWrapper<ProductUniqueFieldValuePO>()
                .eq(ProductUniqueFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ProductUniqueFieldValuePO::getProductId, productId.getValue()));
        for (ProductFieldValue value : product.getCustomFieldValues()) {
            FieldDefinition field = fieldsByCode.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException(
                        "product field definition is missing");
            }
            fieldValueMapper.insert(converter.toPO(tenantId, productId, value, field));
            if (product.isActive() && field.isUnique()) {
                uniqueFieldValueMapper.insert(uniqueValue(
                        tenantId, productId, field, value.values().getFirst()));
            }
        }
    }

    private List<Product> hydrated(TenantId tenantId, List<ProductPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roots.stream().map(ProductPO::getId).distinct().toList();
        List<ProductFieldValuePO> values = fieldValueMapper.selectList(
                new LambdaQueryWrapper<ProductFieldValuePO>()
                        .eq(ProductFieldValuePO::getTenantId, tenantId.getValue())
                        .in(ProductFieldValuePO::getProductId, ids)
                        .orderByAsc(ProductFieldValuePO::getFieldId));
        Set<Long> fieldIds = values.stream().map(ProductFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fieldsById = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                                .eq(CustomFieldDefinitionPO::getTenantId,
                                        tenantId.getValue())
                                .eq(CustomFieldDefinitionPO::getObjectType,
                                        BusinessObjectType.PRODUCT.name())
                                .in(CustomFieldDefinitionPO::getId, fieldIds))
                        .stream().map(fieldDefinitionConverter::toDomain)
                        .collect(Collectors.toMap(
                                field -> field.getId().getValue(), Function.identity()));
        Map<Long, List<ProductFieldValuePO>> valueMap = values.stream()
                .collect(Collectors.groupingBy(ProductFieldValuePO::getProductId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(po -> converter.toDomain(po,
                valueMap.getOrDefault(po.getId(), List.of()), fieldsById)).toList();
    }

    private void applyCondition(
            QueryWrapper<ProductPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("product filter condition is required");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            applyBuiltin(query, column, condition);
        } else {
            applyCustom(query, condition);
        }
    }

    private void applyBuiltin(
            QueryWrapper<ProductPO> query, String column, FilterCondition condition) {
        List<String> values = condition.values();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.EQ) {
            query.eq(column, values.getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.ne(column, values.getFirst());
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) > 0", values.getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) = 0", values.getFirst());
        } else if (operator == FilterOperator.IS_EMPTY) {
            query.and(value -> value.isNull(column).or().eq(column, ""));
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.isNotNull(column).ne(column, "");
        } else if (operator == FilterOperator.GT) {
            query.gt(column, values.getFirst());
        } else if (operator == FilterOperator.GTE) {
            query.ge(column, values.getFirst());
        } else if (operator == FilterOperator.LT) {
            query.lt(column, values.getFirst());
        } else if (operator == FilterOperator.LTE) {
            query.le(column, values.getFirst());
        } else if (operator == FilterOperator.IN) {
            query.in(column, values);
        } else if (operator == FilterOperator.BETWEEN) {
            query.between(column, values.get(0), values.get(1));
        }
    }

    private void applyCustom(
            QueryWrapper<ProductPO> query, FilterCondition condition) {
        String base = """
                SELECT 1
                FROM crm_product_field_value field_value
                JOIN custom_field_definition field_definition
                  ON field_definition.tenant_id = field_value.tenant_id
                 AND field_definition.object_type = field_value.object_type
                 AND field_definition.id = field_value.field_id
                WHERE field_value.tenant_id = crm_product.tenant_id
                  AND field_value.product_id = crm_product.id
                  AND field_definition.field_code = {0}
                """;
        String code = condition.fieldCode().getValue();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.IS_EMPTY) {
            query.apply("NOT EXISTS (" + base + ")", code);
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.apply("EXISTS (" + base + ")", code);
        } else if (operator == FilterOperator.EQ) {
            query.apply("EXISTS (" + base
                    + " AND JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    code, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.apply("EXISTS (" + base
                    + " AND NOT JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    code, condition.values().getFirst());
        } else if (operator == FilterOperator.IN) {
            query.apply("EXISTS (" + base
                    + " AND JSON_OVERLAPS(field_value.values_json, CAST({1} AS JSON)))",
                    code, json(condition.values()));
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("EXISTS (" + base
                    + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    code, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("NOT EXISTS (" + base
                    + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    code, condition.values().getFirst());
        } else if (operator == FilterOperator.BETWEEN) {
            query.apply("EXISTS (" + base + " AND "
                            + ordered("{1}", ">=") + " AND " + ordered("{2}", "<=") + ")",
                    code, condition.values().get(0), condition.values().get(1));
        } else {
            String symbol = null;
            if (operator == FilterOperator.GT) {
                symbol = ">";
            } else if (operator == FilterOperator.GTE) {
                symbol = ">=";
            } else if (operator == FilterOperator.LT) {
                symbol = "<";
            } else if (operator == FilterOperator.LTE) {
                symbol = "<=";
            }
            if (symbol != null) {
                query.apply("EXISTS (" + base + " AND " + ordered("{1}", symbol) + ")",
                        code, condition.values().getFirst());
            }
        }
    }

    private String ordered(String parameter, String operator) {
        return "((field_definition.field_type IN ('INTEGER','DECIMAL') AND "
                + "CAST(field_value.scalar_value AS DECIMAL(30,10)) " + operator
                + " CAST(" + parameter + " AS DECIMAL(30,10))) OR "
                + "(field_definition.field_type NOT IN ('INTEGER','DECIMAL') AND "
                + "field_value.scalar_value " + operator + " " + parameter + "))";
    }

    private ProductUniqueFieldValuePO uniqueValue(
            TenantId tenantId, ProductId productId,
            FieldDefinition field, String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        ProductUniqueFieldValuePO po = new ProductUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setProductId(productId.getValue());
        po.setObjectType(BusinessObjectType.PRODUCT.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedHash(hash(normalized));
        po.setNormalizedValue(normalized);
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private LambdaQueryWrapper<ProductPO> root(TenantId tenantId) {
        return new LambdaQueryWrapper<ProductPO>()
                .eq(ProductPO::getTenantId, tenantId.getValue());
    }

    private LambdaQueryWrapper<ProductPO> activeRoot(TenantId tenantId) {
        return root(tenantId).eq(ProductPO::getStatus, ProductStatus.ACTIVE.name());
    }

    private Map<FieldCode, FieldDefinition> definitionsByCode(
            Collection<FieldDefinition> values) {
        if (values == null) {
            return Map.of();
        }
        return values.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private void requireMatch(TenantId tenantId, Product product) {
        requireTenant(tenantId);
        if (product == null || product.getTenantId() == null
                || !tenantId.sameValueAs(product.getTenantId())) {
            throw new IllegalArgumentException(
                    "product tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Product query value cannot be serialized", exception);
        }
    }

    private static Map<FieldCode, String> builtinColumns() {
        Map<FieldCode, String> values = new HashMap<>();
        values.put(new FieldCode(ProductFieldCatalog.NAME), "name");
        values.put(new FieldCode(ProductFieldCatalog.CODE), "code");
        values.put(new FieldCode(ProductFieldCatalog.CATEGORY), "category_id");
        values.put(new FieldCode(ProductFieldCatalog.UNIT), "unit");
        values.put(new FieldCode(ProductFieldCatalog.LIST_PRICE), "list_price");
        values.put(new FieldCode(ProductFieldCatalog.DESCRIPTION), "description");
        values.put(new FieldCode(ProductFieldCatalog.OWNER_ACTOR), "owner_actor_id");
        values.put(new FieldCode(ProductFieldCatalog.AVAILABILITY), "availability");
        values.put(new FieldCode(ProductFieldCatalog.STATUS), "status");
        values.put(new FieldCode(ProductFieldCatalog.CREATED_TIME), "created_time");
        values.put(new FieldCode(ProductFieldCatalog.UPDATED_TIME), "updated_time");
        return Map.copyOf(values);
    }
}
