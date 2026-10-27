package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContractBatchCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.export.BoundedCsvEncoder;
import io.github.archetom.crm.application.export.BoundedCsvEncoder.CsvLimitExceededException;
import io.github.archetom.crm.application.export.ContractCsvDocument;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver.ContractAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.ContractQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ContractVO;
import io.github.archetom.crm.application.vo.ContractCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.ContractCollaboratorVO;
import io.github.archetom.crm.application.vo.ContractListSummaryVO;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractListSummary;
import io.github.archetom.crm.domain.model.ContractQuery;
import io.github.archetom.crm.domain.model.ContractSort;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldValuePolicy;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractQueryServiceImpl implements ContractQueryService {
    private static final int SELECTED_EXPORT_MAX_ROWS = 100;
    private static final int SELECTED_EXPORT_MAX_BYTES = 16 * 1024 * 1024;
    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final OpportunityRepository opportunityRepository;
    private final ContractAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final SavedViewRepository savedViewRepository;
    private final ContractApplicationSupport support;
    private final ProductApplicationSupport productSupport;
    private final ReceivableApplicationSupport receivableSupport;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final CustomerCsvExportAdmission csvExportAdmission;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ContractVO>> query(
            AuthenticatedCaller caller, ContractQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CONTRACT_QUERY,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private String keyword;

                    @Override
                    public void validate() {
                        require(request, "Contract query request");
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                                ContractAccessMode.READ);
                        keyword = SearchKeyword.optional(request.getKeyword())
                                .map(SearchKeyword::value).orElse(null);
                    }

                    @Override
                    public Pager<ContractVO> execute() {
                        QueryContext context = queryContext(caller, access, request, keyword);
                        PageResult<Contract> result = contractRepository.findContracts(
                                access.tenantId(), context.query());
                        List<ContractVO> values = values(caller, access, result.items(),
                                context.fieldAccess());
                        Pager<ContractVO> pager = pager(result.page(), result.size(), result.total());
                        pager.setObjectList(values);
                        return pager;
                    }
                });
    }

    @Override
    public Result<ContractCsvDocument> exportSelected(
            AuthenticatedCaller caller, ContractBatchCsvExportRequest request) {
        ContractAccess readAccess;
        ContractAccess exportAccess;
        try {
            readAccess = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                    ContractAccessMode.READ);
            exportAccess = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_EXPORT,
                    ContractAccessMode.OWNER_SCOPE);
            if (!readAccess.tenantId().sameValueAs(exportAccess.tenantId())
                    || !readAccess.memberId().sameValueAs(exportAccess.memberId())) {
                return rejectSelectedExport(ApplicationErrorCode.ACCESS_DENIED,
                        "Contract export access is unavailable");
            }
        } catch (ApplicationException exception) {
            return queryTemplate.reject(UseCaseOperation.CONTRACT_CSV_EXPORT, exception);
        }
        CustomerCsvExportAdmission.Lease lease = csvExportAdmission.tryAcquire(
                readAccess.tenantId(), readAccess.memberId());
        if (lease == null) {
            return rejectSelectedExport(ApplicationErrorCode.CONCURRENT_OPERATION,
                    "Contract export capacity is unavailable");
        }
        boolean documentOwnsLease = false;
        try {
            Result<ContractCsvDocument> result = queryTemplate.execute(
                    UseCaseOperation.CONTRACT_CSV_EXPORT, new ServiceOperation<>() {
                        @Override
                        public void validate() {
                            selectedExportTargets(request);
                        }

                        @Override
                        public ContractCsvDocument execute() {
                            List<SelectedExportTarget> targets = selectedExportTargets(request);
                            List<ContractId> ids = targets.stream()
                                    .map(SelectedExportTarget::id).toList();
                            Map<ContractId, Contract> readable = contractsById(
                                    contractRepository.findVisibleByIds(readAccess.tenantId(), ids,
                                            readAccess.visibility(), SELECTED_EXPORT_MAX_ROWS));
                            if (readable.size() != targets.size()) {
                                throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                        "Selected contract is no longer available");
                            }
                            Map<ContractId, Contract> exportable = contractsById(
                                    contractRepository.findVisibleByIds(exportAccess.tenantId(), ids,
                                            exportAccess.visibility(), SELECTED_EXPORT_MAX_ROWS));
                            if (exportable.size() != targets.size()) {
                                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                                        "Contract export scope is unavailable");
                            }
                            List<Contract> contracts = targets.stream().map(target -> {
                                Contract contract = exportable.get(target.id());
                                if (contract == null || contract.getVersion() != target.version()) {
                                    throw support.error(ApplicationErrorCode.VERSION_CONFLICT,
                                            "Selected contract version is stale");
                                }
                                return contract;
                            }).toList();
                            List<SelectedExportColumn> columns = selectedExportColumns(
                                    support.accesses(readAccess.tenantId(), readAccess.memberId()));
                            try {
                                BoundedCsvEncoder encoder = new BoundedCsvEncoder(
                                        SELECTED_EXPORT_MAX_BYTES);
                                encoder.row(columns.stream().map(SelectedExportColumn::header).toList(),
                                        columns.stream().map(SelectedExportColumn::formulaSafe).toList());
                                for (Contract contract : contracts) {
                                    encoder.row(columns.stream().map(column ->
                                                    column.value().apply(contract)).toList(),
                                            columns.stream().map(SelectedExportColumn::formulaSafe).toList());
                                }
                                return new ContractCsvDocument(encoder.toByteArray(), lease);
                            } catch (CsvLimitExceededException exception) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Contract export exceeds the byte limit");
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
    public Result<ContractListSummaryVO> summary(
            AuthenticatedCaller caller, ContractQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CONTRACT_SUMMARY,
                new ServiceOperation<>() {
                    private ContractAccess contractAccess;
                    private ReceivableAccessResolver.ReceivableAccess receivableAccess;
                    private String keyword;

                    @Override
                    public void validate() {
                        require(request, "Contract summary request");
                        contractAccess = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                                ContractAccessMode.READ);
                        receivableAccess = receivableAccessResolver.access(caller,
                                AuthorityCatalog.RECEIVABLES_READ,
                                io.github.archetom.crm.domain.model.ReceivableAccessMode.READ);
                        if (!contractAccess.tenantId().sameValueAs(receivableAccess.tenantId())) {
                            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Contract and receivable access scopes do not match");
                        }
                        keyword = SearchKeyword.optional(request.getKeyword())
                                .map(SearchKeyword::value).orElse(null);
                    }

                    @Override
                    public ContractListSummaryVO execute() {
                        QueryContext context = queryContext(caller, contractAccess, request, keyword);
                        support.requireReadableUnmasked(context.fieldAccess(),
                                ContractFieldCatalog.TOTAL_AMOUNT);
                        receivableSupport.requireReadableUnmasked(receivableSupport.accesses(
                                        receivableAccess.tenantId(), receivableAccess.memberId(),
                                        BusinessObjectType.RECEIVABLE),
                                ReceivableFieldCatalog.AMOUNT);
                        ContractListSummary result = contractRepository.summarize(
                                contractAccess.tenantId(), context.query(),
                                receivableAccess.visibility());
                        return new ContractListSummaryVO(result.count(), result.totalAmount(),
                                result.receivedAmount(), result.outstandingAmount());
                    }
                });
    }

    @Override
    public Result<ContractVO> get(AuthenticatedCaller caller, Long contractIdValue) {
        return queryTemplate.execute(UseCaseOperation.CONTRACT_GET,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                                ContractAccessMode.READ);
                        id = contractId(contractIdValue);
                    }

                    @Override
                    public ContractVO execute() {
                        Contract contract = contractRepository.findVisibleById(
                                        access.tenantId(), id, access.visibility())
                                .orElseThrow(io.github.archetom.crm.domain.exception
                                        .ContractNotFoundException::new);
                        return values(caller, access, List.of(contract),
                                support.accesses(access.tenantId(), access.memberId())).getFirst();
                    }
                });
    }

    @Override
    public Result<ContractCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long contractIdValue) {
        return queryTemplate.execute(UseCaseOperation.CONTRACT_COLLABORATOR_GET,
                new ServiceOperation<>() {
                    private ContractAccess access;
                    private ContractId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTRACTS_ASSIGN,
                                ContractAccessMode.OWNER_SCOPE);
                        id = contractId(contractIdValue);
                    }

                    @Override
                    public ContractCollaboratorBaselineVO execute() {
                        Contract contract = contractRepository.findVisibleById(
                                        access.tenantId(), id, access.visibility())
                                .orElseThrow(io.github.archetom.crm.domain.exception
                                        .ContractNotFoundException::new);
                        String callerState = contract.getOwnerMemberId().sameValueAs(access.memberId())
                                ? "OWNER" : contract.getCollaborators().stream()
                                .filter(value -> value.memberId().sameValueAs(access.memberId()))
                                .filter(value -> value.isEffectiveOn(LocalDate.now()))
                                .map(value -> value.access().name()).findFirst().orElse("NONE");
                        List<ContractCollaboratorVO> collaborators = contract.getCollaborators().stream()
                                .map(value -> new ContractCollaboratorVO(
                                        value.memberId().getValue(), value.access().name(),
                                        value.effectiveUntil()))
                                .toList();
                        return new ContractCollaboratorBaselineVO(contract.getVersion(),
                                collaborators, callerState);
                    }
                });
    }

    private QueryContext queryContext(
            AuthenticatedCaller caller, ContractAccess access,
            ContractQueryRequest request, String keyword) {
        List<FieldDefinition> definitions = support.fields(access.tenantId());
        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                access.tenantId(), access.memberId());
        return new QueryContext(query(caller, access, request, definitions, fieldAccess, keyword),
                fieldAccess);
    }

    private ContractQuery query(
            AuthenticatedCaller caller, ContractAccess access,
            ContractQueryRequest request,
            List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses, String keyword) {
        MemberId owner = memberId(request.getOwnerActorId());
        if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly()) && owner != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract owner scenes are mutually exclusive");
        }
        if (owner != null || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
            support.requireReadableUnmasked(accesses, ContractFieldCatalog.OWNER_ACTOR);
        }
        CustomerId customer = customerId(request.getCustomerId());
        if (customer != null) {
            support.requireReadableUnmasked(accesses, ContractFieldCatalog.CUSTOMER);
            requireVisibleCustomer(caller, customer);
        }
        OpportunityId opportunity = opportunityId(request.getOpportunityId());
        if (opportunity != null) {
            support.requireReadableUnmasked(accesses, ContractFieldCatalog.OPPORTUNITY);
            requireVisibleOpportunity(caller, opportunity);
        }
        ContractStatus status = request.getStatus() == null ? null
                : support.parse(ContractStatus.class, request.getStatus(), "Contract status");
        if (status == ContractStatus.DELETED) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Deleted contracts cannot be queried");
        }
        if (status != null) {
            support.requireReadableUnmasked(accesses, ContractFieldCatalog.STATUS);
        }
        if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract static and custom sort accesses are mutually exclusive");
        }
        ContractSort sort;
        if (request.getCustomSortFieldCode() == null) {
            sort = contractSort(request.getSortField(), request.getSortDirection());
        } else if (request.getSortDirection() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract custom sort must include direction");
        } else {
            sort = customContractSort(request.getCustomSortFieldCode(), request.getSortDirection(),
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
        return new ContractQuery(access.visibility(), keyword,
                FieldAccessPolicy.searchableCandidates(definitions, accesses,
                        ContractFieldCatalog.searchCandidates()), owner,
                subordinateOwnerMemberIds,
                customer, opportunity, status, sort, conditions,
                page(request.getPage()), size(request.getSize()));
    }

    private ContractSort contractSort(String field, String direction) {
        try {
            return ContractSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Contract sort is invalid");
        }
    }

    private ContractSort customContractSort(
            String requestedCode, String direction, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract custom sort field is invalid");
        }
        FieldDefinition field = definitions.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || !field.isActive() || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract custom sort field is unavailable");
        }
        support.requireReadableUnmasked(accesses, code.getValue());
        try {
            return ContractSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Contract sort is invalid");
        }
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ContractAccess access, List<FieldDefinition> fields,
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
        if (view.getObjectType() != BusinessObjectType.CONTRACT) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a contract view");
        }
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(searchableTypes);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream().map(condition ->
                    ContractFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract saved view condition is invalid");
        }
        validateConditions(caller, access, fieldAccess, normalized);
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ContractAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        List<FilterCondition> normalized;
        try {
            List<FilterCondition> values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Contract condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            normalized = values.stream().map(condition -> ContractFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract temporary filter condition is invalid");
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
            AuthenticatedCaller caller, ContractAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess,
            List<FilterCondition> conditions) {
        conditions.forEach(condition -> {
            support.requireReadableUnmasked(fieldAccess, condition.fieldCode().getValue());
            validateRelationCondition(caller, access, condition);
        });
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, ContractAccess access, FilterCondition condition) {
        BusinessObjectType type = null;
        if (new FieldCode(ContractFieldCatalog.CUSTOMER).equals(condition.fieldCode())) {
            type = BusinessObjectType.CUSTOMER;
        } else if (new FieldCode(ContractFieldCatalog.OPPORTUNITY).equals(condition.fieldCode())) {
            type = BusinessObjectType.OPPORTUNITY;
        } else if (new FieldCode(ContractFieldCatalog.CONTACT).equals(condition.fieldCode())) {
            type = BusinessObjectType.CONTACT;
        }
        if (type == null) {
            return;
        }
        if (condition.operator() == FilterOperator.IS_EMPTY
                || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract relation filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                var tenantId = recordAccessGuard.requireVisible(caller, type,
                        Long.parseLong(value), false);
                if (!access.tenantId().sameValueAs(tenantId)) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contract relation filter tenant is invalid");
                }
            } catch (NumberFormatException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contract relation filter ID is invalid");
            }
        }
    }

    private Result<ContractCsvDocument> rejectSelectedExport(
            ApplicationErrorCode code, String message) {
        return queryTemplate.reject(UseCaseOperation.CONTRACT_CSV_EXPORT, support.error(code, message));
    }

    private List<SelectedExportTarget> selectedExportTargets(
            ContractBatchCsvExportRequest request) {
        require(request, "Contract selected CSV export request");
        if (request.getContracts() == null || request.getContracts().isEmpty()
                || request.getContracts().size() > SELECTED_EXPORT_MAX_ROWS) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Selected contract export targets are invalid");
        }
        LinkedHashSet<ContractId> ids = new LinkedHashSet<>();
        List<SelectedExportTarget> targets = new ArrayList<>();
        for (ContractBatchVersionRequest target : request.getContracts()) {
            if (target == null || target.getVersion() == null || target.getVersion() < 0) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Selected contract export target is invalid");
            }
            ContractId id = contractId(target.getContractId());
            if (!ids.add(id)) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Selected contract export target is duplicated");
            }
            targets.add(new SelectedExportTarget(id, target.getVersion()));
        }
        return List.copyOf(targets);
    }

    private Map<ContractId, Contract> contractsById(List<Contract> contracts) {
        return contracts.stream().collect(Collectors.toMap(Contract::getId, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
    }

    private List<SelectedExportColumn> selectedExportColumns(
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        List<SelectedExportColumn> columns = new ArrayList<>();
        columns.add(new SelectedExportColumn("合同 ID", false,
                contract -> String.valueOf(contract.getId().getValue())));
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.NUMBER, "合同编号", true,
                Contract::getNumber);
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.NAME, "合同名称", true,
                Contract::getName);
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.ORDER_DATE, "签约日期", false,
                contract -> dateValue(contract.getOrderDate()));
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.START_DATE, "开始日期", false,
                contract -> dateValue(contract.getStartDate()));
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.END_DATE, "结束日期", false,
                contract -> dateValue(contract.getEndDate()));
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.CONTRACT_TYPE, "合同类型", true,
                Contract::getContractType);
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.PAYMENT_TYPE, "付款方式", true,
                Contract::getPaymentType);
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.TOTAL_AMOUNT, "合同金额", false,
                contract -> contract.getTotalAmount() == null
                        ? null : contract.getTotalAmount().toPlainString());
        addSelectedExportColumn(columns, accesses, ContractFieldCatalog.STATUS, "状态", true,
                contract -> contract.getStatus() == null ? null : contract.getStatus().name());
        return List.copyOf(columns);
    }

    private void addSelectedExportColumn(
            List<SelectedExportColumn> columns,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            String code, String header, boolean formulaSafe,
            Function<Contract, String> value) {
        if (readableUnmasked(accesses, code)) {
            columns.add(new SelectedExportColumn(header, formulaSafe, value));
        }
    }

    private String dateValue(java.time.LocalDate value) {
        return value == null ? null : value.toString();
    }

    private List<ContractVO> values(
            AuthenticatedCaller caller, ContractAccess access,
            List<Contract> contracts, Map<FieldCode, EffectiveFieldAccess> accesses) {
        Set<CustomerId> customers = visibleCustomers(caller, contracts);
        Set<OpportunityId> opportunities = visibleOpportunities(caller, contracts);
        Set<ContactId> contacts = visibleContacts(caller, contracts);
        boolean productsVisible = productsVisible(caller, access, accesses);
        Set<ContractId> collaboratorVisible = collaboratorVisibleIds(caller, contracts);
        return contracts.stream().map(value -> support.toVO(value, accesses,
                customers.contains(value.getCustomerId()),
                value.getOpportunityId() != null && opportunities.contains(value.getOpportunityId()),
                value.getContactId() != null && contacts.contains(value.getContactId()),
                productsVisible, collaboratorVisible.contains(value.getId()))).toList();
    }

    private Set<ContractId> collaboratorVisibleIds(
            AuthenticatedCaller caller, List<Contract> contracts) {
        if (contracts.isEmpty()) {
            return Set.of();
        }
        try {
            ContractAccess access = accessResolver.access(caller,
                    AuthorityCatalog.CONTRACTS_ASSIGN, ContractAccessMode.OWNER_SCOPE);
            return contractRepository.findVisibleByIds(access.tenantId(), contracts.stream()
                            .map(Contract::getId).toList(), access.visibility(), contracts.size())
                    .stream().map(Contract::getId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private Set<CustomerId> visibleCustomers(
            AuthenticatedCaller caller, Collection<Contract> values) {
        if (!caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)) {
            return Set.of();
        }
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        List<CustomerId> ids = values.stream().map(Contract::getCustomerId).distinct().toList();
        return ids.isEmpty() ? Set.of() : customerRepository.findVisibleByIds(
                        access.tenantId(), ids, access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Customer::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<OpportunityId> visibleOpportunities(
            AuthenticatedCaller caller, Collection<Contract> values) {
        if (!caller.hasAuthority(AuthorityCatalog.OPPORTUNITIES_READ)) {
            return Set.of();
        }
        var access = opportunityAccessResolver.access(caller,
                AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
        List<OpportunityId> ids = values.stream().map(Contract::getOpportunityId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Set.of() : opportunityRepository.findVisibleByIds(
                        access.tenantId(), ids, access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Opportunity::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<ContactId> visibleContacts(
            AuthenticatedCaller caller, Collection<Contract> values) {
        if (!caller.hasAuthority(AuthorityCatalog.CONTACTS_READ)) {
            return Set.of();
        }
        var access = contactAccessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                ContactAccessMode.READ);
        List<ContactId> ids = values.stream().map(Contract::getContactId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Set.of() : contactRepository.findVisibleByIds(
                        access.tenantId(), ids, access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Contact::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean productsVisible(
            AuthenticatedCaller caller, ContractAccess access,
            Map<FieldCode, EffectiveFieldAccess> contractFields) {
        if (!caller.hasAuthority(AuthorityCatalog.PRODUCTS_READ)) {
            return false;
        }
        if (!readableUnmasked(contractFields, ContractFieldCatalog.PRODUCTS)) {
            return false;
        }
        Map<FieldCode, EffectiveFieldAccess> fields = productSupport.accesses(
                access.tenantId(), access.memberId());
        return List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.CATEGORY, ProductFieldCatalog.UNIT,
                        ProductFieldCatalog.LIST_PRICE)
                .stream().allMatch(code -> readableUnmasked(fields, code));
    }

    private boolean readableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, String code) {
        EffectiveFieldAccess access = accesses.get(new FieldCode(code));
        return access != null && access.accessLevel() != FieldAccessLevel.HIDDEN
                && access.maskType() == FieldMaskType.NONE;
    }

    private void requireVisibleCustomer(AuthenticatedCaller caller, CustomerId id) {
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        if (customerRepository.findVisibleById(
                access.tenantId(), id, access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Contract customer was not found");
        }
    }

    private void requireVisibleOpportunity(AuthenticatedCaller caller, OpportunityId id) {
        var access = opportunityAccessResolver.access(caller,
                AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
        if (opportunityRepository.findVisibleById(
                access.tenantId(), id, access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Contract opportunity was not found");
        }
    }

    private ContractId contractId(Long value) {
        try {
            return new ContractId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract ID must be positive");
        }
    }

    private CustomerId customerId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }

    private OpportunityId opportunityId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new OpportunityId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity ID must be positive");
        }
    }

    private MemberId memberId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new MemberId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Member ID must be positive");
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

    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        return pager;
    }

    private record QueryContext(
            ContractQuery query, Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
    }

    private record SelectedExportTarget(ContractId id, long version) {
    }

    private record SelectedExportColumn(
            String header, boolean formulaSafe, Function<Contract, String> value) {
    }

    private void require(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }
}
