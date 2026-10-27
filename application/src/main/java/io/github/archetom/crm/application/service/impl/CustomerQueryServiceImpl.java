package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.CustomerQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CustomerCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.CustomerCollaboratorVO;
import io.github.archetom.crm.application.vo.CustomerHierarchyVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.application.vo.NearbyCustomerVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.CustomerNotFoundException;
import io.github.archetom.crm.domain.model.ActivitySubjectSummary;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerDistance;
import io.github.archetom.crm.domain.model.CustomerNearbyQuery;
import io.github.archetom.crm.domain.model.CustomerHierarchyStats;
import io.github.archetom.crm.domain.model.CustomerQuery;
import io.github.archetom.crm.domain.model.CustomerSort;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldValuePolicy;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.CustomerStarRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerQueryServiceImpl implements CustomerQueryService {

    private static final int MAX_HIERARCHY = CustomerHierarchyStats.MAX_TREE_NODES;

    private final CustomerRepository customerRepository;
    private final CustomerStarRepository starRepository;
    private final ActivityRepository activityRepository;
    private final SavedViewRepository savedViewRepository;
    private final CustomerAccessResolver accessResolver;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final CustomerApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<CustomerVO>> query(
            AuthenticatedCaller caller, CustomerQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_QUERY, new ServiceOperation<>() {
            private CustomerAccess access;
            private int page;
            private int size;
            private String name;
            private SearchKeyword keyword;
            private CustomerSort sort;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                        CustomerAccessMode.READ);
                requireRequest(request, "Customer query request");
                page = page(request.getPage());
                size = size(request.getSize());
                name = normalize(request.getName());
                keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
                if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                        && request.getOwnerActorId() != null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Customer owner scenes are mutually exclusive");
                }
                if (request.getSavedViewId() != null && request.getConditions() != null
                        && !request.getConditions().isEmpty()) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Customer saved and temporary filters are mutually exclusive");
                }
                if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Customer static and custom sort fields are mutually exclusive");
                }
                if (request.getCustomSortFieldCode() == null) {
                    sort = customerSort(request.getSortField(), request.getSortDirection());
                } else if (request.getSortDirection() == null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Customer custom sort must include direction");
                }
            }

            @Override
            public Pager<CustomerVO> execute() {
                List<FieldDefinition> fields = support.fields(access.tenantId()).stream()
                        .filter(FieldDefinition::isActive).toList();
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                        access.tenantId(), access.memberId());
                if (name != null) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(CustomerFieldCatalog.NAME));
                }
                if (request.getOwnerActorId() != null
                        || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(CustomerFieldCatalog.OWNER_ACTOR));
                }
                if (request.getCustomSortFieldCode() != null) {
                    sort = customCustomerSort(request.getCustomSortFieldCode(),
                            request.getSortDirection(), fields, fieldAccess);
                } else if (!sort.isDefault()) {
                    requireReadableUnmasked(fieldAccess, sort.field().fieldCode());
                }
                List<FilterCondition> conditions = conditions(request.getSavedViewId(),
                        request.getConditions(),
                        access, fields, fieldAccess);
                PageResult<Customer> result = customerRepository.findCustomers(access.tenantId(),
                        new CustomerQuery(access.visibility(),
                                keyword == null ? null : keyword.value(),
                                FieldAccessPolicy.searchableCandidates(fields, fieldAccess,
                                        CustomerFieldCatalog.searchCandidates()), name,
                                request.getOwnerActorId() == null ? null
                                        : new io.github.archetom.crm.domain.valueobject.MemberId(
                                                request.getOwnerActorId()),
                                Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                                        ? subordinateOwnerResolver.resolve(access.tenantId(),
                                        access.memberId(), access.visibility().dataScope()) : null,
                                request.getStarred(), sort, conditions, page, size));
                return customerPager(result, caller, access, fieldAccess);
            }
        });
    }

    @Override
    public Result<CustomerVO> get(AuthenticatedCaller caller, Long customerId) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_GET, new ServiceOperation<>() {
            private CustomerAccess access;
            private CustomerId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                        CustomerAccessMode.READ);
                id = id(customerId);
            }

            @Override
            public CustomerVO execute() {
                Customer customer = requireVisible(access, id);
                return customerVOs(List.of(customer), caller, access,
                        support.accesses(access.tenantId(), access.memberId())).getFirst();
            }
        });
    }

    @Override
    public Result<CustomerCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long customerId) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_COLLABORATOR_GET,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_ASSIGN,
                                CustomerAccessMode.OWNER_SCOPE);
                        id = id(customerId);
                    }

                    @Override
                    public CustomerCollaboratorBaselineVO execute() {
                        Customer customer = requireVisible(access, id);
                        String callerState = customer.getOwnerMemberId() != null
                                && customer.getOwnerMemberId().sameValueAs(access.memberId())
                                ? "OWNER" : customer.getCollaborators().stream()
                                .filter(value -> value.memberId().sameValueAs(access.memberId()))
                                .map(value -> value.access().name()).findFirst().orElse("NONE");
                        List<CustomerCollaboratorVO> collaborators = customer.getCollaborators().stream()
                                .map(value -> new CustomerCollaboratorVO(
                                        value.memberId().getValue(), value.access().name())).toList();
                        return new CustomerCollaboratorBaselineVO(customer.getVersion(), collaborators,
                                callerState);
                    }
                });
    }

    @Override
    public Result<CustomerHierarchyVO> hierarchy(
            AuthenticatedCaller caller, Long customerId) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_HIERARCHY_GET,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                        id = id(customerId);
                    }

                    @Override
                    public CustomerHierarchyVO execute() {
                        Customer root = requireVisible(access, id);
                        List<Customer> descendants = customerRepository.findVisibleDescendants(
                                access.tenantId(), id, access.visibility(), MAX_HIERARCHY);
                        List<Customer> ancestors = customerRepository.findVisibleAncestors(
                                access.tenantId(), id, access.visibility(), 100);
                        Map<CustomerId, Customer> visibleById = new LinkedHashMap<>();
                        visibleById.put(root.getId(), root);
                        descendants.forEach(value -> visibleById.put(value.getId(), value));
                        ancestors.forEach(value -> visibleById.put(value.getId(), value));
                        List<Customer> visible = List.copyOf(visibleById.values());
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        List<CustomerVO> customers = customerVOs(visible.stream()
                                .sorted(Comparator.comparing(value -> value.getId().getValue()))
                                .toList(), caller, access, fieldAccess);
                        return new CustomerHierarchyVO(id.getValue(), customers);
                    }
                });
    }

    @Override
    public Result<Boolean> duplicateCheck(
            AuthenticatedCaller caller, CustomerDuplicateCheckRequest request) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_DUPLICATE_CHECK,
                new ServiceOperation<>() {
                    private CustomerAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                        requireRequest(request, "Customer duplicate request");
                        if (blank(request.getName()) && blank(request.getPhoneNumber())
                                && (request.getCustomFields() == null
                                || request.getCustomFields().isEmpty())) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "At least one duplicate-check value is required");
                        }
                    }

                    @Override
                    public Boolean execute() {
                        CustomerId excludedId = request.getExcludedCustomerId() == null
                                ? null : id(request.getExcludedCustomerId());
                        if (excludedId != null) {
                            requireVisible(access, excludedId);
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        if (!blank(request.getName())) {
                            requireReadableUnmasked(fieldAccess, new FieldCode(CustomerFieldCatalog.NAME));
                        }
                        if (!blank(request.getPhoneNumber())) {
                            requireReadableUnmasked(fieldAccess,
                                    new FieldCode(CustomerFieldCatalog.PHONE_NUMBER));
                        }
                        Map<FieldCode, String> unique = uniqueValues(
                                request.getCustomFields(), byCode, fieldAccess);
                        return customerRepository.existsDuplicate(access.tenantId(),
                                access.visibility(), excludedId, normalize(request.getName()),
                                normalize(request.getPhoneNumber()), unique);
                    }
                });
    }

    @Override
    public Result<Pager<NearbyCustomerVO>> nearby(
            AuthenticatedCaller caller, BigDecimal longitude, BigDecimal latitude,
            Integer radiusMeters, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_NEARBY_QUERY,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private int resolvedPage;
                    private int resolvedSize;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                        resolvedPage = page(page);
                        resolvedSize = size(size);
                    }

                    @Override
                    public Pager<NearbyCustomerVO> execute() {
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireReadableUnmasked(fieldAccess,
                                new FieldCode(CustomerFieldCatalog.ADDRESSES));
                        PageResult<CustomerDistance> result = customerRepository.findNearby(
                                access.tenantId(), new CustomerNearbyQuery(access.visibility(),
                                        longitude, latitude, radiusMeters == null ? 0 : radiusMeters,
                                        resolvedPage, resolvedSize));
                        List<Customer> customers = result.items().stream()
                                .map(CustomerDistance::customer).toList();
                        Map<Long, CustomerVO> customerById = customerVOs(customers, caller, access,
                                fieldAccess).stream().collect(Collectors.toMap(CustomerVO::id,
                                        Function.identity()));
                        Pager<NearbyCustomerVO> pager = pager(result.page(), result.size(), result.total());
                        pager.setObjectList(result.items().stream().map(value -> new NearbyCustomerVO(
                                customerById.get(value.customer().getId().getValue()),
                                value.distanceMeters())).toList());
                        return pager;
                    }
                });
    }

    @Override
    public Result<List<CustomerVO>> summaries(
            AuthenticatedCaller caller, List<Long> ids) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_SUMMARY_LIST,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private List<CustomerId> customerIds;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                        if (ids == null || ids.isEmpty() || ids.size() > 100) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Customer summary IDs are invalid");
                        }
                        customerIds = ids.stream().map(CustomerQueryServiceImpl.this::id)
                                .distinct().toList();
                    }

                    @Override
                    public List<CustomerVO> execute() {
                        List<Customer> customers = customerRepository.findVisibleByIds(
                                access.tenantId(), customerIds, access.visibility(), 100);
                        return customerVOs(customers, caller, access,
                                support.accesses(access.tenantId(), access.memberId()));
                    }
                });
    }

    @Override
    public Result<List<String>> tags(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_TAG_LIST,
                new ServiceOperation<>() {
                    private CustomerAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                    }

                    @Override
                    public List<String> execute() {
                        requireReadableUnmasked(support.accesses(
                                        access.tenantId(), access.memberId()),
                                new FieldCode(CustomerFieldCatalog.TAGS));
                        return customerRepository.findVisibleTags(
                                access.tenantId(), access.visibility(), 200);
                    }
                });
    }

    private Pager<CustomerVO> customerPager(
            PageResult<Customer> result, AuthenticatedCaller caller, CustomerAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Pager<CustomerVO> pager = pager(result.page(), result.size(), result.total());
        pager.setObjectList(customerVOs(result.items(), caller, access, fieldAccess));
        return pager;
    }

    private List<CustomerVO> customerVOs(
            List<Customer> customers, AuthenticatedCaller caller, CustomerAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Set<CustomerId> visibleRelations = visibleRelations(customers, access);
        Set<CustomerId> starred = starRepository.findStarred(
                access.tenantId(), access.memberId(), customers.stream().map(Customer::getId).toList());
        Map<Long, ActivitySubjectSummary> latestActivities = latestActivities(caller, access, customers);
        return customers.stream().map(customer -> support.toVO(customer,
                starred.contains(customer.getId()), fieldAccess, visibleRelations,
                latestActivityTime(latestActivities.get(customer.getId().getValue())),
                latestActivityContent(latestActivities.get(customer.getId().getValue())))).toList();
    }

    private Map<Long, ActivitySubjectSummary> latestActivities(
            AuthenticatedCaller caller, CustomerAccess access, List<Customer> customers) {
        if (!caller.hasAuthority(AuthorityCatalog.ACTIVITIES_READ) || customers.isEmpty()) {
            return Map.of();
        }
        return activityRepository.findLatestBySubjects(access.tenantId(), BusinessObjectType.CUSTOMER,
                        customers.stream().map(value -> value.getId().getValue()).toList())
                .stream().collect(Collectors.toMap(ActivitySubjectSummary::subjectId,
                        Function.identity()));
    }

    private static LocalDateTime latestActivityTime(ActivitySubjectSummary value) {
        return value == null ? null : value.occurredTime();
    }

    private static String latestActivityContent(ActivitySubjectSummary value) {
        return value == null ? null : value.content();
    }

    private Set<CustomerId> visibleRelations(
            Collection<Customer> customers, CustomerAccess access) {
        Set<CustomerId> parentIds = customers.stream().map(Customer::getParentCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return customerRepository.findVisibleByIds(
                        access.tenantId(), parentIds, access.visibility(), Math.min(1_000, parentIds.size()))
                .stream().map(Customer::getId).collect(Collectors.toUnmodifiableSet());
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, CustomerAccess access,
            List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (viewId == null && requested != null && !requested.isEmpty()) {
            return temporaryConditions(requested, fields, fieldAccess);
        }
        if (viewId == null) {
            return List.of();
        }
        SavedView view;
        try {
            view = savedViewRepository.findById(access.tenantId(), access.memberId(),
                            new SavedViewId(viewId))
                    .orElseThrow(() -> support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.CUSTOMER) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a customer view");
        }
        Map<FieldCode, CustomFieldType> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        FieldDefinition::getFieldType, (left, right) -> left));
        view.assertApplicableTo(searchable);
        view.getConditions().forEach(condition ->
                requireReadableUnmasked(fieldAccess, condition.fieldCode()));
        return view.getConditions();
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        List<FilterCondition> values;
        try {
            values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Customer condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            values = values.stream().map(condition -> CustomerFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer temporary filter condition is invalid");
        }
        values.forEach(condition -> requireReadableUnmasked(fieldAccess, condition.fieldCode()));
        return values;
    }

    private Map<FieldCode, String> uniqueValues(
            Map<String, List<String>> requested,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<FieldCode, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : requested.entrySet()) {
            FieldCode code;
            try {
                code = new FieldCode(entry.getKey());
            } catch (RuntimeException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer field code is invalid");
            }
            FieldDefinition field = fields.get(code);
            if (field == null || !field.isActive() || !field.isUnique() || field.isBuiltin()) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer duplicate field is invalid");
            }
            requireReadableUnmasked(fieldAccess, code);
            CustomerFieldValue value = CustomerFieldValuePolicy.normalize(field, entry.getValue());
            if (value != null) {
                values.put(code, value.values().getFirst());
            }
        }
        return Map.copyOf(values);
    }

    private void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Customer field cannot be used for this query");
        }
    }

    private CustomerSort customerSort(String field, String direction) {
        try {
            return CustomerSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Customer sort is invalid");
        }
    }

    private CustomerSort customCustomerSort(
            String requestedCode, String direction, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer custom sort field is invalid");
        }
        FieldDefinition field = fields.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer custom sort field is unavailable");
        }
        requireReadableUnmasked(fieldAccess, code);
        try {
            return CustomerSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Customer sort is invalid");
        }
    }

    private Customer requireVisible(CustomerAccess access, CustomerId id) {
        return customerRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(CustomerNotFoundException::new);
    }

    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        return pager;
    }

    private CustomerId id(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }

    private int page(Integer value) {
        int resolved = value == null ? QueryRequest.DEFAULT_PAGE : value;
        if (resolved < 1 || resolved > QueryRequest.MAX_PAGE) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Page is outside the supported range");
        }
        return resolved;
    }

    private int size(Integer value) {
        int resolved = value == null ? QueryRequest.DEFAULT_SIZE : value;
        if (resolved < 1 || resolved > QueryRequest.MAX_SIZE) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Page size is outside the supported range");
        }
        return resolved;
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim();
    }
}
