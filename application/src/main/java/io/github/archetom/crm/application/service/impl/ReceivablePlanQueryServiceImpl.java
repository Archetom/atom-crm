package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.application.export.BoundedCsvEncoder;
import io.github.archetom.crm.application.export.BoundedCsvEncoder.CsvLimitExceededException;
import io.github.archetom.crm.application.export.ReceivablePlanCsvDocument;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.ReceivablePlanQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ReceivablePlanVO;
import io.github.archetom.crm.application.vo.ReceivablePlanListSummaryVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReceivablePlanQuery;
import io.github.archetom.crm.domain.model.ReceivablePlanSummary;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.ReceivableFieldValuePolicy;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
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
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceivablePlanQueryServiceImpl implements ReceivablePlanQueryService {
    static final int EXPORT_MAX_ROWS = 5_000;
    private static final int EXPORT_PROBE_LIMIT = EXPORT_MAX_ROWS + 1;
    private static final int EXPORT_MAX_BYTES = 16 * 1024 * 1024;
    private final ReceivablePlanRepository repository;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final ReceivableRepository receivableRepository;
    private final SavedViewRepository savedViewRepository;
    private final ReceivableAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final ReceivableApplicationSupport support;
    private final ReceivableOutputSupport output;
    private final CustomerCsvExportAdmission csvExportAdmission;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ReceivablePlanVO>> query(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_QUERY, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private String keyword;

            @Override
            public void validate() {
                require(request, "Receivable plan query request");
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                        ReceivableAccessMode.READ);
                keyword = SearchKeyword.optional(request.getKeyword())
                        .map(SearchKeyword::value).orElse(null);
            }

            @Override
            public Pager<ReceivablePlanVO> execute() {
                QueryContext context = queryContext(caller, access, criteria(request), keyword);
                PageResult<ReceivablePlan> result = repository.findPlans(access.tenantId(),
                        context.query());
                Pager<ReceivablePlanVO> pager = pager(result.page(), result.size(), result.total());
                pager.setObjectList(output.plans(caller, result.items(), access, context.fieldAccess()));
                return pager;
            }
        });
    }

    @Override
    public Result<ReceivablePlanCsvDocument> export(
            AuthenticatedCaller caller, ReceivablePlanCsvExportRequest request) {
        ReceivableAccessResolver.ReceivableAccess readAccess;
        ReceivableAccessResolver.ReceivableAccess exportAccess;
        try {
            readAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                    ReceivableAccessMode.READ);
            exportAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_EXPORT,
                    ReceivableAccessMode.OWNER_SCOPE);
            if (!readAccess.tenantId().sameValueAs(exportAccess.tenantId())
                    || !readAccess.memberId().sameValueAs(exportAccess.memberId())) {
                return rejectExport(ApplicationErrorCode.ACCESS_DENIED,
                        "Receivable plan export access is unavailable");
            }
        } catch (ApplicationException exception) {
            return queryTemplate.reject(UseCaseOperation.RECEIVABLE_PLAN_CSV_EXPORT, exception);
        }
        CustomerCsvExportAdmission.Lease lease = csvExportAdmission.tryAcquire(
                readAccess.tenantId(), readAccess.memberId());
        if (lease == null) {
            return rejectExport(ApplicationErrorCode.CONCURRENT_OPERATION,
                    "Receivable plan export capacity is unavailable");
        }
        boolean documentOwnsLease = false;
        try {
            Result<ReceivablePlanCsvDocument> result = queryTemplate.execute(
                    UseCaseOperation.RECEIVABLE_PLAN_CSV_EXPORT, new ServiceOperation<>() {
                        private List<FieldCode> selectedFields;
                        private String keyword;

                        @Override
                        public void validate() {
                            require(request, "Receivable plan CSV export request");
                            selectedFields = exportFields(request);
                            keyword = SearchKeyword.optional(request.getKeyword())
                                    .map(SearchKeyword::value).orElse(null);
                        }

                        @Override
                        public ReceivablePlanCsvDocument execute() {
                            QueryContext context = queryContext(caller, readAccess, criteria(request), keyword);
                            selectedFields.forEach(field -> support.requireReadableUnmasked(
                                    context.fieldAccess(), field.getValue()));
                            List<ReceivablePlan> plans = repository.findExportPlans(
                                    readAccess.tenantId(), context.query(), exportAccess.visibility(),
                                    EXPORT_PROBE_LIMIT);
                            if (plans.size() > EXPORT_MAX_ROWS) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Receivable plan export exceeds the row limit");
                            }
                            try {
                                BoundedCsvEncoder encoder = new BoundedCsvEncoder(EXPORT_MAX_BYTES);
                                encoder.row(exportHeaders(selectedFields),
                                        formulaSafe(selectedFields.size() + 1));
                                appendExportRows(encoder, output.plans(caller, plans, readAccess,
                                        context.fieldAccess()), selectedFields);
                                return new ReceivablePlanCsvDocument(encoder.toByteArray(), lease);
                            } catch (CsvLimitExceededException exception) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Receivable plan export exceeds the byte limit");
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

    @Override
    public Result<ReceivablePlanListSummaryVO> summary(
            AuthenticatedCaller caller, ReceivablePlanQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_SUMMARY,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess planAccess;
                    private ReceivableAccessResolver.ReceivableAccess receivableAccess;
                    private String keyword;

                    @Override
                    public void validate() {
                        require(request, "Receivable plan summary request");
                        planAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                                ReceivableAccessMode.READ);
                        receivableAccess = accessResolver.access(caller, AuthorityCatalog.RECEIVABLES_READ,
                                ReceivableAccessMode.READ);
                        if (!planAccess.tenantId().sameValueAs(receivableAccess.tenantId())) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Receivable plan and receivable tenants do not match");
                        }
                        keyword = SearchKeyword.optional(request.getKeyword())
                                .map(SearchKeyword::value).orElse(null);
                    }

                    @Override
                    public ReceivablePlanListSummaryVO execute() {
                        QueryContext context = queryContext(caller, planAccess, criteria(request), keyword);
                        support.requireReadableUnmasked(context.fieldAccess(),
                                ReceivablePlanFieldCatalog.PLANNED_AMOUNT);
                        support.requireReadableUnmasked(support.accesses(
                                        receivableAccess.tenantId(), receivableAccess.memberId(),
                                        BusinessObjectType.RECEIVABLE),
                                io.github.archetom.crm.domain.policy.ReceivableFieldCatalog.AMOUNT);
                        ReceivablePlanSummary result = repository.summarize(planAccess.tenantId(),
                                context.query(), receivableAccess.visibility());
                        return new ReceivablePlanListSummaryVO(result.count(), result.plannedAmount(),
                                result.receivedAmount(), result.remainingAmount());
                    }
                });
    }

    @Override
    public Result<ReceivablePlanVO> get(AuthenticatedCaller caller, Long planIdValue) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_GET, new ServiceOperation<>() {
            private ReceivableAccessResolver.ReceivableAccess access;
            private ReceivablePlanId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                        ReceivableAccessMode.READ);
                id = id(planIdValue);
            }

            @Override
            public ReceivablePlanVO execute() {
                ReceivablePlan value = repository.findVisibleById(access.tenantId(), id, access.visibility())
                        .orElseThrow(io.github.archetom.crm.domain.exception.ReceivablePlanNotFoundException::new);
                return output.plan(caller, value, access, support.accesses(
                        access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN));
            }
        });
    }

    @Override
    public Result<List<ReceivablePlanVO>> available(AuthenticatedCaller caller, Long contractIdValue) {
        return queryTemplate.execute(UseCaseOperation.RECEIVABLE_PLAN_AVAILABLE_QUERY,
                new ServiceOperation<>() {
                    private ReceivableAccessResolver.ReceivableAccess access;
                    private ContractAccessResolver.ContractAccess contractAccess;
                    private ContractId contractId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.RECEIVABLE_PLANS_READ,
                                ReceivableAccessMode.READ);
                        contractAccess = contractAccessResolver.access(caller,
                                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                        contractId = contract(contractIdValue);
                    }

                    @Override
                    public List<ReceivablePlanVO> execute() {
                        if (!access.tenantId().sameValueAs(contractAccess.tenantId())
                                || contractRepository.findVisibleById(contractAccess.tenantId(), contractId,
                                contractAccess.visibility()).isEmpty()) {
                            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                    "Contract was not found");
                        }
                        Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
                        support.requireReadableUnmasked(fields, ReceivablePlanFieldCatalog.CONTRACT);
                        support.requireReadableUnmasked(fields, ReceivablePlanFieldCatalog.PLANNED_AMOUNT);
                        support.requireAnyReadableUnmasked(fields,
                                ReceivablePlanFieldCatalog.RECEIVED_AMOUNT,
                                ReceivablePlanFieldCatalog.REMAINING_AMOUNT,
                                ReceivablePlanFieldCatalog.PROGRESS);
                        List<ReceivablePlan> values = repository.findAvailableByContract(
                                access.tenantId(), contractId, access.visibility());
                        Map<ReceivablePlanId, java.math.BigDecimal> receivedAmounts =
                                receivableRepository.sumActiveAmountsByPlan(access.tenantId(),
                                        values.stream().map(ReceivablePlan::getId).toList());
                        return output.plans(values, receivedAmounts, fields,
                                values.isEmpty() ? false
                                        : output.customerVisible(caller, access, values.getFirst()), true);
                    }
                });
    }

    private QueryContext queryContext(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess access,
            QueryCriteria criteria, String keyword) {
        List<FieldDefinition> definitions = support.fields(
                access.tenantId(), BusinessObjectType.RECEIVABLE_PLAN);
        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                access.tenantId(), access.memberId(), BusinessObjectType.RECEIVABLE_PLAN);
        return new QueryContext(query(caller, access, criteria, definitions, fieldAccess, keyword),
                fieldAccess);
    }

    private ReceivablePlanQuery query(
            AuthenticatedCaller caller, ReceivableAccessResolver.ReceivableAccess access,
            QueryCriteria criteria, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            String keyword) {
        MemberId owner = member(criteria.ownerActorId());
        if (Boolean.TRUE.equals(criteria.subordinateOwnerOnly()) && owner != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan owner scenes are mutually exclusive");
        }
        if (owner != null || Boolean.TRUE.equals(criteria.subordinateOwnerOnly())) {
            support.requireReadableUnmasked(accesses, ReceivablePlanFieldCatalog.OWNER_ACTOR);
        }
        var subordinateOwnerMemberIds = Boolean.TRUE.equals(criteria.subordinateOwnerOnly())
                ? subordinateOwnerResolver.resolve(access.tenantId(), access.memberId(),
                access.visibility().dataScope()) : null;
        CustomerId customer = customer(criteria.customerId());
        if (customer != null) {
            support.requireReadableUnmasked(accesses, ReceivablePlanFieldCatalog.CUSTOMER);
            requireCustomer(caller, access, customer);
        }
        ContractId contract = contract(criteria.contractId());
        if (contract != null) {
            support.requireReadableUnmasked(accesses, ReceivablePlanFieldCatalog.CONTRACT);
            requireContract(caller, access, contract);
        }
        List<FilterCondition> conditions = conditions(criteria.savedViewId(), criteria.conditions(),
                caller, access, definitions, accesses);
        return new ReceivablePlanQuery(page(criteria.page()), size(criteria.size()),
                keyword, FieldAccessPolicy.searchableCandidates(definitions, accesses,
                        ReceivablePlanFieldCatalog.searchCandidates()), owner,
                subordinateOwnerMemberIds, customer, contract, conditions, access.visibility());
    }

    private static QueryCriteria criteria(ReceivablePlanQueryRequest request) {
        return new QueryCriteria(request.getPage(), request.getSize(), request.getOwnerActorId(),
                request.getSubordinateOwnerOnly(), request.getCustomerId(), request.getContractId(),
                request.getSavedViewId(), request.getConditions());
    }

    private static QueryCriteria criteria(ReceivablePlanCsvExportRequest request) {
        return new QueryCriteria(null, null, request.getOwnerActorId(),
                request.getSubordinateOwnerOnly(), request.getCustomerId(), request.getContractId(),
                request.getSavedViewId(), request.getConditions());
    }

    private record QueryCriteria(
            Integer page,
            Integer size,
            Long ownerActorId,
            Boolean subordinateOwnerOnly,
            Long customerId,
            Long contractId,
            Long savedViewId,
            List<FilterConditionRequest> conditions) {
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
        if (view.getObjectType() != BusinessObjectType.RECEIVABLE_PLAN) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a receivable plan view");
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
                    "Receivable plan saved view condition is invalid");
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
                    throw new IllegalArgumentException("Receivable plan condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            normalized = values.stream().map(condition -> ReceivableFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan temporary filter condition is invalid");
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
        if (!code.equals(ReceivablePlanFieldCatalog.CUSTOMER)
                && !code.equals(ReceivablePlanFieldCatalog.CONTRACT)) {
            return;
        }
        if (condition.operator() == FilterOperator.IS_EMPTY
                || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan relation filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                long id = Long.parseLong(value);
                if (code.equals(ReceivablePlanFieldCatalog.CUSTOMER)) {
                    requireCustomer(caller, access, new CustomerId(id));
                } else {
                    requireContract(caller, access, new ContractId(id));
                }
            } catch (IllegalArgumentException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Receivable plan relation filter ID is invalid");
            }
        }
    }

    private void requireCustomer(AuthenticatedCaller caller,
                                 ReceivableAccessResolver.ReceivableAccess plan,
                                 CustomerId customer) {
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        if (!access.tenantId().sameValueAs(plan.tenantId())
                || customerRepository.findVisibleById(access.tenantId(), customer,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Customer was not found");
        }
    }

    private void requireContract(AuthenticatedCaller caller,
                                 ReceivableAccessResolver.ReceivableAccess plan,
                                 ContractId contract) {
        var access = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                ContractAccessMode.READ);
        if (!access.tenantId().sameValueAs(plan.tenantId())
                || contractRepository.findVisibleById(access.tenantId(), contract,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "Contract was not found");
        }
    }

    private Result<ReceivablePlanCsvDocument> rejectExport(
            ApplicationErrorCode code, String message) {
        return queryTemplate.reject(UseCaseOperation.RECEIVABLE_PLAN_CSV_EXPORT,
                support.error(code, message));
    }

    private List<FieldCode> exportFields(ReceivablePlanCsvExportRequest request) {
        if (request.getFieldCodes() == null || request.getFieldCodes().isEmpty()
                || request.getFieldCodes().size() > ReceivablePlanFieldCatalog.codes().size()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan export fields are invalid");
        }
        List<FieldCode> fields = new ArrayList<>();
        try {
            for (String code : request.getFieldCodes()) {
                FieldCode field = new FieldCode(code);
                if (!ReceivablePlanFieldCatalog.codes().contains(field)) {
                    throw new IllegalArgumentException("field is not exportable");
                }
                fields.add(field);
            }
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan export fields are invalid");
        }
        if (new LinkedHashSet<>(fields).size() != fields.size()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Receivable plan export fields are duplicated");
        }
        return List.copyOf(fields);
    }

    private List<String> exportHeaders(List<FieldCode> fields) {
        List<String> values = new ArrayList<>(fields.size() + 1);
        values.add("receivable_plan_id");
        fields.forEach(field -> values.add(field.getValue()));
        return values;
    }

    private List<Boolean> formulaSafe(int size) {
        List<Boolean> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(true);
        }
        return values;
    }

    private void appendExportRows(
            BoundedCsvEncoder encoder, List<ReceivablePlanVO> plans, List<FieldCode> fields) {
        for (ReceivablePlanVO plan : plans) {
            if (plan == null || plan.id() == null) {
                throw new IllegalStateException("receivable plan export projection is invalid");
            }
            List<String> values = new ArrayList<>(fields.size() + 1);
            values.add(Long.toString(plan.id()));
            fields.forEach(field -> values.add(exportValue(plan, field)));
            encoder.row(values, formulaSafe(values.size()));
        }
    }

    private String exportValue(ReceivablePlanVO plan, FieldCode field) {
        return switch (field.getValue()) {
            case ReceivablePlanFieldCatalog.NUMBER -> string(plan.installment());
            case ReceivablePlanFieldCatalog.CUSTOMER -> string(plan.customerId());
            case ReceivablePlanFieldCatalog.CONTRACT -> string(plan.contractId());
            case ReceivablePlanFieldCatalog.DUE_DATE -> string(plan.dueDate());
            case ReceivablePlanFieldCatalog.PLANNED_AMOUNT -> decimal(plan.plannedAmount());
            case ReceivablePlanFieldCatalog.PAYMENT_METHOD -> plan.paymentMethod();
            case ReceivablePlanFieldCatalog.REMINDER_DAYS -> string(plan.reminderDays());
            case ReceivablePlanFieldCatalog.REMARK -> plan.remark();
            case ReceivablePlanFieldCatalog.OWNER_ACTOR -> string(plan.ownerActorId());
            case ReceivablePlanFieldCatalog.RECEIVED_AMOUNT -> decimal(plan.receivedAmount());
            case ReceivablePlanFieldCatalog.REMAINING_AMOUNT -> decimal(plan.remainingAmount());
            case ReceivablePlanFieldCatalog.PROGRESS -> plan.progress();
            case ReceivablePlanFieldCatalog.STATUS -> plan.status();
            case ReceivablePlanFieldCatalog.CREATED_TIME -> string(plan.createdTime());
            case ReceivablePlanFieldCatalog.UPDATED_TIME -> string(plan.updatedTime());
            default -> throw new IllegalStateException("receivable plan export field is invalid");
        };
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private ReceivablePlanId id(Long value) {
        try { return new ReceivablePlanId(value); } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Receivable plan ID is invalid");
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
            ReceivablePlanQuery query, Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
    }
    private void require(Object value, String description) {
        if (value == null) throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                description + " is required");
    }
}
