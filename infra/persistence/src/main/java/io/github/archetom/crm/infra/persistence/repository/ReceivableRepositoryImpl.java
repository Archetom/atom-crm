package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReceivableFieldValue;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.ReceivableQuery;
import io.github.archetom.crm.domain.model.ReceivableSortDirection;
import io.github.archetom.crm.domain.model.ReceivableCustomerSummary;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.model.ReceivableSummary;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReceivableId;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.ReceivableFieldValuePOConverter;
import io.github.archetom.crm.infra.persistence.converter.ReceivablePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivableFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivableMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivableUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivableFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivableUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.query.MySqlCustomReportQuery;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import io.github.archetom.crm.infra.persistence.support.QueryFilterConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.Collection;
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
public class ReceivableRepositoryImpl implements ReceivableRepository {

    private static final Map<FieldCode, String> BUILTIN_COLUMNS = Map.ofEntries(
            Map.entry(new FieldCode(ReceivableFieldCatalog.NUMBER), "receipt_number"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.CUSTOMER), "customer_id"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.CONTRACT), "contract_id"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.PLAN), "receivable_plan_id"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.RECEIVED_DATE), "received_date"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.PAYMENT_METHOD), "payment_method"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.AMOUNT), "amount"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.REMARK), "remark"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.OWNER_ACTOR), "owner_actor_id"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.STATUS), "status"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.CREATED_TIME), "created_time"),
            Map.entry(new FieldCode(ReceivableFieldCatalog.UPDATED_TIME), "updated_time"));
    private final ReceivableMapper mapper;
    private final ReceivableFieldValueMapper fieldValueMapper;
    private final ReceivableUniqueFieldValueMapper uniqueValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final ReceivablePOConverter converter;
    private final ReceivableFieldValuePOConverter fieldConverter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final MySqlCustomReportQuery customReportQuery;

    @Override
    public Receivable save(TenantId tenantId, Receivable value,
                           Collection<FieldDefinition> fieldDefinitions) {
        requireTenant(tenantId);
        if (value == null || !tenantId.sameValueAs(value.getTenantId())) {
            throw new IllegalArgumentException("receivable tenant does not match");
        }
        ReceivablePO po = converter.toPO(value);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) throw new IllegalStateException("receivable insert failed");
            } else if (mapper.update(po, new LambdaUpdateWrapper<ReceivablePO>()
                    .eq(ReceivablePO::getTenantId, tenantId.getValue())
                    .eq(ReceivablePO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            ReceivableId id = new ReceivableId(po.getId());
            synchronize(tenantId, id, value, definitions(fieldDefinitions));
            value.onPersisted(id, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return value;
        } catch (DuplicateKeyException exception) {
            throw new io.github.archetom.crm.domain.exception.ReceivableDomainException(
                    "receivable already exists");
        }
    }

    @Override
    public Optional<Receivable> findById(TenantId tenantId, ReceivableId id) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePO po = mapper.selectOne(new LambdaQueryWrapper<ReceivablePO>()
                .eq(ReceivablePO::getTenantId, tenantId.getValue())
                .eq(ReceivablePO::getId, id.getValue())
                .ne(ReceivablePO::getStatus, ReceivableStatus.DELETED.name()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Receivable> findByIdForAttachmentMutation(TenantId tenantId, ReceivableId id) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePO po = mapper.selectOne(new LambdaQueryWrapper<ReceivablePO>()
                .eq(ReceivablePO::getTenantId, tenantId.getValue())
                .eq(ReceivablePO::getId, id.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Receivable> findVisibleById(
            TenantId tenantId, ReceivableId id, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePO po = mapper.selectOne(visible(tenantId, visibility).eq("id", id.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Receivable> findVisibleByIdForUpdate(
            TenantId tenantId, ReceivableId id, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePO po = mapper.selectOne(visible(tenantId, visibility)
                .eq("id", id.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ReceivableId> ids,
            ReceivableVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("receivable visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(ReceivableId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return mapper.selectList(visible(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(ReceivablePO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Receivable> findVisibleByIds(
            TenantId tenantId, Collection<ReceivableId> ids,
            ReceivableVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("receivable visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(ReceivableId::getValue).distinct().toList();
        return values.isEmpty() ? List.of() : hydrated(tenantId, mapper.selectList(
                visible(tenantId, visibility).in("id", values).orderByAsc("id")
                        .last("LIMIT " + limit)));
    }

    @Override
    public PageResult<Receivable> findReceivables(TenantId tenantId, ReceivableQuery query) {
        requireTenant(tenantId);
        QueryWrapper<ReceivablePO> wrapper = queryWrapper(tenantId, query);
        boolean ascending = query.sort().direction() == ReceivableSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<ReceivablePO> page = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                hydrated(tenantId, page.getRecords()));
    }

    @Override
    public ReceivableSummary summarize(TenantId tenantId, ReceivableQuery query) {
        requireTenant(tenantId);
        Map<String, Object> result = mapper.selectMaps(queryWrapper(tenantId, query)
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ReceivableSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    private QueryWrapper<ReceivablePO> queryWrapper(TenantId tenantId, ReceivableQuery query) {
        if (query == null) throw new IllegalArgumentException("receivable query is required");
        QueryWrapper<ReceivablePO> wrapper = visible(tenantId, query.visibility());
        if (query.keyword() != null) {
            if (query.keywordCandidates().isEmpty()) {
                wrapper.apply("1 = 0");
            } else if (query.keywordCandidates().containsKey(
                    new FieldCode(ReceivableFieldCatalog.NUMBER))) {
                wrapper.apply("receipt_number LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(query.keyword()));
            }
        }
        if (query.ownerMemberId() != null) wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        if (query.subordinateOwnerMemberIds() != null) {
            List<Long> ownerIds = query.subordinateOwnerMemberIds().stream()
                    .map(value -> value.getValue()).toList();
            if (ownerIds.isEmpty()) wrapper.apply("1 = 0");
            else wrapper.in("owner_actor_id", ownerIds);
        }
        if (query.customerId() != null) wrapper.eq("customer_id", query.customerId().getValue());
        if (query.contractId() != null) wrapper.eq("contract_id", query.contractId().getValue());
        if (query.planId() != null) wrapper.eq("receivable_plan_id", query.planId().getValue());
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        return wrapper;
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.ReceivableSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("receivable custom sort is invalid");
        }
        String scalar = """
                (SELECT receivable_field_sort.scalar_value
                FROM crm_receivable_field_value receivable_field_sort
                WHERE receivable_field_sort.tenant_id = crm_receivable.tenant_id
                  AND receivable_field_sort.receivable_id = crm_receivable.id
                  AND receivable_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private void applyCondition(QueryWrapper<ReceivablePO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("receivable filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            QueryFilterConditions.applyBuiltin(query, column, condition);
            return;
        }
        QueryFilterConditions.applyCustom(query, condition, "crm_receivable",
                "crm_receivable_field_value", "receivable_id");
    }

    @Override
    public ReceivableSummary summarize(TenantId tenantId, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        Map<String, Object> result = mapper.selectMaps(visible(tenantId, visibility)
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ReceivableSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public DashboardMetricSummary summarizeReceived(
            TenantId tenantId, ReceivableVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        if (range == null) {
            throw new IllegalArgumentException("receivable dashboard range is required");
        }
        Map<String, Object> result = mapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ReceivableStatus.ACTIVE.name())
                        .ge("received_date", range.from())
                        .le("received_date", range.to())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new DashboardMetricSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public ReceivableCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        Map<String, Object> result = mapper.selectMaps(visible(tenantId, visibility)
                        .eq("customer_id", customerId.getValue())
                        .eq("status", ReceivableStatus.ACTIVE.name())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ReceivableCustomerSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public List<ReportTrendPoint> reportActiveTrend(
            TenantId tenantId, ReceivableVisibility visibility,
            ReportDateRange range, ReportGranularity granularity) {
        requireTenant(tenantId);
        requireReport(range, granularity);
        String period = receivablePeriod(granularity);
        return mapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ReceivableStatus.ACTIVE.name())
                        .ge("received_date", range.from())
                        .le("received_date", range.to())
                        .select(period + " AS period_start",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(amount), 0) AS aggregate_amount")
                        .groupBy(period).orderByAsc(period))
                .stream().map(row -> new ReportTrendPoint(
                        date(row.get("period_start")), number(row.get("aggregate_count")),
                        decimal(row.get("aggregate_amount"))))
                .toList();
    }

    @Override
    public List<ReportRankingValue> reportActiveRanking(
            TenantId tenantId, ReceivableVisibility visibility,
            ReportDateRange range, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireReportLimit(limit);
        if (range == null) {
            throw new IllegalArgumentException("receivable report range is required");
        }
        return mapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ReceivableStatus.ACTIVE.name())
                        .ge("received_date", range.from())
                        .le("received_date", range.to())
                        .select("owner_actor_id AS owner_actor_id",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(amount), 0) AS aggregate_amount")
                        .groupBy("owner_actor_id")
                        .orderByDesc("aggregate_amount")
                        .orderByDesc("aggregate_count")
                        .orderByAsc("owner_actor_id")
                        .last("LIMIT " + boundedLimit))
                .stream().map(row -> new ReportRankingValue(
                        new MemberId(number(row.get("owner_actor_id"))),
                        number(row.get("aggregate_count")),
                        decimal(row.get("aggregate_amount"))))
                .toList();
    }

    @Override
    public List<CustomReportRow> runCustomReport(
            TenantId tenantId, ReceivableVisibility visibility, CustomReportQuery query) {
        return customReportQuery.receivable(tenantId, visibility, query);
    }

    @Override
    public BigDecimal sumActiveAmount(TenantId tenantId, ContractId contractId,
                                      ReceivableId excludingId) {
        return sum(tenantId, "contract_id", contractId == null ? null : contractId.getValue(), excludingId);
    }

    @Override
    public BigDecimal sumActiveAmountByPlan(TenantId tenantId, ReceivablePlanId planId,
                                            ReceivableId excludingId) {
        return sum(tenantId, "receivable_plan_id", planId == null ? null : planId.getValue(), excludingId);
    }

    @Override
    public Map<ReceivablePlanId, BigDecimal> sumActiveAmountsByPlan(
            TenantId tenantId, Collection<ReceivablePlanId> planIds) {
        requireTenant(tenantId);
        if (planIds == null) {
            throw new IllegalArgumentException("receivable plan amount batch is invalid");
        }
        List<Long> ids = planIds.stream().filter(java.util.Objects::nonNull)
                .map(ReceivablePlanId::getValue).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<ReceivablePlanId, BigDecimal> totals = new LinkedHashMap<>();
        for (int start = 0; start < ids.size(); start += 500) {
            List<Long> batch = ids.subList(start, Math.min(start + 500, ids.size()));
            mapper.selectMaps(new QueryWrapper<ReceivablePO>()
                            .select("receivable_plan_id AS plan_id",
                                    "COALESCE(SUM(amount), 0) AS total_amount")
                            .eq("tenant_id", tenantId.getValue())
                            .in("receivable_plan_id", batch)
                            .ne("status", ReceivableStatus.DELETED.name())
                            .groupBy("receivable_plan_id"))
                    .forEach(row -> totals.put(new ReceivablePlanId(number(row.get("plan_id"))),
                            decimal(row.get("total_amount"))));
        }
        return Map.copyOf(totals);
    }

    @Override
    public boolean existsByContract(TenantId tenantId, ContractId contractId) {
        requireTenant(tenantId);
        return contractId != null && mapper.selectCount(new LambdaQueryWrapper<ReceivablePO>()
                .eq(ReceivablePO::getTenantId, tenantId.getValue())
                .eq(ReceivablePO::getContractId, contractId.getValue())) > 0;
    }

    @Override
    public boolean existsByPlan(TenantId tenantId, ReceivablePlanId planId) {
        requireTenant(tenantId);
        return planId != null && mapper.selectCount(new LambdaQueryWrapper<ReceivablePO>()
                .eq(ReceivablePO::getTenantId, tenantId.getValue())
                .eq(ReceivablePO::getReceivablePlanId, planId.getValue())) > 0;
    }

    private BigDecimal sum(TenantId tenantId, String column, Long value, ReceivableId excludingId) {
        requireTenant(tenantId);
        if (value == null) throw new IllegalArgumentException("receivable relation is required");
        QueryWrapper<ReceivablePO> query = new QueryWrapper<ReceivablePO>()
                .select("COALESCE(SUM(amount), 0) AS total_amount")
                .eq("tenant_id", tenantId.getValue()).eq(column, value)
                .ne("status", ReceivableStatus.DELETED.name());
        if (excludingId != null) query.ne("id", excludingId.getValue());
        Object amount = mapper.selectMaps(query).stream().findFirst()
                .map(row -> row.get("total_amount")).orElse(BigDecimal.ZERO);
        return decimal(amount);
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private void synchronize(TenantId tenantId, ReceivableId id, Receivable value,
                             Map<FieldCode, FieldDefinition> fields) {
        fieldValueMapper.delete(new LambdaQueryWrapper<ReceivableFieldValuePO>()
                .eq(ReceivableFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ReceivableFieldValuePO::getReceivableId, id.getValue()));
        uniqueValueMapper.delete(new LambdaQueryWrapper<ReceivableUniqueFieldValuePO>()
                .eq(ReceivableUniqueFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ReceivableUniqueFieldValuePO::getReceivableId, id.getValue()));
        for (ReceivableFieldValue fieldValue : value.getCustomFieldValues()) {
            FieldDefinition field = fields.get(fieldValue.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("receivable field definition is missing");
            }
            fieldValueMapper.insert(fieldConverter.toPO(tenantId, id, fieldValue, field));
            if (!value.isDeleted() && field.isUnique()) {
                uniqueValueMapper.insert(unique(tenantId, id, field, fieldValue.values().getFirst()));
            }
        }
    }

    private List<Receivable> hydrated(TenantId tenantId, List<ReceivablePO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> ids = roots.stream().map(ReceivablePO::getId).distinct().toList();
        List<ReceivableFieldValuePO> values = fieldValueMapper.selectList(
                new LambdaQueryWrapper<ReceivableFieldValuePO>()
                        .eq(ReceivableFieldValuePO::getTenantId, tenantId.getValue())
                        .in(ReceivableFieldValuePO::getReceivableId, ids)
                        .orderByAsc(ReceivableFieldValuePO::getFieldId));
        Map<Long, FieldDefinition> fields = fields(tenantId, BusinessObjectType.RECEIVABLE,
                values.stream().map(ReceivableFieldValuePO::getFieldId).collect(Collectors.toSet()));
        Map<Long, List<ReceivableFieldValuePO>> byRoot = values.stream()
                .collect(Collectors.groupingBy(ReceivableFieldValuePO::getReceivableId));
        return roots.stream().map(root -> converter.toDomain(root,
                byRoot.getOrDefault(root.getId(), List.of()), fields)).toList();
    }

    private Map<Long, FieldDefinition> fields(
            TenantId tenantId, BusinessObjectType type, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, type.name())
                .in(CustomFieldDefinitionPO::getId, ids)).stream()
                .map(fieldDefinitionConverter::toDomain).collect(Collectors.toMap(
                        value -> value.getId().getValue(), Function.identity()));
    }

    private QueryWrapper<ReceivablePO> visible(TenantId tenantId, ReceivableVisibility visibility) {
        if (visibility == null) throw new IllegalArgumentException("receivable visibility is required");
        QueryWrapper<ReceivablePO> query = new QueryWrapper<ReceivablePO>()
                .eq("tenant_id", tenantId.getValue())
                .ne("status", ReceivableStatus.DELETED.name());
        if (visibility.dataScope() == DataScope.TENANT) return query;
        if (visibility.dataScope() == DataScope.SELF) {
            return query.eq("owner_actor_id", visibility.callerMemberId().getValue());
        }
        List<Long> departments = visibility.ownerDepartmentIds().stream()
                .map(DepartmentId::getValue).sorted().toList();
        return query.apply("""
                EXISTS (SELECT 1 FROM organization_membership owner_membership
                 WHERE owner_membership.tenant_id = crm_receivable.tenant_id
                   AND owner_membership.actor_id = crm_receivable.owner_actor_id
                   AND owner_membership.status = 'ACTIVE'
                   AND owner_membership.department_id IN (%s))
                """.formatted(placeholders(departments.size())), departments.toArray());
    }

    private String receivablePeriod(ReportGranularity granularity) {
        if (granularity == ReportGranularity.DAY) {
            return "received_date";
        }
        if (granularity == ReportGranularity.WEEK) {
            return "DATE_SUB(received_date, INTERVAL WEEKDAY(received_date) DAY)";
        }
        if (granularity == ReportGranularity.MONTH) {
            return "DATE_SUB(received_date, "
                    + "INTERVAL (DAYOFMONTH(received_date) - 1) DAY)";
        }
        throw new IllegalArgumentException("receivable report granularity is invalid");
    }

    private void requireReport(ReportDateRange range, ReportGranularity granularity) {
        if (range == null || granularity == null) {
            throw new IllegalArgumentException("receivable report query is invalid");
        }
    }

    private int requireReportLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("receivable report limit is invalid");
        }
        return limit;
    }

    private LocalDate date(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value == null) {
            throw new IllegalStateException("receivable report period is missing");
        }
        return LocalDate.parse(value.toString());
    }

    private ReceivableUniqueFieldValuePO unique(
            TenantId tenantId, ReceivableId id, FieldDefinition field, String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
        ReceivableUniqueFieldValuePO po = new ReceivableUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue()); po.setReceivableId(id.getValue());
        po.setObjectType(BusinessObjectType.RECEIVABLE.name());
        po.setFieldId(field.getId().getValue()); po.setNormalizedValue(normalized);
        po.setNormalizedHash(hash(normalized)); po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private Map<FieldCode, FieldDefinition> definitions(Collection<FieldDefinition> fields) {
        if (fields == null) return Map.of();
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private String placeholders(int size) {
        return java.util.stream.IntStream.range(0, size).mapToObj(i -> "{" + i + "}")
                .collect(Collectors.joining(","));
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant is required");
    }
}
