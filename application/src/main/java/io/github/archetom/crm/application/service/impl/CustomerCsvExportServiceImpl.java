package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCsvExportRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.application.export.CustomerCsvDocument;
import io.github.archetom.crm.application.export.BoundedCsvEncoder;
import io.github.archetom.crm.application.export.BoundedCsvEncoder.CsvLimitExceededException;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.CallerGuard;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.CustomerCsvExportService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerExportCell;
import io.github.archetom.crm.domain.model.CustomerExportQuery;
import io.github.archetom.crm.domain.model.CustomerExportRow;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldValuePolicy;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.common.result.Result;
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

@Service
@RequiredArgsConstructor
public class CustomerCsvExportServiceImpl implements CustomerCsvExportService {

    static final int MAX_FIELDS = 50;
    static final int MAX_ROWS = 5_000;
    static final int ID_PROBE_LIMIT = MAX_ROWS + 1;
    static final int ID_BATCH_SIZE = 200;
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
            new FieldCode(CustomerFieldCatalog.TAGS));

    private final CustomerRepository customerRepository;
    private final SavedViewRepository savedViewRepository;
    private final CallerGuard callerGuard;
    private final CustomerAccessResolver accessResolver;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final CustomerApplicationSupport support;
    private final CustomerCsvExportAdmission admission;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<CustomerCsvDocument> export(
            AuthenticatedCaller caller, CustomerCsvExportRequest request) {
        TenantId callerTenant;
        try {
            callerTenant = callerGuard.requireTenant(caller, AuthorityCatalog.CUSTOMERS_READ);
            TenantId exportTenant = callerGuard.requireTenant(caller, AuthorityCatalog.CUSTOMERS_EXPORT);
            if (!callerTenant.sameValueAs(exportTenant)) {
                return reject(ApplicationErrorCode.ACCESS_DENIED,
                        "Customer export access is unavailable");
            }
        } catch (ApplicationException exception) {
            return queryTemplate.reject(UseCaseOperation.CUSTOMER_CSV_EXPORT, exception);
        }

        MemberId callerMember = new MemberId(caller.actorId());
        CustomerCsvExportAdmission.Lease lease = admission.tryAcquire(callerTenant, callerMember);
        if (lease == null) {
            return reject(ApplicationErrorCode.CONCURRENT_OPERATION,
                    "Customer export capacity is unavailable");
        }
        boolean documentOwnsLease = false;
        try {
            Result<CustomerCsvDocument> result = queryTemplate.execute(UseCaseOperation.CUSTOMER_CSV_EXPORT,
                new ServiceOperation<>() {
                    private List<FieldCode> requestedFields;

                    @Override
                    public void validate() {
                        requestedFields = fields(request);
                        if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                                && request.getOwnerActorId() != null) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Customer export owner scenes are mutually exclusive");
                        }
                        if (request.getSavedViewId() != null && request.getConditions() != null
                                && !request.getConditions().isEmpty()) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Customer saved and temporary filters are mutually exclusive");
                        }
                    }

                    @Override
                    public CustomerCsvDocument execute() {
                        CustomerAccess readAccess = accessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                        CustomerAccess exportAccess = accessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_EXPORT, CustomerAccessMode.OWNER_SCOPE);
                        if (!readAccess.tenantId().sameValueAs(exportAccess.tenantId())
                                || !readAccess.memberId().sameValueAs(exportAccess.memberId())
                                || !callerTenant.sameValueAs(readAccess.tenantId())
                                || !callerMember.sameValueAs(readAccess.memberId())) {
                            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                                    "Customer export access is unavailable");
                        }
                        List<FieldDefinition> fields = support.fields(readAccess.tenantId()).stream()
                                .filter(FieldDefinition::isActive).toList();
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                readAccess.tenantId(), readAccess.memberId());
                        List<FieldDefinition> selected = selectedFields(requestedFields, fields, fieldAccess);
                        String name = optional(request.getName());
                        SearchKeyword keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
                        if (name != null) {
                            requireReadableUnmasked(fieldAccess,
                                    new FieldCode(CustomerFieldCatalog.NAME));
                        }
                        if (request.getOwnerActorId() != null
                                || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
                            requireReadableUnmasked(fieldAccess,
                                    new FieldCode(CustomerFieldCatalog.OWNER_ACTOR));
                        }
                        List<FilterCondition> conditions = conditions(request.getSavedViewId(),
                                request.getConditions(), readAccess, fields, fieldAccess);
                        CustomerExportQuery query = new CustomerExportQuery(
                                readAccess.visibility(), exportAccess.visibility(),
                                keyword == null ? null : keyword.value(),
                                FieldAccessPolicy.searchableCandidates(fields, fieldAccess,
                                        CustomerFieldCatalog.searchCandidates()), name,
                                request.getOwnerActorId() == null ? null
                                        : new io.github.archetom.crm.domain.valueobject.MemberId(
                                                request.getOwnerActorId()),
                                Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                                        ? subordinateOwnerResolver.resolve(readAccess.tenantId(),
                                        readAccess.memberId(), readAccess.visibility().dataScope()) : null,
                                request.getStarred(), conditions);
                        List<CustomerId> customerIds = customerRepository.findExportCustomerIds(
                                readAccess.tenantId(), query, ID_PROBE_LIMIT);
                        if (customerIds.size() > MAX_ROWS) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Customer export exceeds the row limit");
                        }
                        try {
                            BoundedCsvEncoder encoder = new BoundedCsvEncoder(MAX_BYTES);
                            encoder.row(headers(requestedFields),
                                    allFormulaSafe(requestedFields.size() + 1));
                            for (int offset = 0; offset < customerIds.size(); offset += ID_BATCH_SIZE) {
                                List<CustomerId> batch = customerIds.subList(offset,
                                        Math.min(offset + ID_BATCH_SIZE, customerIds.size()));
                                List<CustomerExportRow> rows = customerRepository.findExportRows(
                                        readAccess.tenantId(), batch, requestedFields);
                                appendRows(encoder, rows, selected, requestedFields);
                            }
                            return new CustomerCsvDocument(encoder.toByteArray(), lease);
                        } catch (CsvLimitExceededException exception) {
                            throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Customer export exceeds the byte limit");
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

    private Result<CustomerCsvDocument> reject(ApplicationErrorCode code, String message) {
        return queryTemplate.reject(UseCaseOperation.CUSTOMER_CSV_EXPORT, support.error(code, message));
    }

    private List<FieldCode> fields(CustomerCsvExportRequest request) {
        if (request == null || request.getFieldCodes() == null || request.getFieldCodes().isEmpty()
                || request.getFieldCodes().size() > MAX_FIELDS) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer export fields are invalid");
        }
        List<FieldCode> fields = new ArrayList<>();
        for (String code : request.getFieldCodes()) {
            try {
                fields.add(new FieldCode(code));
            } catch (RuntimeException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer export fields are invalid");
            }
        }
        if (new LinkedHashSet<>(fields).size() != fields.size()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer export fields are duplicated");
        }
        return List.copyOf(fields);
    }

    private List<FieldDefinition> selectedFields(
            List<FieldCode> requested, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> definitions = fields.stream().collect(Collectors.toMap(
                FieldDefinition::getCode, value -> value, (left, right) -> left, LinkedHashMap::new));
        List<FieldDefinition> selected = new ArrayList<>();
        for (FieldCode code : requested) {
            FieldDefinition definition = definitions.get(code);
            if (definition == null || !definition.isActive()
                    || NON_PROJECTABLE_FIELD_CODES.contains(code)
                    || !EXPORTABLE_TYPES.contains(definition.getFieldType())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer export field is unavailable");
            }
            requireReadableUnmasked(fieldAccess, code);
            selected.add(definition);
        }
        return List.copyOf(selected);
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

    private void appendRows(BoundedCsvEncoder encoder, List<CustomerExportRow> rows,
            List<FieldDefinition> selected, List<FieldCode> requested) {
        for (CustomerExportRow row : rows) {
            if (row == null || row.customerId() == null || row.cells().size() != requested.size()) {
                throw new IllegalStateException("customer export row projection is invalid");
            }
            List<String> values = new ArrayList<>(requested.size() + 1);
            List<Boolean> formulaSafe = new ArrayList<>(requested.size() + 1);
            values.add(Long.toString(row.customerId().getValue()));
            formulaSafe.add(false);
            for (int index = 0; index < requested.size(); index++) {
                CustomerExportCell cell = row.cells().get(index);
                FieldDefinition field = selected.get(index);
                if (cell == null || !requested.get(index).equals(cell.fieldCode())) {
                    throw new IllegalStateException("customer export cell projection is invalid");
                }
                values.add(normalize(cell.scalarValue(), field.getFieldType()));
                formulaSafe.add(FORMULA_GUARDED_TYPES.contains(field.getFieldType()));
            }
            encoder.row(values, formulaSafe);
        }
    }

    private List<String> headers(List<FieldCode> fields) {
        List<String> headers = new ArrayList<>(fields.size() + 1);
        headers.add("customer_id");
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
            throw new IllegalStateException("customer export scalar projection is invalid", exception);
        }
    }

    private String booleanValue(String value) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalStateException("customer export boolean projection is invalid");
        }
        return value;
    }

    private void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Customer field cannot be exported");
        }
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
