package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReceivableFieldValue;
import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ReceivableDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.ReceivablePlanQuery;
import io.github.archetom.crm.domain.model.ReceivablePlanCustomerSummary;
import io.github.archetom.crm.domain.model.ReceivablePlanSummary;
import io.github.archetom.crm.domain.model.ReceivablePlanStatus;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.ReceivablePlanFieldCatalog;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ReceivablePlanId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.ReceivableFieldValuePOConverter;
import io.github.archetom.crm.infra.persistence.converter.ReceivablePlanPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivablePlanFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivablePlanMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReceivablePlanUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.support.QueryFilterConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReceivablePlanRepositoryImpl implements ReceivablePlanRepository {
    private static final int AVAILABLE_LIMIT = 500;
    private static final Map<FieldCode, String> BUILTIN_COLUMNS = Map.ofEntries(
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.NUMBER), "installment"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.CUSTOMER), "customer_id"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.CONTRACT), "contract_id"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.DUE_DATE), "due_date"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.PLANNED_AMOUNT), "planned_amount"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.PAYMENT_METHOD), "payment_method"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.REMINDER_DAYS), "reminder_days"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.REMARK), "remark"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.OWNER_ACTOR), "owner_actor_id"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.STATUS), "status"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.CREATED_TIME), "created_time"),
            Map.entry(new FieldCode(ReceivablePlanFieldCatalog.UPDATED_TIME), "updated_time"));

    private final ReceivablePlanMapper mapper;
    private final ReceivablePlanFieldValueMapper fieldValueMapper;
    private final ReceivablePlanUniqueFieldValueMapper uniqueValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final ReceivablePlanPOConverter converter;
    private final ReceivableFieldValuePOConverter fieldConverter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;

    @Override
    public ReceivablePlan save(TenantId tenantId, ReceivablePlan value,
                               Collection<FieldDefinition> fieldDefinitions) {
        requireTenant(tenantId);
        if (value == null || !tenantId.sameValueAs(value.getTenantId())) {
            throw new IllegalArgumentException("receivable plan tenant does not match");
        }
        ReceivablePlanPO po = converter.toPO(value);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) throw new IllegalStateException("receivable plan insert failed");
            } else if (mapper.update(po, new LambdaUpdateWrapper<ReceivablePlanPO>()
                    .eq(ReceivablePlanPO::getTenantId, tenantId.getValue())
                    .eq(ReceivablePlanPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            ReceivablePlanId id = new ReceivablePlanId(po.getId());
            synchronize(tenantId, id, value, definitions(fieldDefinitions));
            value.onPersisted(id, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ReceivableDomainException("receivable plan already exists");
        }
    }

    @Override
    public Optional<ReceivablePlan> findById(TenantId tenantId, ReceivablePlanId id) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePlanPO po = mapper.selectOne(new LambdaQueryWrapper<ReceivablePlanPO>()
                .eq(ReceivablePlanPO::getTenantId, tenantId.getValue())
                .eq(ReceivablePlanPO::getId, id.getValue())
                .ne(ReceivablePlanPO::getStatus, ReceivablePlanStatus.DELETED.name()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<ReceivablePlan> findByIdForAttachmentMutation(TenantId tenantId, ReceivablePlanId id) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePlanPO po = mapper.selectOne(new LambdaQueryWrapper<ReceivablePlanPO>()
                .eq(ReceivablePlanPO::getTenantId, tenantId.getValue())
                .eq(ReceivablePlanPO::getId, id.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<ReceivablePlan> findVisibleById(
            TenantId tenantId, ReceivablePlanId id, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePlanPO po = mapper.selectOne(visible(tenantId, visibility).eq("id", id.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<ReceivablePlan> findVisibleByIdForUpdate(
            TenantId tenantId, ReceivablePlanId id, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReceivablePlanPO po = mapper.selectOne(visible(tenantId, visibility)
                .eq("id", id.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ReceivablePlanId> ids,
            ReceivableVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("receivableplan visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(ReceivablePlanId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return mapper.selectList(visible(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(ReceivablePlanPO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<ReceivablePlan> findVisibleByIds(
            TenantId tenantId, Collection<ReceivablePlanId> ids,
            ReceivableVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("receivable plan visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(ReceivablePlanId::getValue).distinct().toList();
        return values.isEmpty() ? List.of() : hydrated(tenantId, mapper.selectList(
                visible(tenantId, visibility).in("id", values).orderByAsc("id")
                        .last("LIMIT " + limit)));
    }

    @Override
    public PageResult<ReceivablePlan> findPlans(TenantId tenantId, ReceivablePlanQuery query) {
        requireTenant(tenantId);
        if (query == null) throw new IllegalArgumentException("receivable plan query is required");
        QueryWrapper<ReceivablePlanPO> wrapper = queryWrapper(tenantId, query);
        wrapper.orderByAsc("due_date").orderByAsc("installment").orderByAsc("id");
        IPage<ReceivablePlanPO> page = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                hydrated(tenantId, page.getRecords()));
    }

    @Override
    public List<ReceivablePlan> findExportPlans(
            TenantId tenantId, ReceivablePlanQuery query,
            ReceivableVisibility exportVisibility, int limit) {
        requireTenant(tenantId);
        if (query == null || exportVisibility == null || limit < 1 || limit > 5_001) {
            throw new IllegalArgumentException("receivable plan export query is invalid");
        }
        QueryWrapper<ReceivablePlanPO> wrapper = queryWrapper(tenantId, query);
        applyOwnerVisibility(wrapper, exportVisibility);
        wrapper.orderByAsc("due_date").orderByAsc("installment").orderByAsc("id")
                .last("LIMIT " + limit);
        return hydrated(tenantId, mapper.selectList(wrapper));
    }

    @Override
    public ReceivablePlanSummary summarize(
            TenantId tenantId, ReceivablePlanQuery query, ReceivableVisibility receivableVisibility) {
        requireTenant(tenantId);
        if (query == null || receivableVisibility == null) {
            throw new IllegalArgumentException("receivable plan summary query is required");
        }
        String receiptVisibility = receiptVisibilityPredicate(receivableVisibility, "receipt");
        Map<String, Object> result = mapper.selectMaps(queryWrapper(tenantId, query)
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(planned_amount), 0) AS planned_amount",
                                "COALESCE(SUM(COALESCE((SELECT SUM(receipt.amount) "
                                        + "FROM crm_receivable receipt "
                                        + "WHERE receipt.tenant_id = crm_receivable_plan.tenant_id "
                                        + "AND receipt.receivable_plan_id = crm_receivable_plan.id "
                                        + "AND receipt.status <> 'DELETED' AND "
                                        + receiptVisibility + "), 0)), 0) AS received_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ReceivablePlanSummary(number(result.get("total_count")),
                decimal(result.get("planned_amount")), decimal(result.get("received_amount")));
    }

    @Override
    public ReceivablePlanCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        Map<String, Object> result = mapper.selectMaps(visible(tenantId, visibility)
                        .eq("customer_id", customerId.getValue())
                        .eq("status", ReceivablePlanStatus.ACTIVE.name())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(planned_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ReceivablePlanCustomerSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public DashboardMetricSummary summarizeDue(
            TenantId tenantId, ReceivableVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        if (range == null) {
            throw new IllegalArgumentException("receivable plan dashboard range is required");
        }
        Map<String, Object> result = mapper.selectMaps(visible(tenantId, visibility)
                        .ge("due_date", range.from())
                        .le("due_date", range.to())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(planned_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new DashboardMetricSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    private void applyKeyword(
            QueryWrapper<ReceivablePlanPO> wrapper, ReceivablePlanQuery query) {
        if (query.keywordCandidates().isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        if (query.keywordCandidates().containsKey(
                new FieldCode(ReceivablePlanFieldCatalog.NUMBER))) {
            try {
                wrapper.eq("installment", ReceivablePlanFieldCatalog.parseExactPlanNumber(
                        new SearchKeyword(query.keyword())));
            } catch (IllegalArgumentException exception) {
                wrapper.apply("1 = 0");
            }
        }
    }

    /** Builds the list predicate once so pagination and money aggregation cannot drift. */
    private QueryWrapper<ReceivablePlanPO> queryWrapper(TenantId tenantId, ReceivablePlanQuery query) {
        QueryWrapper<ReceivablePlanPO> wrapper = visible(tenantId, query.visibility());
        if (query.keyword() != null) {
            applyKeyword(wrapper, query);
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
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        return wrapper;
    }

    private void applyCondition(QueryWrapper<ReceivablePlanPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("receivable plan filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            QueryFilterConditions.applyBuiltin(query, column, condition);
            return;
        }
        QueryFilterConditions.applyCustom(query, condition, "crm_receivable_plan",
                "crm_receivable_plan_field_value", "receivable_plan_id");
    }

    /**
     * Uses only server-resolved visibility IDs in a fixed correlated-receipt predicate.
     * Query input never contributes a column, alias, or SQL fragment here.
     */
    private String receiptVisibilityPredicate(ReceivableVisibility visibility, String alias) {
        if (visibility.dataScope() == DataScope.TENANT) return "1 = 1";
        if (visibility.dataScope() == DataScope.SELF) {
            return alias + ".owner_actor_id = " + visibility.callerMemberId().getValue();
        }
        List<Long> departments = visibility.ownerDepartmentIds().stream()
                .map(DepartmentId::getValue).sorted().toList();
        if (departments.isEmpty()) return "1 = 0";
        String departmentIds = departments.stream().map(String::valueOf)
                .collect(Collectors.joining(","));
        return "EXISTS (SELECT 1 FROM organization_membership receipt_owner_membership "
                + "WHERE receipt_owner_membership.tenant_id = " + alias + ".tenant_id "
                + "AND receipt_owner_membership.actor_id = " + alias + ".owner_actor_id "
                + "AND receipt_owner_membership.status = 'ACTIVE' "
                + "AND receipt_owner_membership.department_id IN (" + departmentIds + "))";
    }

    @Override
    public List<ReceivablePlan> findAvailableByContract(
            TenantId tenantId, ContractId contractId,
            ReceivableVisibility visibility) {
        requireTenant(tenantId);
        if (contractId == null) {
            throw new IllegalArgumentException("available receivable plan query is invalid");
        }
        return hydrated(tenantId, mapper.selectList(visible(tenantId, visibility)
                .eq("contract_id", contractId.getValue())
                .apply("COALESCE((SELECT SUM(receivable.amount) FROM crm_receivable receivable "
                                + "WHERE receivable.tenant_id = {0} "
                                + "AND receivable.receivable_plan_id = crm_receivable_plan.id "
                                + "AND receivable.status <> {1}), 0) < planned_amount",
                        tenantId.getValue(), ReceivableStatus.DELETED.name())
                .orderByAsc("due_date").orderByAsc("installment").orderByAsc("id")
                .last("LIMIT " + AVAILABLE_LIMIT)));
    }

    @Override
    public BigDecimal sumActivePlannedAmount(
            TenantId tenantId, ContractId contractId, ReceivablePlanId excludingId) {
        requireTenant(tenantId);
        if (contractId == null) throw new IllegalArgumentException("contract ID is required");
        QueryWrapper<ReceivablePlanPO> query = new QueryWrapper<ReceivablePlanPO>()
                .select("COALESCE(SUM(planned_amount), 0) AS total_amount")
                .eq("tenant_id", tenantId.getValue()).eq("contract_id", contractId.getValue())
                .ne("status", ReceivablePlanStatus.DELETED.name());
        if (excludingId != null) query.ne("id", excludingId.getValue());
        Object amount = mapper.selectMaps(query).stream().findFirst()
                .map(row -> row.get("total_amount")).orElse(BigDecimal.ZERO);
        return amount instanceof BigDecimal decimal ? decimal : new BigDecimal(amount.toString());
    }

    @Override
    public int nextInstallment(TenantId tenantId, ContractId contractId) {
        requireTenant(tenantId);
        if (contractId == null) throw new IllegalArgumentException("contract ID is required");
        Object value = mapper.selectMaps(new QueryWrapper<ReceivablePlanPO>()
                .select("COALESCE(MAX(installment), 0) AS max_installment")
                .eq("tenant_id", tenantId.getValue()).eq("contract_id", contractId.getValue()))
                .stream().findFirst().map(row -> row.get("max_installment")).orElse(0);
        int maximum = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
        return Math.addExact(maximum, 1);
    }

    @Override
    public boolean existsByContract(TenantId tenantId, ContractId contractId) {
        requireTenant(tenantId);
        return contractId != null && mapper.selectCount(new LambdaQueryWrapper<ReceivablePlanPO>()
                .eq(ReceivablePlanPO::getTenantId, tenantId.getValue())
                .eq(ReceivablePlanPO::getContractId, contractId.getValue())) > 0;
    }

    private void synchronize(TenantId tenantId, ReceivablePlanId id, ReceivablePlan value,
                             Map<FieldCode, FieldDefinition> fields) {
        fieldValueMapper.delete(new LambdaQueryWrapper<ReceivablePlanFieldValuePO>()
                .eq(ReceivablePlanFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ReceivablePlanFieldValuePO::getReceivablePlanId, id.getValue()));
        uniqueValueMapper.delete(new LambdaQueryWrapper<ReceivablePlanUniqueFieldValuePO>()
                .eq(ReceivablePlanUniqueFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ReceivablePlanUniqueFieldValuePO::getReceivablePlanId, id.getValue()));
        for (ReceivableFieldValue fieldValue : value.getCustomFieldValues()) {
            FieldDefinition field = fields.get(fieldValue.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("receivable plan field definition is missing");
            }
            fieldValueMapper.insert(fieldConverter.toPlanPO(tenantId, id, fieldValue, field));
            if (!value.isDeleted() && field.isUnique()) {
                uniqueValueMapper.insert(unique(tenantId, id, field, fieldValue.values().getFirst()));
            }
        }
    }

    private List<ReceivablePlan> hydrated(TenantId tenantId, List<ReceivablePlanPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> ids = roots.stream().map(ReceivablePlanPO::getId).distinct().toList();
        List<ReceivablePlanFieldValuePO> values = fieldValueMapper.selectList(
                new LambdaQueryWrapper<ReceivablePlanFieldValuePO>()
                        .eq(ReceivablePlanFieldValuePO::getTenantId, tenantId.getValue())
                        .in(ReceivablePlanFieldValuePO::getReceivablePlanId, ids)
                        .orderByAsc(ReceivablePlanFieldValuePO::getFieldId));
        Map<Long, FieldDefinition> fields = fields(tenantId, values.stream()
                .map(ReceivablePlanFieldValuePO::getFieldId).collect(Collectors.toSet()));
        Map<Long, List<ReceivablePlanFieldValuePO>> byRoot = values.stream()
                .collect(Collectors.groupingBy(ReceivablePlanFieldValuePO::getReceivablePlanId));
        return roots.stream().map(root -> converter.toDomain(root,
                byRoot.getOrDefault(root.getId(), List.of()), fields)).toList();
    }

    private Map<Long, FieldDefinition> fields(TenantId tenantId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.RECEIVABLE_PLAN.name())
                .in(CustomFieldDefinitionPO::getId, ids)).stream()
                .map(fieldDefinitionConverter::toDomain).collect(Collectors.toMap(
                        value -> value.getId().getValue(), Function.identity()));
    }

    private QueryWrapper<ReceivablePlanPO> visible(
            TenantId tenantId, ReceivableVisibility visibility) {
        if (visibility == null) throw new IllegalArgumentException("receivable plan visibility is required");
        QueryWrapper<ReceivablePlanPO> query = new QueryWrapper<ReceivablePlanPO>()
                .eq("tenant_id", tenantId.getValue())
                .ne("status", ReceivablePlanStatus.DELETED.name());
        return applyOwnerVisibility(query, visibility);
    }

    /** Adds only the owner-scope predicate so independent read/export scopes are intersected. */
    private QueryWrapper<ReceivablePlanPO> applyOwnerVisibility(
            QueryWrapper<ReceivablePlanPO> query, ReceivableVisibility visibility) {
        if (visibility == null) throw new IllegalArgumentException("receivable plan visibility is required");
        if (visibility.dataScope() == DataScope.TENANT) return query;
        if (visibility.dataScope() == DataScope.SELF) {
            return query.eq("owner_actor_id", visibility.callerMemberId().getValue());
        }
        List<Long> departments = visibility.ownerDepartmentIds().stream()
                .map(DepartmentId::getValue).sorted().toList();
        return query.apply("""
                EXISTS (SELECT 1 FROM organization_membership owner_membership
                 WHERE owner_membership.tenant_id = crm_receivable_plan.tenant_id
                   AND owner_membership.actor_id = crm_receivable_plan.owner_actor_id
                   AND owner_membership.status = 'ACTIVE'
                   AND owner_membership.department_id IN (%s))
                """.formatted(placeholders(departments.size())), departments.toArray());
    }

    private ReceivablePlanUniqueFieldValuePO unique(
            TenantId tenantId, ReceivablePlanId id, FieldDefinition field, String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
        ReceivablePlanUniqueFieldValuePO po = new ReceivablePlanUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue()); po.setReceivablePlanId(id.getValue());
        po.setObjectType(BusinessObjectType.RECEIVABLE_PLAN.name());
        po.setFieldId(field.getId().getValue()); po.setNormalizedValue(normalized);
        po.setNormalizedHash(hash(normalized)); po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private Map<FieldCode, FieldDefinition> definitions(Collection<FieldDefinition> fields) {
        if (fields == null) return Map.of();
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
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
