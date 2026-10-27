package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.ReceivableQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ReceivableListSummaryVO;
import io.github.archetom.crm.application.vo.ReceivableVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivableQuery;
import io.github.archetom.crm.domain.model.ReceivableSort;
import io.github.archetom.crm.domain.model.ReceivableSummary;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldValuePolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceivableQueryServiceImpl implements ReceivableQueryService {
    private final ReceivableRepository repository;
    private final ReceivablePlanRepository planRepository;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final SavedViewRepository savedViewRepository;
    private final ReceivableAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final ReceivableApplicationSupport support;
    private final ReceivableOutputSupport output;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ReceivableVO>> query(
            AuthenticatedCaller caller, ReceivableQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_QUERY, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private String keyword;

            @Override
            public void validate() {
                require(request, "Receivable query request");
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_READ,
                        ReceivableAccessMode.READ);
                keyword = SearchKeyword.optional(request.getKeyword())
                        .map(SearchKeyword::value).orElse(null);
            }

            @Override
            public Pager<ReceivableVO> execute() {
                QueryContext context = queryContext(caller, access, request, keyword);
                PageResult<Receivable> result = repository.findReceivables(access.tenantId(),
                        context.query());
                Pager<ReceivableVO> pager = pager(result.page(), result.size(), result.total());
                pager.setObjectList(output.receivables(caller, result.items(), access,
                        context.fieldAccess()));
                return pager;
            }
        });
    }

    @Override
    public Result<ReceivableListSummaryVO> summary(
            AuthenticatedCaller caller, ReceivableQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_SUMMARY,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private String keyword;

                    @Override
                    public void validate() {
                        require(request, "Receivable summary request");
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_READ,
                                ReceivableAccessMode.READ);
                        keyword = SearchKeyword.optional(request.getKeyword())
                                .map(SearchKeyword::value).orElse(null);
                    }

                    @Override
                    public ReceivableListSummaryVO execute() {
                        QueryContext context = queryContext(caller, access, request, keyword);
                        support.requireReadableUnmasked(context.fieldAccess(),
                                ReceivableFieldCatalog.AMOUNT);
                        ReceivableSummary result = repository.summarize(access.tenantId(),
                                context.query());
                        return new ReceivableListSummaryVO(result.count(), result.totalAmount());
                    }
                });
    }

    @Override
    public Result<ReceivableVO> get(AuthenticatedCaller caller, Long receivableIdValue) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_GET, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private ReceivableId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_READ,
                        ReceivableAccessMode.READ);
                id = id(receivableIdValue);
            }

            @Override
            public ReceivableVO execute() {
                Receivable value = repository.findVisibleById(access.tenantId(), id, access.visibility())
                        .orElseThrow(io.github.archetom.crm.domain.exception.ReceivableNotFoundException::new);
                return output.receivable(caller, value, access, support.accesses(
                        access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE));
            }
        });
    }

    private QueryContext queryContext(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess access,
            ReceivableQueryRequest request, String keyword) {
        List<FieldDefinition> definitions = support.fields(
                access.tenantId(), BusinessObjectType.RECEIVABLE);
        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE);
        return new QueryContext(query(caller, access, request, definitions, fieldAccess, keyword),
                fieldAccess);
    }

    private ReceivableQuery query(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess access,
            ReceivableQueryRequest request, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            String keyword) {
        MemberId owner = member(request.getOwnerActorId());
        if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly()) && owner != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable owner scenes are mutually exclusive");
        }
        if (owner != null || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
            support.requireReadableUnmasked(accesses, ReceivableFieldCatalog.OWNER_ACTOR);
        }
        CustomerId customer = customer(request.getCustomerId());
        if (customer != null) {
            support.requireReadableUnmasked(accesses, ReceivableFieldCatalog.CUSTOMER);
            requireCustomer(caller, access, customer);
        }
        ContractId contract = contract(request.getContractId());
        if (contract != null) {
            support.requireReadableUnmasked(accesses, ReceivableFieldCatalog.CONTRACT);
            requireContract(caller, access, contract);
        }
        ReceivablePlanId plan = plan(request.getReceivablePlanId());
        if (plan != null) {
            support.requireReadableUnmasked(accesses, ReceivableFieldCatalog.PLAN);
            requirePlan(caller, access, plan);
        }
        if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable static and custom sort accesses are mutually exclusive");
        }
        ReceivableSort sort;
        if (request.getCustomSortFieldCode() == null) {
            sort = receivableSort(request.getSortField(), request.getSortDirection());
        } else if (request.getSortDirection() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable custom sort must include direction");
        } else {
            sort = customReceivableSort(request.getCustomSortFieldCode(), request.getSortDirection(),
                    definitions, accesses);
        }
        if (!sort.isCustom() && !sort.isDefault()) {
            support.requireReadableUnmasked(accesses, sort.field().fieldCode().getValue());
        }
        var subordinateOwnerMemberIds = Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                ? subordinateOwnerResolver.resolve(access.tenantId(), access.memberId(),
                access.visibility().dataScope()) : null;
        List<FilterCondition> conditions = conditions(request.getSavedViewId(), request.getConditions(),
                caller, access, definitions, accesses);
        return new ReceivableQuery(page(request.getPage()), size(request.getSize()),
                keyword, FieldAccessPolicy.searchableCandidates(definitions, accesses,
                        ReceivableFieldCatalog.searchCandidates()), owner,
                subordinateOwnerMemberIds,
                customer, contract, plan, sort, conditions, access.visibility());
    }

    private ReceivableSort receivableSort(String field, String direction) {
        try {
            return ReceivableSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable sort is invalid");
        }
    }

    private ReceivableSort customReceivableSort(
            String requestedCode, String direction, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable custom sort field is invalid");
        }
        FieldDefinition field = definitions.stream()
                .filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || !field.isActive() || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable custom sort field is unavailable");
        }
        support.requireReadableUnmasked(accesses, code.getValue());
        try {
            return ReceivableSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable sort is invalid");
        }
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ReceivableAccessResolver.ReceivableAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (viewId != null && requested != null && !requested.isEmpty()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view and temporary filters cannot be combined");
        }
        if (viewId == null && requested != null && !requested.isEmpty()) {
            return temporaryConditions(requested, caller, access, fields, fieldAccess);
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
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.RECEIVABLE) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a receivable view");
        }
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(searchableTypes);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream().map(condition ->
                    ReceivableFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable saved view condition is invalid");
        }
        validateConditions(caller, access, fieldAccess, normalized);
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ReceivableAccessResolver.ReceivableAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        List<FilterCondition> normalized;
        try {
            List<FilterCondition> values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Receivable condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            normalized = values.stream().map(condition -> ReceivableFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable temporary filter condition is invalid");
        }
        validateConditions(caller, access, fieldAccess, normalized);
        return normalized;
    }

    private Map<FieldCode, FieldDefinition> searchableFields(List<FieldDefinition> fields) {
        return fields.stream().filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left));
    }

    private void validateConditions(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess,
            List<FilterCondition> conditions) {
        conditions.forEach(condition -> {
            support.requireReadableUnmasked(fieldAccess, condition.fieldCode().getValue());
            validateRelationCondition(caller, access, condition);
        });
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess access,
            FilterCondition condition) {
        String code = condition.fieldCode().getValue();
        if (!code.equals(ReceivableFieldCatalog.CUSTOMER)
                && !code.equals(ReceivableFieldCatalog.CONTRACT)
                && !code.equals(ReceivableFieldCatalog.PLAN)) {
            return;
        }
        if (condition.operator() == FilterOperator.IS_EMPTY
                || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable relation filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                long id = Long.parseLong(value);
                if (code.equals(ReceivableFieldCatalog.CUSTOMER)) {
                    requireCustomer(caller, access, new CustomerId(id));
                } else if (code.equals(ReceivableFieldCatalog.CONTRACT)) {
                    requireContract(caller, access, new ContractId(id));
                } else {
                    requirePlan(caller, access, new ReceivablePlanId(id));
                }
            } catch (IllegalArgumentException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Receivable relation filter ID is invalid");
            }
        }
    }

    private void requireCustomer(AuthenticatedCaller caller,
                                 ReceivableAccessResolver.ReceivableAccess receipt,
                                 CustomerId customer) {
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        if (!access.tenantId().sameValueAs(receipt.tenantId())
                || customerRepository.findVisibleById(access.tenantId(), customer,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Customer was not found");
        }
    }

    private void requireContract(AuthenticatedCaller caller,
                                 ReceivableAccessResolver.ReceivableAccess receipt,
                                 ContractId contract) {
        var access = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                ContractAccessMode.READ);
        if (!access.tenantId().sameValueAs(receipt.tenantId())
                || contractRepository.findVisibleById(access.tenantId(), contract,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Contract was not found");
        }
    }

    private void requirePlan(AuthenticatedCaller caller,
                             ReceivableAccessResolver.ReceivableAccess receipt,
                             ReceivablePlanId plan) {
        var access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                ReceivableAccessMode.READ);
        if (!access.tenantId().sameValueAs(receipt.tenantId())
                || planRepository.findVisibleById(access.tenantId(), plan,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Receivable plan was not found");
        }
    }

    private ReceivableId id(Long value) {
        try { return new ReceivableId(value); } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable ID is invalid");
        }
    }

    private CustomerId customer(Long value) {
        try { return value == null ? null : new CustomerId(value); } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Customer ID is invalid");
        }
    }

    private ContractId contract(Long value) {
        try { return value == null ? null : new ContractId(value); } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Contract ID is invalid");
        }
    }

    private ReceivablePlanId plan(Long value) {
        try { return value == null ? null : new ReceivablePlanId(value); } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable plan ID is invalid");
        }
    }

    private MemberId member(Long value) {
        try { return value == null ? null : new MemberId(value); } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Member ID is invalid");
        }
    }

    private int page(Integer value) { return value == null ? 1 : value; }
    private int size(Integer value) { return value == null ? 20 : value; }
    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>(); pager.setPageNum(page); pager.setPageSize(size); pager.setTotalNum(total); return pager;
    }
    private record QueryContext(
            ReceivableQuery query, Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
    }
    private void require(Object value, String description) {
        if (value == null) throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                description + " is required");
    }
}
