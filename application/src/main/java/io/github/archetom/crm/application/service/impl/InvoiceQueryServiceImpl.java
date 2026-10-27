package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.assembler.InvoiceAssembler;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver;
import io.github.archetom.crm.application.security.InvoiceAccessResolver.InvoiceAccess;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.InvoiceQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.InvoiceProfileVO;
import io.github.archetom.crm.application.vo.InvoiceVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.entity.InvoiceProfile;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.InvoiceAccessMode;
import io.github.archetom.crm.domain.model.InvoiceQuery;
import io.github.archetom.crm.domain.model.InvoiceSort;
import io.github.archetom.crm.domain.model.InvoiceStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldValuePolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.InvoiceProfileRepository;
import io.github.archetom.crm.domain.repository.InvoiceRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceQueryServiceImpl implements InvoiceQueryService {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceProfileRepository profileRepository;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final SavedViewRepository savedViewRepository;
    private final InvoiceAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final InvoiceApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<InvoiceVO>> query(
            AuthenticatedCaller caller, InvoiceQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.INVOICE_QUERY,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private String keyword;

                    @Override
                    public void validate() {
                        require(request, "Invoice query request");
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_READ,
                                InvoiceAccessMode.READ);
                        keyword = SearchKeyword.optional(request.getKeyword())
                                .map(SearchKeyword::value).orElse(null);
                    }

                    @Override
                    public Pager<InvoiceVO> execute() {
                        List<FieldDefinition> definitions = support.fields(access.tenantId());
                        Map<FieldCode, EffectiveFieldAccess> fields = support.accesses(
                                access.tenantId(), access.memberId());
                        InvoiceQuery query = query(caller, access, request,
                                definitions, fields, keyword);
                        PageResult<Invoice> result = invoiceRepository.findInvoices(
                                access.tenantId(), query);
                        List<InvoiceVO> values = values(caller, result.items(), fields);
                        Pager<InvoiceVO> pager = pager(
                                result.page(), result.size(), result.total());
                        pager.setObjectList(values);
                        return pager;
                    }
                });
    }

    @Override
    public Result<InvoiceVO> get(AuthenticatedCaller caller, Long invoiceIdValue) {
        return queryTemplate.execute(UseCaseOperation.INVOICE_GET,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private InvoiceId invoiceId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_READ,
                                InvoiceAccessMode.READ);
                        invoiceId = invoiceId(invoiceIdValue);
                    }

                    @Override
                    public InvoiceVO execute() {
                        Invoice invoice = invoiceRepository.findVisibleById(
                                        access.tenantId(), invoiceId, access.visibility())
                                .orElseThrow(io.github.archetom.crm.domain.exception
                                        .InvoiceNotFoundException::new);
                        return values(caller, List.of(invoice), support.accesses(
                                access.tenantId(), access.memberId())).getFirst();
                    }
                });
    }

    @Override
    public Result<List<InvoiceProfileVO>> profiles(
            AuthenticatedCaller caller, Long customerIdValue) {
        return queryTemplate.execute(UseCaseOperation.INVOICE_PROFILE_QUERY,
                new ServiceOperation<>() {
                    private InvoiceAccess access;
                    private CustomerId customerId;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.INVOICES_READ,
                                InvoiceAccessMode.READ);
                        customerId = customerId(customerIdValue);
                    }

                    @Override
                    public List<InvoiceProfileVO> execute() {
                        requireVisibleCustomer(caller, access, customerId);
                        return profileRepository.findByCustomer(access.tenantId(), customerId,
                                        InvoiceProfile.MAX_ACTIVE_PER_CUSTOMER)
                                .stream().map(InvoiceAssembler::toVO).toList();
                    }
                });
    }

    private InvoiceQuery query(
            AuthenticatedCaller caller, InvoiceAccess access,
            InvoiceQueryRequest request, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            String keyword) {
        MemberId owner = memberId(request.getOwnerActorId());
        if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly()) && owner != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice owner scenes are mutually exclusive");
        }
        if (owner != null || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
            support.requireReadableUnmasked(accesses, InvoiceFieldCatalog.OWNER_ACTOR);
        }
        CustomerId customer = customerIdOrNull(request.getCustomerId());
        if (customer != null) {
            support.requireReadableUnmasked(accesses, InvoiceFieldCatalog.CUSTOMER);
            requireVisibleCustomer(caller, access, customer);
        }
        ContractId contract = contractId(request.getContractId());
        if (contract != null) {
            support.requireReadableUnmasked(accesses, InvoiceFieldCatalog.CONTRACT);
            requireVisibleContract(caller, access, contract);
        }
        InvoiceStatus status = request.getStatus() == null ? null
                : support.parse(InvoiceStatus.class, request.getStatus(), "Invoice status");
        if (status == InvoiceStatus.DELETED) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Deleted invoices cannot be queried");
        }
        if (status != null) {
            support.requireReadableUnmasked(accesses, InvoiceFieldCatalog.STATUS);
        }
        if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice static and custom sort accesses are mutually exclusive");
        }
        InvoiceSort sort;
        if (request.getCustomSortFieldCode() == null) {
            sort = invoiceSort(request.getSortField(), request.getSortDirection());
        } else if (request.getSortDirection() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice custom sort must include direction");
        } else {
            sort = customInvoiceSort(request.getCustomSortFieldCode(), request.getSortDirection(),
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
        return new InvoiceQuery(access.visibility(), keyword,
                FieldAccessPolicy.searchableCandidates(definitions, accesses,
                        InvoiceFieldCatalog.searchCandidates()), owner,
                subordinateOwnerMemberIds,
                customer, contract, status, sort, conditions,
                page(request.getPage()), size(request.getSize()));
    }

    private InvoiceSort invoiceSort(String field, String direction) {
        try {
            return InvoiceSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Invoice sort is invalid");
        }
    }

    private InvoiceSort customInvoiceSort(
            String requestedCode, String direction, List<FieldDefinition> definitions,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice custom sort field is invalid");
        }
        FieldDefinition field = definitions.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || !field.isActive() || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice custom sort field is unavailable");
        }
        support.requireReadableUnmasked(accesses, code.getValue());
        try {
            return InvoiceSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Invoice sort is invalid");
        }
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            InvoiceAccess access, List<FieldDefinition> fields,
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
        if (view.getObjectType() != BusinessObjectType.INVOICE) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not an invoice view");
        }
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(searchableTypes);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream().map(condition ->
                    InvoiceFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice saved view condition is invalid");
        }
        validateConditions(caller, access, fieldAccess, normalized);
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            InvoiceAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        List<FilterCondition> normalized;
        try {
            List<FilterCondition> values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Invoice condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            normalized = values.stream().map(condition -> InvoiceFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice temporary filter condition is invalid");
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
            AuthenticatedCaller caller, InvoiceAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess,
            List<FilterCondition> conditions) {
        conditions.forEach(condition -> {
            support.requireReadableUnmasked(fieldAccess, condition.fieldCode().getValue());
            validateRelationCondition(caller, access, condition);
        });
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, InvoiceAccess access, FilterCondition condition) {
        if (!new FieldCode(InvoiceFieldCatalog.CUSTOMER).equals(condition.fieldCode())
                && !new FieldCode(InvoiceFieldCatalog.CONTRACT).equals(condition.fieldCode())) {
            return;
        }
        if (condition.operator() == FilterOperator.IS_EMPTY
                || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice relation filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                if (new FieldCode(InvoiceFieldCatalog.CUSTOMER).equals(condition.fieldCode())) {
                    requireVisibleCustomer(caller, access, customerId(Long.parseLong(value)));
                } else {
                    requireVisibleContract(caller, access, contractId(Long.parseLong(value)));
                }
            } catch (NumberFormatException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Invoice relation filter ID is invalid");
            }
        }
    }

    private List<InvoiceVO> values(
            AuthenticatedCaller caller, List<Invoice> invoices,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        Set<CustomerId> customers = visibleCustomers(caller, invoices);
        Set<ContractId> contracts = visibleContracts(caller, invoices);
        return invoices.stream().map(invoice -> support.toVO(invoice, accesses,
                customers.contains(invoice.getCustomerId()),
                contracts.contains(invoice.getContractId()))).toList();
    }

    private Set<CustomerId> visibleCustomers(
            AuthenticatedCaller caller, Collection<Invoice> invoices) {
        if (!caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)) {
            return Set.of();
        }
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        List<CustomerId> ids = invoices.stream().map(Invoice::getCustomerId)
                .distinct().toList();
        return ids.isEmpty() ? Set.of() : customerRepository.findVisibleByIds(
                        access.tenantId(), ids, access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Customer::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<ContractId> visibleContracts(
            AuthenticatedCaller caller, Collection<Invoice> invoices) {
        if (!caller.hasAuthority(AuthorityCatalog.CONTRACTS_READ)) {
            return Set.of();
        }
        var access = contractAccessResolver.access(caller, AuthorityCatalog.CONTRACTS_READ,
                ContractAccessMode.READ);
        List<ContractId> ids = invoices.stream().map(Invoice::getContractId)
                .distinct().toList();
        return ids.isEmpty() ? Set.of() : contractRepository.findVisibleByIds(
                        access.tenantId(), ids, access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Contract::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void requireVisibleCustomer(
            AuthenticatedCaller caller, InvoiceAccess invoiceAccess, CustomerId customerId) {
        var access = customerAccessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                CustomerAccessMode.READ);
        if (!access.tenantId().sameValueAs(invoiceAccess.tenantId())
                || customerRepository.findVisibleById(access.tenantId(), customerId,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Invoice customer was not found");
        }
    }

    private void requireVisibleContract(
            AuthenticatedCaller caller, InvoiceAccess invoiceAccess, ContractId contractId) {
        var access = contractAccessResolver.access(caller,
                AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
        if (!access.tenantId().sameValueAs(invoiceAccess.tenantId())
                || contractRepository.findVisibleById(access.tenantId(), contractId,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Invoice contract was not found");
        }
    }

    private InvoiceId invoiceId(Long value) {
        try {
            return new InvoiceId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Invoice ID must be positive");
        }
    }

    private CustomerId customerId(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }

    private CustomerId customerIdOrNull(Long value) {
        return value == null ? null : customerId(value);
    }

    private ContractId contractId(Long value) {
        if (value == null) {
            return null;
        }
        try {
            return new ContractId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contract ID must be positive");
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

    private void require(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }
}
