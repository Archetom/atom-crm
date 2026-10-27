package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionCreateRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionQueryRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionRunRequest;
import io.github.archetom.crm.api.dto.request.ReportDefinitionUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReportMeasureRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.application.service.ReportDefinitionService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.FieldOptionVO;
import io.github.archetom.crm.application.vo.FilterConditionVO;
import io.github.archetom.crm.application.vo.ReportCatalogVO;
import io.github.archetom.crm.application.vo.ReportDefinitionRunVO;
import io.github.archetom.crm.application.vo.ReportDefinitionSummaryVO;
import io.github.archetom.crm.application.vo.ReportDefinitionVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReportDefinition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportFieldReference;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportMeasure;
import io.github.archetom.crm.domain.model.ReportMeasureType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldValuePolicy;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.ReportDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReportDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportDefinitionServiceImpl implements ReportDefinitionService {

    private static final int MAX_DEFINITIONS = 100;
    private static final int MAX_ROWS = 500;
    private static final int MAX_BUCKETS = 120;
    private static final int MAX_YEARS = 10;
    private static final List<BusinessObjectType> SUPPORTED = List.of(
            BusinessObjectType.OPPORTUNITY, BusinessObjectType.CONTRACT,
            BusinessObjectType.RECEIVABLE);

    private final ReportDefinitionRepository definitionRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final ReceivableRepository receivableRepository;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<ReportCatalogVO> catalog(AuthenticatedCaller caller, String objectType) {
        return queryTemplate.execute(UseCaseOperation.REPORT_CATALOG_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private SourceAccess source;
            private List<SourceAccess> availableSources;

            @Override
            public void validate() {
                tenantId = organizationAccessGuard.requireActiveMembership(
                        caller, AuthorityCatalog.REPORTS_READ);
                availableSources = availableSources(caller, tenantId);
                if (availableSources.isEmpty()) {
                    throw error(ApplicationErrorCode.ACCESS_DENIED,
                            "Caller cannot read a supported report source");
                }
                if (objectType == null || objectType.isBlank()) {
                    source = availableSources.getFirst();
                    type = source.objectType();
                } else {
                    type = supportedObject(objectType);
                    source = sourceAccess(caller, tenantId, type);
                }
            }

            @Override
            public ReportCatalogVO execute() {
                return toCatalog(catalogContext(tenantId, source), availableSources.stream()
                        .map(value -> value.objectType().name()).toList());
            }
        });
    }

    @Override
    public Result<Pager<ReportDefinitionSummaryVO>> query(
            AuthenticatedCaller caller, ReportDefinitionQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.REPORT_DEFINITION_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private BusinessObjectType type;
                    private String keyword;

                    @Override
                    public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        memberId = new MemberId(caller.actorId());
                        require(request, "report definition query");
                        if (request.getPage() == null || request.getPage() < 1
                                || request.getSize() == null || request.getSize() < 1
                                || request.getSize() > 100) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Report definition page is invalid");
                        }
                        type = request.getObjectType() == null || request.getObjectType().isBlank()
                                ? null : supportedObject(request.getObjectType());
                        keyword = request.getKeyword() == null || request.getKeyword().isBlank()
                                ? null : IdentifierKeyNormalizer.normalize(request.getKeyword());
                    }

                    @Override
                    public Pager<ReportDefinitionSummaryVO> execute() {
                        PageResult<ReportDefinition> page = definitionRepository.findActivePage(
                                tenantId, memberId, type, keyword,
                                request.getPage(), request.getSize());
                        Pager<ReportDefinitionSummaryVO> result = new Pager<>();
                        result.setPageNum(page.page());
                        result.setPageSize(page.size());
                        result.setTotalNum(page.total());
                        result.setObjectList(page.items().stream()
                                .map(ReportDefinitionServiceImpl.this::summary).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<ReportDefinitionVO> get(AuthenticatedCaller caller, Long definitionId) {
        return queryTemplate.execute(UseCaseOperation.REPORT_DEFINITION_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ReportDefinitionId id;

                    @Override
                    public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        memberId = new MemberId(caller.actorId());
                        id = id(definitionId);
                    }

                    @Override
                    public ReportDefinitionVO execute() {
                        ReportDefinition definition = requireDefinition(tenantId, memberId, id);
                        SourceAccess source = sourceAccess(caller, tenantId,
                                definition.getObjectType());
                        CatalogContext catalog = catalogContext(tenantId, source);
                        validateDefinition(definition, catalog);
                        return detail(definition);
                    }
                });
    }

    @Override
    public Result<ReportDefinitionVO> create(
            AuthenticatedCaller caller, ReportDefinitionCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.REPORT_DEFINITION_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private BusinessObjectType type;
                    private SourceAccess source;

                    @Override
                    public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        require(request, "report definition create request");
                        type = supportedObject(request.getObjectType());
                        source = sourceAccess(caller, tenantId, type);
                    }

                    @Override
                    public ReportDefinitionVO execute() {
                        objectLock.acquire(tenantId, type);
                        if (definitionRepository.countActive(tenantId, memberId, type)
                                >= MAX_DEFINITIONS) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Report definition limit was reached");
                        }
                        CatalogContext catalog = catalogContext(tenantId, source);
                        List<FilterCondition> filters = normalizeFilters(
                                conditions(request.getFilters()), catalog);
                        ReportDefinition definition = ReportDefinition.create(
                                tenantId, memberId, request.getName(), request.getDescription(), type,
                                fieldCode(request.getTimeFieldCode()),
                                granularity(request.getGranularity()),
                                optionalFieldCode(request.getDimensionFieldCode()),
                                filters, measures(request.getMeasures()), catalog.types());
                        return detail(definitionRepository.save(tenantId, definition));
                    }
                });
    }

    @Override
    public Result<ReportDefinitionVO> update(
            AuthenticatedCaller caller, Long definitionId,
            ReportDefinitionUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.REPORT_DEFINITION_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ReportDefinitionId id;
                    private BusinessObjectType type;
                    private SourceAccess source;

                    @Override
                    public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = id(definitionId);
                        require(request, "report definition update request");
                        ReportDefinition current = requireDefinition(tenantId, memberId, id);
                        type = current.getObjectType();
                        if (request.getObjectType() != null && !request.getObjectType().isBlank()
                                && supportedObject(request.getObjectType()) != type) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Report definition source cannot be changed");
                        }
                        source = sourceAccess(caller, tenantId, type);
                    }

                    @Override
                    public ReportDefinitionVO execute() {
                        objectLock.acquire(tenantId, type);
                        ReportDefinition definition = requireDefinition(tenantId, memberId, id);
                        requireVersion(definition, request.getVersion());
                        CatalogContext catalog = catalogContext(tenantId, source);
                        definition.update(request.getName(), request.getDescription(),
                                fieldCode(request.getTimeFieldCode()),
                                granularity(request.getGranularity()),
                                optionalFieldCode(request.getDimensionFieldCode()),
                                normalizeFilters(conditions(request.getFilters()), catalog),
                                measures(request.getMeasures()), catalog.types());
                        return detail(definitionRepository.save(tenantId, definition));
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long definitionId, Long version) {
        return commandTemplate.execute(UseCaseOperation.REPORT_DEFINITION_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ReportDefinitionId id;
                    private BusinessObjectType type;

                    @Override
                    public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = id(definitionId);
                        type = requireDefinition(tenantId, memberId, id).getObjectType();
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(tenantId, type);
                        ReportDefinition definition = requireDefinition(tenantId, memberId, id);
                        requireVersion(definition, version);
                        definition.delete();
                        definitionRepository.save(tenantId, definition);
                        return null;
                    }
                });
    }

    @Override
    public Result<ReportDefinitionRunVO> run(
            AuthenticatedCaller caller, Long definitionId,
            ReportDefinitionRunRequest request) {
        return queryTemplate.execute(UseCaseOperation.REPORT_DEFINITION_RUN,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ReportDefinitionId id;
                    private ReportDateRange range;

                    @Override
                    public void validate() {
                        tenantId = organizationAccessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.REPORTS_READ);
                        memberId = new MemberId(caller.actorId());
                        id = id(definitionId);
                        require(request, "report execution request");
                        range = new ReportDateRange(request.getFrom(), request.getTo());
                        if (range.exceedsYears(MAX_YEARS)) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Report execution range is too large");
                        }
                    }

                    @Override
                    public ReportDefinitionRunVO execute() {
                        ReportDefinition definition = requireDefinition(tenantId, memberId, id);
                        range.periodStarts(definition.getGranularity(), MAX_BUCKETS);
                        SourceAccess source = sourceAccess(caller, tenantId,
                                definition.getObjectType());
                        CatalogContext catalog = catalogContext(tenantId, source);
                        validateDefinition(definition, catalog);
                        List<FilterCondition> filters = normalizeFilters(
                                definition.getFilters(), catalog);
                        Map<FieldCode, ReportFieldReference> references = definition
                                .referencedFields().stream().collect(Collectors.toMap(
                                        Function.identity(), code -> reference(catalog, code)));
                        CustomReportQuery query = new CustomReportQuery(range,
                                definition.getGranularity(),
                                references.get(definition.getTimeFieldCode()),
                                definition.getDimensionFieldCode() == null ? null
                                        : references.get(definition.getDimensionFieldCode()),
                                definition.getMeasures(), references, filters, MAX_ROWS);
                        List<CustomReportRow> rows = ReportDefinitionServiceImpl.this
                                .execute(source, tenantId, query);
                        if (rows.size() > MAX_ROWS) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Report result exceeds the supported limit");
                        }
                        return runVO(definition, range, catalog, rows);
                    }
                });
    }

    private List<CustomReportRow> execute(
            SourceAccess source, TenantId tenantId, CustomReportQuery query) {
        return switch (source.objectType()) {
            case OPPORTUNITY -> opportunityRepository.runCustomReport(
                    tenantId, source.opportunity().visibility(), query);
            case CONTRACT -> contractRepository.runCustomReport(
                    tenantId, source.contract().visibility(), query);
            case RECEIVABLE -> receivableRepository.runCustomReport(
                    tenantId, source.receivable().visibility(), query);
            default -> throw new IllegalArgumentException("unsupported report source");
        };
    }

    private SourceAccess sourceAccess(
            AuthenticatedCaller caller, TenantId tenantId, BusinessObjectType type) {
        return switch (type) {
            case OPPORTUNITY -> {
                var value = opportunityAccessResolver.access(caller,
                        AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
                requireTenant(tenantId, value.tenantId());
                yield new SourceAccess(type, value.memberId(), value, null, null);
            }
            case CONTRACT -> {
                var value = contractAccessResolver.access(caller,
                        AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
                requireTenant(tenantId, value.tenantId());
                yield new SourceAccess(type, value.memberId(), null, value, null);
            }
            case RECEIVABLE -> {
                var value = receivableAccessResolver.access(caller,
                        AuthorityCatalog.RECEIVABLES_READ, ReceivableAccessMode.READ);
                requireTenant(tenantId, value.tenantId());
                yield new SourceAccess(type, value.memberId(), null, null, value);
            }
            default -> throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Report source object is unsupported");
        };
    }

    private List<SourceAccess> availableSources(
            AuthenticatedCaller caller, TenantId tenantId) {
        List<SourceAccess> sources = new ArrayList<>();
        for (BusinessObjectType type : SUPPORTED) {
            try {
                sources.add(sourceAccess(caller, tenantId, type));
            } catch (NonRetryableApplicationException exception) {
                if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                    throw exception;
                }
            }
        }
        return List.copyOf(sources);
    }

    private CatalogContext catalogContext(TenantId tenantId, SourceAccess source) {
        Map<FieldCode, EffectiveFieldAccess> accesses = fieldAccessResolver.resolve(
                tenantId, source.memberId(), source.objectType());
        List<FieldDefinition> fields = fieldRepository.findAll(
                        tenantId, source.objectType(), false).stream()
                .filter(FieldDefinition::isActive)
                .filter(FieldDefinition::isSearchable)
                .filter(field -> readableUnmasked(accesses.get(field.getCode())))
                .filter(field -> !field.isBuiltin()
                        || executableBuiltin(source.objectType(), field.getCode()))
                .sorted(Comparator.comparingInt(FieldDefinition::getDisplayOrder)
                        .thenComparing(field -> field.getCode().getValue()))
                .toList();
        Map<FieldCode, FieldDefinition> byCode = fields.stream().collect(Collectors.toMap(
                FieldDefinition::getCode, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
        Map<FieldCode, CustomFieldType> types = byCode.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        return new CatalogContext(source.objectType(), fields, byCode, types);
    }

    private ReportCatalogVO toCatalog(
            CatalogContext context, List<String> supportedObjectTypes) {
        List<ReportCatalogVO.ReportCatalogFieldVO> all = context.fields().stream()
                .map(this::catalogField).toList();
        return new ReportCatalogVO(context.objectType().name(),
                supportedObjectTypes,
                all.stream().filter(value -> value.fieldType().equals("DATE")
                        || value.fieldType().equals("DATETIME")).toList(),
                all.stream().filter(value -> Set.of("SELECT", "BOOLEAN", "USER", "DEPARTMENT")
                        .contains(value.fieldType())).toList(),
                all.stream().filter(value -> value.fieldType().equals("INTEGER")
                        || value.fieldType().equals("DECIMAL")).toList(),
                all.stream().filter(value -> !value.fieldType().equals("RELATION")).toList(),
                List.of("COUNT", "SUM"));
    }

    private ReportCatalogVO.ReportCatalogFieldVO catalogField(FieldDefinition field) {
        List<String> granularities = field.getFieldType() == CustomFieldType.DATE
                || field.getFieldType() == CustomFieldType.DATETIME
                ? List.of("DAY", "WEEK", "MONTH") : List.of();
        List<String> operators = SavedViewPolicy.supportedOperators(field.getFieldType()).stream()
                .map(Enum::name).sorted().toList();
        List<FieldOptionVO> options = field.getOptions().stream()
                .map(value -> new FieldOptionVO(value.value(), value.label(), value.order())).toList();
        return new ReportCatalogVO.ReportCatalogFieldVO(field.getCode().getValue(),
                field.getDisplayName(), field.getFieldType().name(),
                granularities, operators, options);
    }

    private void validateDefinition(ReportDefinition definition, CatalogContext catalog) {
        ReportDefinition.create(definition.getTenantId(), definition.getCreatorMemberId(),
                definition.getName(), definition.getDescription(), definition.getObjectType(),
                definition.getTimeFieldCode(), definition.getGranularity(),
                definition.getDimensionFieldCode(), definition.getFilters(),
                definition.getMeasures(), catalog.types());
        normalizeFilters(definition.getFilters(), catalog);
    }

    private List<FilterCondition> normalizeFilters(
            List<FilterCondition> filters, CatalogContext catalog) {
        try {
            return (filters == null ? List.<FilterCondition>of() : filters).stream()
                    .map(filter -> OpportunityFieldValuePolicy.normalizeFilterCondition(
                            catalog.byCode().get(filter.fieldCode()), filter)).toList();
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Report filter condition is invalid");
        }
    }

    private List<FilterCondition> conditions(List<FilterConditionRequest> requests) {
        return (requests == null ? List.<FilterConditionRequest>of() : requests).stream()
                .map(value -> {
                    if (value == null) throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Report filter condition is invalid");
                    return new FilterCondition(fieldCode(value.getFieldCode()),
                            parse(FilterOperator.class, value.getOperator(), "Filter operator"),
                            value.getValues());
                }).toList();
    }

    private List<ReportMeasure> measures(List<ReportMeasureRequest> requests) {
        if (requests == null) return List.of();
        return requests.stream().map(value -> {
            if (value == null) throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Report measure is invalid");
            ReportMeasureType type = ReportMeasureType.parse(value.getType());
            return new ReportMeasure(type, type == ReportMeasureType.COUNT
                    ? optionalFieldCode(value.getFieldCode()) : fieldCode(value.getFieldCode()));
        }).toList();
    }

    private ReportFieldReference reference(CatalogContext catalog, FieldCode code) {
        FieldDefinition field = catalog.byCode().get(code);
        if (field == null || field.getId() == null) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Report field is unavailable");
        }
        return new ReportFieldReference(field.getCode(), field.getFieldType(),
                field.isBuiltin(), field.getId().getValue());
    }

    private ReportDefinitionRunVO runVO(
            ReportDefinition definition, ReportDateRange range,
            CatalogContext catalog, List<CustomReportRow> rows) {
        List<ReportDefinitionRunVO.ReportColumnVO> columns = new ArrayList<>();
        FieldDefinition time = catalog.byCode().get(definition.getTimeFieldCode());
        columns.add(new ReportDefinitionRunVO.ReportColumnVO("periodStart", "TIME",
                time.getCode().getValue(), time.getFieldType().name(), time.getDisplayName()));
        FieldDefinition dimension = definition.getDimensionFieldCode() == null ? null
                : catalog.byCode().get(definition.getDimensionFieldCode());
        if (dimension != null) columns.add(new ReportDefinitionRunVO.ReportColumnVO(
                "dimension", "DIMENSION", dimension.getCode().getValue(),
                dimension.getFieldType().name(), dimension.getDisplayName()));
        for (ReportMeasure measure : definition.getMeasures()) {
            FieldDefinition field = measure.fieldCode() == null ? null
                    : catalog.byCode().get(measure.fieldCode());
            columns.add(new ReportDefinitionRunVO.ReportColumnVO(measure.key(), "MEASURE",
                    field == null ? null : field.getCode().getValue(),
                    field == null ? null : field.getFieldType().name(),
                    field == null ? "Count" : "Sum of " + field.getDisplayName()));
        }
        List<ReportDefinitionRunVO.ReportRowVO> values = rows.stream().map(row ->
                new ReportDefinitionRunVO.ReportRowVO(row.periodStart(),
                        dimension == null ? null : dimension(row.dimensionKey(), dimension),
                        row.values())).toList();
        return new ReportDefinitionRunVO(definition.getId().getValue(), definition.getVersion(),
                definition.getObjectType().name(), range.from(), range.to(),
                definition.getGranularity().name(), List.copyOf(columns), values);
    }

    private ReportDefinitionRunVO.ReportDimensionVO dimension(
            String key, FieldDefinition field) {
        String label = null;
        if (key != null && field.getFieldType() == CustomFieldType.SELECT) {
            label = field.getOptions().stream().filter(value -> value.value().equals(key))
                    .map(io.github.archetom.crm.domain.entity.FieldOption::label)
                    .findFirst().orElse(null);
        } else if (key != null && field.getFieldType() == CustomFieldType.BOOLEAN) {
            label = Boolean.parseBoolean(key) ? "True" : "False";
        }
        return new ReportDefinitionRunVO.ReportDimensionVO(key, label);
    }

    private ReportDefinitionSummaryVO summary(ReportDefinition value) {
        return new ReportDefinitionSummaryVO(value.getId().getValue(), value.getName(),
                value.getDescription(), value.getObjectType().name(),
                value.getTimeFieldCode().getValue(), value.getGranularity().name(),
                value.getDimensionFieldCode() == null ? null
                        : value.getDimensionFieldCode().getValue(),
                value.getMeasures().size(), value.getFilters().size(), value.getVersion(),
                value.getCreatedTime(), value.getUpdatedTime());
    }

    private ReportDefinitionVO detail(ReportDefinition value) {
        return new ReportDefinitionVO(value.getId().getValue(), value.getName(),
                value.getDescription(), value.getObjectType().name(),
                value.getTimeFieldCode().getValue(), value.getGranularity().name(),
                value.getDimensionFieldCode() == null ? null
                        : value.getDimensionFieldCode().getValue(),
                value.getFilters().stream().map(filter -> new FilterConditionVO(
                        filter.fieldCode().getValue(), filter.operator().name(), filter.values()))
                        .toList(),
                value.getMeasures().stream().map(measure ->
                        new ReportDefinitionVO.ReportMeasureVO(measure.type().name(),
                                measure.fieldCode() == null ? null
                                        : measure.fieldCode().getValue(), measure.key()))
                        .toList(), value.getVersion(), value.getCreatedTime(), value.getUpdatedTime());
    }

    private ReportDefinition requireDefinition(
            TenantId tenantId, MemberId memberId, ReportDefinitionId id) {
        return definitionRepository.findActiveById(tenantId, memberId, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Report definition was not found"));
    }

    private void requireVersion(ReportDefinition definition, Long version) {
        if (version == null || !version.equals(definition.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Report definition version is stale");
        }
    }

    private boolean executableBuiltin(BusinessObjectType type, FieldCode code) {
        return switch (type) {
            case OPPORTUNITY -> OpportunityFieldCatalog.codes().contains(code)
                    && !Set.of(OpportunityFieldCatalog.PRIMARY_CONTACT,
                    OpportunityFieldCatalog.CONTACTS, OpportunityFieldCatalog.PRODUCTS)
                    .contains(code.getValue());
            case CONTRACT -> ContractFieldCatalog.codes().contains(code)
                    && !ContractFieldCatalog.PRODUCTS.equals(code.getValue());
            case RECEIVABLE -> ReceivableFieldCatalog.codes().contains(code);
            default -> false;
        };
    }

    private boolean readableUnmasked(EffectiveFieldAccess access) {
        return access != null && access.accessLevel().covers(FieldAccessLevel.READ)
                && access.maskType() == FieldMaskType.NONE;
    }

    private BusinessObjectType supportedObject(String value) {
        BusinessObjectType type = parse(BusinessObjectType.class, value,
                "Report source object");
        if (!SUPPORTED.contains(type)) throw error(ApplicationErrorCode.PARAMETER_INVALID,
                "Report source object is unsupported");
        return type;
    }

    private ReportGranularity granularity(String value) {
        try {
            return ReportGranularity.parse(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Report granularity is invalid");
        }
    }

    private FieldCode fieldCode(String value) {
        try {
            return new FieldCode(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Report field code is invalid");
        }
    }

    private FieldCode optionalFieldCode(String value) {
        return value == null || value.isBlank() ? null : fieldCode(value);
    }

    private ReportDefinitionId id(Long value) {
        try {
            return new ReportDefinitionId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Report definition ID is invalid");
        }
    }

    private void requireTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Report source tenant is unavailable");
        }
    }

    private void require(Object value, String description) {
        if (value == null) throw error(ApplicationErrorCode.PARAMETER_INVALID,
                description + " is required");
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) throw error(ApplicationErrorCode.PARAMETER_INVALID,
                description + " is required");
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private record SourceAccess(
            BusinessObjectType objectType, MemberId memberId,
            OpportunityAccessResolver.OpportunityAccess opportunity,
            ContractAccessResolver.ContractAccess contract,
            ReceivableAccessResolver.ReceivableAccess receivable) { }

    private record CatalogContext(
            BusinessObjectType objectType, List<FieldDefinition> fields,
            Map<FieldCode, FieldDefinition> byCode,
            Map<FieldCode, CustomFieldType> types) { }
}
