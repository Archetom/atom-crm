package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ProductQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ProductCategoryVO;
import io.github.archetom.crm.application.vo.ProductEditableBaselineVO;
import io.github.archetom.crm.application.vo.ProductVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductCategory;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.ProductCategoryNotFoundException;
import io.github.archetom.crm.domain.exception.ProductDomainException;
import io.github.archetom.crm.domain.exception.ProductNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductQuery;
import io.github.archetom.crm.domain.model.ProductSort;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldValuePolicy;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductQueryServiceImpl implements ProductQueryService {

    private static final int MAX_CATEGORIES = 5_000;

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final SavedViewRepository savedViewRepository;
    private final ProductApplicationSupport support;
    private final OrganizationAccessGuard accessGuard;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ProductVO>> query(
            AuthenticatedCaller caller, ProductQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.PRODUCT_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private SearchKeyword keyword;

                    @Override
                    public void validate() {
                        requireRequest(request, "Product query request");
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        memberId = member(caller.actorId());
                        keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
                    }

                    @Override
                    public Pager<ProductVO> execute() {
                        if (request.getSavedViewId() != null && request.getConditions() != null
                                && !request.getConditions().isEmpty()) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Product saved and temporary filters are mutually exclusive");
                        }
                        if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Product static and custom sort fields are mutually exclusive");
                        }
                        List<FieldDefinition> fields = support.fields(tenantId).stream()
                                .filter(FieldDefinition::isActive).toList();
                        Map<FieldCode, EffectiveFieldAccess> accesses =
                                support.accesses(tenantId, memberId);
                        ProductCategoryId categoryId = categoryId(request.getCategoryId());
                        MemberId owner = nullableMember(request.getOwnerActorId());
                        ProductAvailability availability = request.getAvailability() == null
                                ? null : support.parse(ProductAvailability.class,
                                        request.getAvailability(), "Product availability");
                        if (request.getName() != null) {
                            support.requireReadableUnmasked(accesses,
                                    new FieldCode(ProductFieldCatalog.NAME));
                        }
                        if (request.getCode() != null) {
                            support.requireReadableUnmasked(accesses,
                                    new FieldCode(ProductFieldCatalog.CODE));
                        }
                        if (categoryId != null) {
                            support.requireReadableUnmasked(accesses,
                                    new FieldCode(ProductFieldCatalog.CATEGORY));
                            requireCategory(tenantId, categoryId);
                        }
                        if (owner != null) {
                            support.requireReadableUnmasked(accesses,
                                    new FieldCode(ProductFieldCatalog.OWNER_ACTOR));
                        }
                        if (availability != null) {
                            support.requireReadableUnmasked(accesses,
                                    new FieldCode(ProductFieldCatalog.AVAILABILITY));
                        }
                        ProductSort sort;
                        if (request.getCustomSortFieldCode() == null) {
                            sort = productSort(request.getSortField(), request.getSortDirection());
                        } else if (request.getSortDirection() == null) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Product custom sort must include direction");
                        } else {
                            sort = customProductSort(request.getCustomSortFieldCode(),
                                    request.getSortDirection(), fields, accesses);
                        }
                        if (!sort.isCustom() && !sort.isDefault()) {
                            support.requireReadableUnmasked(accesses, sort.field().fieldCode());
                        }
                        List<FilterCondition> conditions = conditions(
                                request.getSavedViewId(), request.getConditions(), tenantId, memberId,
                                fields, accesses);
                        PageResult<Product> page = productRepository.findProducts(
                                tenantId, new ProductQuery(
                                        keyword == null ? null : keyword.value(),
                                        FieldAccessPolicy.searchableCandidates(fields, accesses,
                                                ProductFieldCatalog.searchCandidates()),
                                        request.getName(),
                                        request.getCode(), categoryId, owner, availability,
                                        sort,
                                        conditions, page(request.getPage()),
                                        size(request.getSize())));
                        Map<ProductCategoryId, String> categories = categoryNames(
                                tenantId, page.items());
                        Pager<ProductVO> pager = pager(
                                page.page(), page.size(), page.total());
                        pager.setObjectList(page.items().stream().map(product ->
                                support.toVO(product,
                                        product.getCategoryId() == null ? null
                                                : categories.get(product.getCategoryId()),
                                        accesses))
                                .toList());
                        return pager;
                    }
                });
    }

    private ProductSort productSort(String field, String direction) {
        try {
            return ProductSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Product sort is invalid");
        }
    }

    private ProductSort customProductSort(
            String requestedCode, String direction, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product custom sort field is invalid");
        }
        FieldDefinition field = fields.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product custom sort field is unavailable");
        }
        support.requireReadableUnmasked(accesses, code);
        try {
            return ProductSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Product sort is invalid");
        }
    }

    @Override
    public Result<ProductVO> get(AuthenticatedCaller caller, Long productIdValue) {
        return queryTemplate.execute(UseCaseOperation.PRODUCT_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ProductId productId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        memberId = member(caller.actorId());
                        productId = productId(productIdValue);
                    }

                    @Override
                    public ProductVO execute() {
                        Product product = productRepository.findActiveById(
                                        tenantId, productId)
                                .orElseThrow(ProductNotFoundException::new);
                        String category = product.getCategoryId() == null ? null
                                : requireCategory(tenantId,
                                        product.getCategoryId()).getName();
                        return support.toVO(product, category,
                                support.accesses(tenantId, memberId));
                    }
                });
    }

    @Override
    public Result<ProductEditableBaselineVO> editableBaseline(
            AuthenticatedCaller caller, Long productIdValue) {
        return queryTemplate.execute(UseCaseOperation.PRODUCT_EDITABLE_BASELINE_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ProductId productId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_WRITE);
                        memberId = member(caller.actorId());
                        productId = productId(productIdValue);
                    }

                    @Override
                    public ProductEditableBaselineVO execute() {
                        Product product = productRepository.findActiveById(
                                        tenantId, productId)
                                .orElseThrow(ProductNotFoundException::new);
                        return support.editableBaseline(product,
                                support.accesses(tenantId, memberId));
                    }
                });
    }

    @Override
    public Result<List<ProductCategoryVO>> categoryTree(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.PRODUCT_CATEGORY_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                    }

                    @Override
                    public List<ProductCategoryVO> execute() {
                        return tree(categories(tenantId));
                    }
                });
    }

    @Override
    public Result<ProductCategoryVO> getCategory(
            AuthenticatedCaller caller, Long categoryIdValue) {
        return queryTemplate.execute(UseCaseOperation.PRODUCT_CATEGORY_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ProductCategoryId categoryId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.PRODUCTS_READ);
                        categoryId = categoryId(categoryIdValue);
                    }

                    @Override
                    public ProductCategoryVO execute() {
                        return support.categoryVO(
                                requireCategory(tenantId, categoryId), List.of());
                    }
                });
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested,
            TenantId tenantId, MemberId memberId,
            List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (viewId == null && requested != null && !requested.isEmpty()) {
            return temporaryConditions(requested, tenantId, fields, accesses);
        }
        if (viewId == null) {
            return List.of();
        }
        SavedView view;
        try {
            view = savedViewRepository.findById(
                            tenantId, memberId, new SavedViewId(viewId))
                    .orElseThrow(() -> support.error(
                            ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.PRODUCT) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a product view");
        }
        Map<FieldCode, FieldDefinition> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> types = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(types);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream().map(condition ->
                    ProductFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (ProductDomainException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product saved view condition is invalid");
        }
        for (FilterCondition condition : normalized) {
            support.requireReadableUnmasked(accesses, condition.fieldCode());
            validateCategoryCondition(tenantId, condition);
        }
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, TenantId tenantId,
            List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, FieldDefinition> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> types = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        List<FilterCondition> normalized;
        try {
            List<FilterCondition> values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Product condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, types);
            normalized = values.stream().map(condition ->
                    ProductFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product temporary filter condition is invalid");
        }
        for (FilterCondition condition : normalized) {
            support.requireReadableUnmasked(accesses, condition.fieldCode());
            validateCategoryCondition(tenantId, condition);
        }
        return normalized;
    }

    private void validateCategoryCondition(
            TenantId tenantId, FilterCondition condition) {
        if (!new FieldCode(ProductFieldCatalog.CATEGORY)
                .equals(condition.fieldCode())) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ
                && condition.operator() != FilterOperator.IN
                && condition.operator() != FilterOperator.IS_EMPTY
                && condition.operator() != FilterOperator.IS_NOT_EMPTY) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product category filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                requireCategory(tenantId,
                        new ProductCategoryId(Long.parseLong(value)));
            } catch (NumberFormatException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product category filter is invalid");
            }
        }
    }

    private Map<ProductCategoryId, String> categoryNames(
            TenantId tenantId, List<Product> products) {
        Set<ProductCategoryId> wanted = products.stream()
                .map(Product::getCategoryId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return categories(tenantId).stream()
                .filter(value -> wanted.contains(value.getId()))
                .collect(Collectors.toMap(ProductCategory::getId,
                        ProductCategory::getName));
    }

    private List<ProductCategoryVO> tree(List<ProductCategory> categories) {
        Map<ProductCategoryId, ProductCategory> byId = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, Function.identity()));
        Map<ProductCategoryId, List<ProductCategory>> children = new HashMap<>();
        List<ProductCategory> roots = new ArrayList<>();
        for (ProductCategory category : categories) {
            if (category.getParentCategoryId() == null) {
                roots.add(category);
            } else if (byId.containsKey(category.getParentCategoryId())) {
                children.computeIfAbsent(category.getParentCategoryId(), ignored ->
                        new ArrayList<>()).add(category);
            } else {
                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Stored product category hierarchy is invalid");
            }
        }
        Comparator<ProductCategory> order = Comparator
                .comparing(ProductCategory::getName).thenComparing(
                        value -> value.getId().getValue());
        roots.sort(order);
        children.values().forEach(values -> values.sort(order));
        Set<ProductCategoryId> visited = new HashSet<>();
        List<ProductCategoryVO> result = roots.stream().map(value ->
                node(value, children, visited, 1)).toList();
        if (visited.size() != categories.size()) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Stored product category hierarchy is invalid");
        }
        return result;
    }

    private ProductCategoryVO node(
            ProductCategory category,
            Map<ProductCategoryId, List<ProductCategory>> children,
            Set<ProductCategoryId> visited,
            int depth) {
        if (depth > 16 || !visited.add(category.getId())) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Stored product category hierarchy is invalid");
        }
        return support.categoryVO(category,
                children.getOrDefault(category.getId(), List.of()).stream()
                        .map(value -> node(value, children, visited, depth + 1)).toList());
    }

    private List<ProductCategory> categories(TenantId tenantId) {
        List<ProductCategory> values = categoryRepository.findAllActive(
                tenantId, MAX_CATEGORIES + 1);
        if (values.size() > MAX_CATEGORIES) {
            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "Product category catalog is too large");
        }
        return values;
    }

    private ProductCategory requireCategory(
            TenantId tenantId, ProductCategoryId categoryId) {
        return categoryRepository.findActiveById(tenantId, categoryId)
                .orElseThrow(ProductCategoryNotFoundException::new);
    }

    private ProductId productId(Long value) {
        try {
            return new ProductId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product ID must be positive");
        }
    }

    private ProductCategoryId categoryId(Long value) {
        try {
            return value == null ? null : new ProductCategoryId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product category ID must be positive");
        }
    }

    private MemberId nullableMember(Long value) {
        return value == null ? null : member(value);
    }

    private MemberId member(Long value) {
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Member ID must be positive");
        }
    }

    private int page(Integer value) {
        return value == null ? 1 : value;
    }

    private int size(Integer value) {
        return value == null ? 20 : value;
    }

    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        return pager;
    }

    private void requireRequest(Object value, String description) {
        if (value == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }
}
