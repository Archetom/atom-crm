package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ProductCsvExportRequest;
import io.github.archetom.crm.application.export.BoundedCsvEncoder;
import io.github.archetom.crm.application.export.BoundedCsvEncoder.CsvLimitExceededException;
import io.github.archetom.crm.application.export.ProductCsvDocument;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.ProductCsvExportService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.entity.ProductFieldValue;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ProductAvailability;
import io.github.archetom.crm.domain.model.ProductQuery;
import io.github.archetom.crm.domain.model.ProductSort;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldValuePolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ProductCategoryId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bounded, synchronous Product CSV export with server-owned fields, filters, and policy checks. */
@Service
@RequiredArgsConstructor
public class ProductCsvExportServiceImpl implements ProductCsvExportService {

    static final int MAX_FIELDS = 50;
    static final int MAX_ROWS = 5_000;
    static final int PAGE_SIZE = 200;
    static final int MAX_BYTES = 16 * 1024 * 1024;

    private static final Set<CustomFieldType> EXPORTABLE_TYPES = Set.of(
            CustomFieldType.TEXT, CustomFieldType.TEXTAREA, CustomFieldType.SELECT,
            CustomFieldType.DATE, CustomFieldType.DATETIME, CustomFieldType.INTEGER,
            CustomFieldType.DECIMAL, CustomFieldType.BOOLEAN, CustomFieldType.EMAIL,
            CustomFieldType.PHONE, CustomFieldType.URL);
    private static final Set<CustomFieldType> FORMULA_GUARDED_TYPES = Set.of(
            CustomFieldType.TEXT, CustomFieldType.TEXTAREA, CustomFieldType.SELECT,
            CustomFieldType.EMAIL, CustomFieldType.PHONE, CustomFieldType.URL);
    private static final Set<FieldCode> NON_PROJECTABLE_FIELD_CODES = Set.of(
            new FieldCode(ProductFieldCatalog.CATEGORY), new FieldCode(ProductFieldCatalog.OWNER_ACTOR));

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final SavedViewRepository savedViewRepository;
    private final OrganizationAccessGuard accessGuard;
    private final ProductApplicationSupport support;
    private final CustomerCsvExportAdmission admission;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<ProductCsvDocument> export(AuthenticatedCaller caller, ProductCsvExportRequest request) {
        TenantId callerTenant;
        try {
            callerTenant = accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_READ);
            TenantId exportTenant = accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_EXPORT);
            if (!callerTenant.sameValueAs(exportTenant)) {
                return reject(ApplicationErrorCode.ACCESS_DENIED, "Product export access is unavailable");
            }
        } catch (ApplicationException exception) {
            return queryTemplate.reject(UseCaseOperation.PRODUCT_CSV_EXPORT, exception);
        }

        MemberId callerMember = new MemberId(caller.actorId());
        CustomerCsvExportAdmission.Lease lease = admission.tryAcquire(callerTenant, callerMember);
        if (lease == null) {
            return reject(ApplicationErrorCode.CONCURRENT_OPERATION,
                    "Product export capacity is unavailable");
        }
        boolean documentOwnsLease = false;
        try {
            Result<ProductCsvDocument> result = queryTemplate.execute(UseCaseOperation.PRODUCT_CSV_EXPORT,
                    new ServiceOperation<>() {
                        @Override
                        public void validate() {
                            fields(request);
                            if (request.getSavedViewId() != null && request.getConditions() != null
                                    && !request.getConditions().isEmpty()) {
                                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                        "Product saved and temporary filters are mutually exclusive");
                            }
                        }

                        @Override
                        public ProductCsvDocument execute() {
                            List<FieldCode> requestedFields = fields(request);
                            List<FieldDefinition> definitions = support.fields(callerTenant).stream()
                                    .filter(FieldDefinition::isActive).toList();
                            Map<FieldCode, EffectiveFieldAccess> accesses = support.accesses(
                                    callerTenant, callerMember);
                            List<FieldDefinition> selected = selectedFields(
                                    requestedFields, definitions, accesses);
                            ProductCategoryId categoryId = categoryId(request.getCategoryId());
                            MemberId owner = member(request.getOwnerActorId());
                            ProductAvailability availability = request.getAvailability() == null ? null
                                    : support.parse(ProductAvailability.class, request.getAvailability(),
                                            "Product availability");
                            if (request.getName() != null) {
                                requireReadableUnmasked(accesses,
                                        new FieldCode(ProductFieldCatalog.NAME));
                            }
                            if (request.getCode() != null) {
                                requireReadableUnmasked(accesses,
                                        new FieldCode(ProductFieldCatalog.CODE));
                            }
                            if (categoryId != null) {
                                requireReadableUnmasked(accesses,
                                        new FieldCode(ProductFieldCatalog.CATEGORY));
                                requireCategory(callerTenant, categoryId);
                            }
                            if (owner != null) {
                                requireReadableUnmasked(accesses,
                                        new FieldCode(ProductFieldCatalog.OWNER_ACTOR));
                            }
                            if (availability != null) {
                                requireReadableUnmasked(accesses,
                                        new FieldCode(ProductFieldCatalog.AVAILABILITY));
                            }
                            ProductSort sort = sort(request, definitions, accesses);
                            if (!sort.isCustom() && !sort.isDefault()) {
                                requireReadableUnmasked(accesses, sort.field().fieldCode());
                            }
                            List<FilterCondition> conditions = conditions(request.getSavedViewId(),
                                    request.getConditions(), callerTenant, callerMember, definitions, accesses);
                            ProductQuery query = new ProductQuery(
                                    SearchKeyword.optional(request.getKeyword()).map(SearchKeyword::value).orElse(null),
                                    FieldAccessPolicy.searchableCandidates(definitions, accesses,
                                            ProductFieldCatalog.searchCandidates()),
                                    request.getName(), request.getCode(), categoryId, owner, availability,
                                    sort, conditions, 1, PAGE_SIZE);
                            List<Product> products = products(callerTenant, query);
                            try {
                                BoundedCsvEncoder encoder = new BoundedCsvEncoder(MAX_BYTES);
                                encoder.row(headers(requestedFields),
                                        allFormulaSafe(requestedFields.size() + 1));
                                appendRows(encoder, products, selected, requestedFields);
                                return new ProductCsvDocument(encoder.toByteArray(), lease);
                            } catch (CsvLimitExceededException exception) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Product export exceeds the byte limit");
                            }
                        }
                    });
            documentOwnsLease = result.isSuccess();
            return result;
        } finally {
            if (!documentOwnsLease) {
                lease.close();
            }
        }
    }

    private Result<ProductCsvDocument> reject(ApplicationErrorCode code, String message) {
        return queryTemplate.reject(UseCaseOperation.PRODUCT_CSV_EXPORT, support.error(code, message));
    }

    private List<FieldCode> fields(ProductCsvExportRequest request) {
        if (request == null || request.getFieldCodes() == null || request.getFieldCodes().isEmpty()
                || request.getFieldCodes().size() > MAX_FIELDS) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product export fields are invalid");
        }
        List<FieldCode> fields = new ArrayList<>();
        for (String code : request.getFieldCodes()) {
            try {
                fields.add(new FieldCode(code));
            } catch (RuntimeException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product export fields are invalid");
            }
        }
        if (new LinkedHashSet<>(fields).size() != fields.size()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product export fields are duplicated");
        }
        return List.copyOf(fields);
    }

    private List<FieldDefinition> selectedFields(
            List<FieldCode> requested, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, FieldDefinition> byCode = definitions.stream().collect(Collectors.toMap(
                FieldDefinition::getCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<FieldDefinition> selected = new ArrayList<>();
        for (FieldCode code : requested) {
            FieldDefinition definition = byCode.get(code);
            if (definition == null || !definition.isActive()
                    || NON_PROJECTABLE_FIELD_CODES.contains(code)
                    || !EXPORTABLE_TYPES.contains(definition.getFieldType())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product export field is unavailable");
            }
            requireReadableUnmasked(accesses, code);
            selected.add(definition);
        }
        return List.copyOf(selected);
    }

    private ProductSort sort(ProductCsvExportRequest request, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (request.getCustomSortFieldCode() == null) {
            try {
                return ProductSort.from(request.getSortField(), request.getSortDirection());
            } catch (RuntimeException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Product sort is invalid");
            }
        }
        if (request.getSortField() != null || request.getSortDirection() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Product sort is invalid");
        }
        FieldCode code;
        try {
            code = new FieldCode(request.getCustomSortFieldCode());
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product custom sort field is invalid");
        }
        FieldDefinition definition = definitions.stream().filter(value -> value.getCode().equals(code))
                .findFirst().orElse(null);
        if (definition == null || definition.isBuiltin() || !definition.getFieldType().supportsListSorting()
                || definition.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product custom sort field is unavailable");
        }
        requireReadableUnmasked(accesses, code);
        try {
            return ProductSort.custom(definition.getId().getValue(), definition.getFieldType(),
                    request.getSortDirection());
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Product sort is invalid");
        }
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, TenantId tenantId, MemberId memberId,
            List<FieldDefinition> definitions, Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (viewId == null && requested != null && !requested.isEmpty()) {
            return temporaryConditions(requested, tenantId, definitions, accesses);
        }
        if (viewId == null) {
            return List.of();
        }
        SavedView view;
        try {
            view = savedViewRepository.findById(tenantId, memberId, new SavedViewId(viewId))
                    .orElseThrow(() -> support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.PRODUCT) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a product view");
        }
        Map<FieldCode, FieldDefinition> searchable = definitions.stream()
                .filter(FieldDefinition::isSearchable).collect(Collectors.toMap(
                        FieldDefinition::getCode, Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> types = searchable.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(types);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream().map(condition ->
                    ProductFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product saved view condition is invalid");
        }
        normalized.forEach(condition -> {
            requireReadableUnmasked(accesses, condition.fieldCode());
            validateCategoryCondition(tenantId, condition);
        });
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, TenantId tenantId,
            List<FieldDefinition> definitions, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Map<FieldCode, FieldDefinition> searchable = definitions.stream()
                .filter(FieldDefinition::isSearchable).collect(Collectors.toMap(
                        FieldDefinition::getCode, Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> types = searchable.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
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
            normalized = values.stream().map(condition -> ProductFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product temporary filter condition is invalid");
        }
        normalized.forEach(condition -> {
            requireReadableUnmasked(accesses, condition.fieldCode());
            validateCategoryCondition(tenantId, condition);
        });
        return normalized;
    }

    private void validateCategoryCondition(TenantId tenantId, FilterCondition condition) {
        if (!new FieldCode(ProductFieldCatalog.CATEGORY).equals(condition.fieldCode())) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN
                && condition.operator() != FilterOperator.IS_EMPTY
                && condition.operator() != FilterOperator.IS_NOT_EMPTY) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product category filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                requireCategory(tenantId, new ProductCategoryId(Long.parseLong(value)));
            } catch (NumberFormatException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Product category filter is invalid");
            }
        }
    }

    private List<Product> products(TenantId tenantId, ProductQuery baseQuery) {
        List<Product> products = new ArrayList<>();
        long expectedTotal = -1;
        for (int page = 1; ; page++) {
            ProductQuery query = new ProductQuery(baseQuery.keyword(), baseQuery.keywordCandidates(),
                    baseQuery.name(), baseQuery.code(), baseQuery.categoryId(), baseQuery.ownerMemberId(),
                    baseQuery.availability(), baseQuery.sort(), baseQuery.conditions(), page, PAGE_SIZE);
            PageResult<Product> result = productRepository.findProducts(tenantId, query);
            if (expectedTotal < 0) {
                expectedTotal = result.total();
                if (expectedTotal > MAX_ROWS) {
                    throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Product export exceeds the row limit");
                }
            }
            products.addAll(result.items());
            if (products.size() >= expectedTotal) {
                return List.copyOf(products.subList(0, (int) expectedTotal));
            }
            if (result.items().isEmpty() || page >= Math.ceil((double) MAX_ROWS / PAGE_SIZE)) {
                throw support.error(ApplicationErrorCode.CONCURRENT_OPERATION,
                        "Product export changed while it was being prepared");
            }
        }
    }

    private void appendRows(BoundedCsvEncoder encoder, List<Product> products,
            List<FieldDefinition> selected, List<FieldCode> requested) {
        for (Product product : products) {
            if (product == null || product.getId() == null) {
                throw new IllegalStateException("product export row projection is invalid");
            }
            List<String> values = new ArrayList<>(requested.size() + 1);
            List<Boolean> formulaSafe = new ArrayList<>(requested.size() + 1);
            values.add(Long.toString(product.getId().getValue()));
            formulaSafe.add(false);
            for (int index = 0; index < requested.size(); index++) {
                FieldCode code = requested.get(index);
                FieldDefinition field = selected.get(index);
                values.add(normalize(value(product, code), field.getFieldType()));
                formulaSafe.add(FORMULA_GUARDED_TYPES.contains(field.getFieldType()));
            }
            encoder.row(values, formulaSafe);
        }
    }

    private String value(Product product, FieldCode code) {
        return switch (code.getValue()) {
            case ProductFieldCatalog.NAME -> product.getName();
            case ProductFieldCatalog.CODE -> product.getCode();
            case ProductFieldCatalog.UNIT -> product.getUnit();
            case ProductFieldCatalog.LIST_PRICE -> product.getListPrice().toPlainString();
            case ProductFieldCatalog.DESCRIPTION -> product.getDescription();
            case ProductFieldCatalog.AVAILABILITY -> product.getAvailability().name();
            case ProductFieldCatalog.STATUS -> product.getStatus().name();
            case ProductFieldCatalog.CREATED_TIME -> product.getCreatedTime().toString();
            case ProductFieldCatalog.UPDATED_TIME -> product.getUpdatedTime().toString();
            default -> product.getCustomFieldValues().stream()
                    .filter(value -> code.equals(value.fieldCode())).findFirst()
                    .map(ProductFieldValue::values).map(values -> String.join(";", values)).orElse(null);
        };
    }

    private List<String> headers(List<FieldCode> fields) {
        List<String> headers = new ArrayList<>(fields.size() + 1);
        headers.add("product_id");
        fields.forEach(field -> headers.add(field.getValue()));
        return headers;
    }

    private List<Boolean> allFormulaSafe(int size) {
        List<Boolean> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(true);
        }
        return values;
    }

    private String normalize(String value, CustomFieldType type) {
        if (value == null) {
            return null;
        }
        try {
            return switch (type) {
                case INTEGER -> Long.toString(Long.parseLong(value));
                case DECIMAL -> new BigDecimal(value).toPlainString();
                case BOOLEAN -> booleanValue(value);
                case DATE -> LocalDate.parse(value).toString();
                case DATETIME -> LocalDateTime.parse(value).toString();
                default -> value;
            };
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new IllegalStateException("product export scalar projection is invalid", exception);
        }
    }

    private String booleanValue(String value) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalStateException("product export boolean projection is invalid");
        }
        return value;
    }

    private void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Product field cannot be exported");
        }
    }

    private ProductCategoryId categoryId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new ProductCategoryId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product category ID is invalid");
        }
    }

    private MemberId member(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Product owner is invalid");
        }
    }

    private void requireCategory(TenantId tenantId, ProductCategoryId categoryId) {
        if (categoryRepository.findActiveById(tenantId, categoryId).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Product category was not found");
        }
    }
}
