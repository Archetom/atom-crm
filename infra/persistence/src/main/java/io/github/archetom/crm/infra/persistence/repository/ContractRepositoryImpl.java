package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.support.ConstraintViolations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.entity.ContractFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ContractAlreadyExistsException;
import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.ContractCustomerSummary;
import io.github.archetom.crm.domain.model.ContractExpirationSummary;
import io.github.archetom.crm.domain.model.ContractListSummary;
import io.github.archetom.crm.domain.model.ContractQuery;
import io.github.archetom.crm.domain.model.ContractSortDirection;
import io.github.archetom.crm.domain.model.ContractStatus;
import io.github.archetom.crm.domain.model.ContractSummary;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.model.ReceivableStatus;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ContractPOConverter;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractCollaboratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractProductLineMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractProductLinePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.query.MySqlCustomReportQuery;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import io.github.archetom.crm.infra.persistence.support.QueryFilterConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class ContractRepositoryImpl implements ContractRepository {

    private static final Map<FieldCode, String> BUILTIN_COLUMNS = Map.ofEntries(
            Map.entry(new FieldCode(ContractFieldCatalog.NAME), "name"),
            Map.entry(new FieldCode(ContractFieldCatalog.NUMBER), "contract_number"),
            Map.entry(new FieldCode(ContractFieldCatalog.CUSTOMER), "customer_id"),
            Map.entry(new FieldCode(ContractFieldCatalog.OPPORTUNITY), "opportunity_id"),
            Map.entry(new FieldCode(ContractFieldCatalog.CONTACT), "contact_id"),
            Map.entry(new FieldCode(ContractFieldCatalog.ORDER_DATE), "order_date"),
            Map.entry(new FieldCode(ContractFieldCatalog.START_DATE), "start_date"),
            Map.entry(new FieldCode(ContractFieldCatalog.END_DATE), "end_date"),
            Map.entry(new FieldCode(ContractFieldCatalog.CONTRACT_TYPE), "contract_type"),
            Map.entry(new FieldCode(ContractFieldCatalog.PAYMENT_TYPE), "payment_type"),
            Map.entry(new FieldCode(ContractFieldCatalog.REMARK), "remark"),
            Map.entry(new FieldCode(ContractFieldCatalog.SUBTOTAL), "subtotal"),
            Map.entry(new FieldCode(ContractFieldCatalog.DISCOUNT_RATE), "discount_rate"),
            Map.entry(new FieldCode(ContractFieldCatalog.TOTAL_AMOUNT), "total_amount"),
            Map.entry(new FieldCode(ContractFieldCatalog.OWNER_ACTOR), "owner_actor_id"),
            Map.entry(new FieldCode(ContractFieldCatalog.STATUS), "status"),
            Map.entry(new FieldCode(ContractFieldCatalog.CREATED_TIME), "created_time"),
            Map.entry(new FieldCode(ContractFieldCatalog.UPDATED_TIME), "updated_time"));

    private static final String OPPORTUNITY_CUSTOMER_FK =
            "fk_crm_contract_opportunity_customer";
    private static final String CONTACT_CUSTOMER_FK =
            "fk_crm_contract_contact_customer";
    private static final String RECEIVABLE_PLAN_CONTRACT_CUSTOMER_FK =
            "fk_crm_receivable_plan_contract_customer";
    private static final String RETURN_VISIT_CONTRACT_CUSTOMER_FK =
            "fk_crm_return_visit_contract_customer";

    private final ContractMapper contractMapper;
    private final ContractCollaboratorMapper collaboratorMapper;
    private final ContractProductLineMapper productMapper;
    private final ContractFieldValueMapper fieldValueMapper;
    private final ContractUniqueFieldValueMapper uniqueValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final ContractPOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final MySqlCustomReportQuery customReportQuery;

    @Override
    public Contract save(
            TenantId tenantId, Contract contract,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, contract);
        ContractPO po = converter.toPO(contract);
        Map<FieldCode, FieldDefinition> fields = definitions(fieldDefinitions);
        try {
            if (po.getId() == null) {
                if (contractMapper.insert(po) != 1) {
                    throw new IllegalStateException("Contract insert did not affect one row");
                }
            } else {
                LambdaUpdateWrapper<ContractPO> update = new LambdaUpdateWrapper<ContractPO>()
                        .eq(ContractPO::getTenantId, tenantId.getValue())
                        .eq(ContractPO::getId, po.getId());
                if (contractMapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }
            ContractId id = new ContractId(po.getId());
            synchronize(tenantId, id, contract, fields);
            contract.onPersisted(id, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return contract;
        } catch (DuplicateKeyException exception) {
            throw new ContractAlreadyExistsException();
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.causeContains(exception, OPPORTUNITY_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, CONTACT_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, RECEIVABLE_PLAN_CONTRACT_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, RETURN_VISIT_CONTRACT_CUSTOMER_FK)) {
                throw new ContractDomainException(
                        "contract relationship customer changed concurrently");
            }
            throw exception;
        }
    }


    @Override
    public Optional<Contract> findById(TenantId tenantId, ContractId contractId) {
        requireTenant(tenantId);
        if (contractId == null) {
            return Optional.empty();
        }
        ContractPO po = contractMapper.selectOne(root(tenantId)
                .eq(ContractPO::getId, contractId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contract> findByIdForUpdate(TenantId tenantId, ContractId contractId) {
        requireTenant(tenantId);
        if (contractId == null) {
            return Optional.empty();
        }
        ContractPO po = contractMapper.selectOne(root(tenantId)
                .eq(ContractPO::getId, contractId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contract> findVisibleById(
            TenantId tenantId, ContractId contractId, ContractVisibility visibility) {
        requireTenant(tenantId);
        if (contractId == null) {
            return Optional.empty();
        }
        ContractPO po = contractMapper.selectOne(visible(tenantId, visibility)
                .eq("id", contractId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contract> findVisibleByIdForUpdate(
            TenantId tenantId, ContractId contractId, ContractVisibility visibility) {
        requireTenant(tenantId);
        if (contractId == null) {
            return Optional.empty();
        }
        ContractPO po = contractMapper.selectOne(visible(tenantId, visibility)
                .eq("id", contractId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ContractId> ids,
            ContractVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("contract visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(ContractId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return contractMapper.selectList(visible(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(ContractPO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Contract> findVisibleByIds(
            TenantId tenantId, Collection<ContractId> contractIds,
            ContractVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (contractIds == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("contract visibility batch is invalid");
        }
        List<Long> ids = contractIds.stream().filter(java.util.Objects::nonNull)
                .map(ContractId::getValue).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return hydrated(tenantId, contractMapper.selectList(visible(tenantId, visibility)
                .in("id", ids).orderByAsc("id").last("LIMIT " + limit)));
    }

    @Override
    public PageResult<Contract> findContracts(TenantId tenantId, ContractQuery query) {
        requireTenant(tenantId);
        QueryWrapper<ContractPO> wrapper = queryWrapper(tenantId, query);
        boolean ascending = query.sort().direction() == ContractSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<ContractPO> page = contractMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0, page.getTotal()), hydrated(tenantId, page.getRecords()));
    }

    @Override
    public ContractListSummary summarize(
            TenantId tenantId, ContractQuery query, ReceivableVisibility receivableVisibility) {
        requireTenant(tenantId);
        if (receivableVisibility == null) {
            throw new IllegalArgumentException("receivable visibility is required");
        }
        QueryWrapper<ContractPO> wrapper = queryWrapper(tenantId, query).select(
                "COUNT(*) AS total_count",
                "COALESCE(SUM(total_amount), 0) AS total_amount",
                receivedAmountExpression(receivableVisibility));
        Map<String, Object> result = contractMapper.selectMaps(wrapper).stream()
                .findFirst().orElse(Map.of());
        return new ContractListSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")), decimal(result.get("received_amount")));
    }

    private QueryWrapper<ContractPO> queryWrapper(TenantId tenantId, ContractQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("contract query is required");
        }
        QueryWrapper<ContractPO> wrapper = visible(tenantId, query.visibility());
        applyKeyword(wrapper, query);
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.subordinateOwnerMemberIds() != null) {
            List<Long> ownerIds = query.subordinateOwnerMemberIds().stream()
                    .map(value -> value.getValue()).toList();
            if (ownerIds.isEmpty()) wrapper.apply("1 = 0");
            else wrapper.in("owner_actor_id", ownerIds);
        }
        if (query.customerId() != null) {
            wrapper.eq("customer_id", query.customerId().getValue());
        }
        if (query.opportunityId() != null) {
            wrapper.eq("opportunity_id", query.opportunityId().getValue());
        }
        if (query.status() != null) {
            wrapper.eq("status", query.status().name());
        }
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        return wrapper;
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.ContractSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("contract custom sort is invalid");
        }
        String scalar = """
                (SELECT contract_field_sort.scalar_value
                FROM crm_contract_field_value contract_field_sort
                WHERE contract_field_sort.tenant_id = crm_contract.tenant_id
                  AND contract_field_sort.contract_id = crm_contract.id
                  AND contract_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    /**
     * The correlated receipt aggregate keeps every contract-list predicate at the contract
     * root while independently narrowing receipt money to the caller's receivable scope.
     */
    private String receivedAmountExpression(ReceivableVisibility visibility) {
        String scope = switch (visibility.dataScope()) {
            case TENANT -> "";
            case SELF -> " AND contract_receivable.owner_actor_id = "
                    + visibility.callerMemberId().getValue();
            case DEPARTMENT, DEPARTMENT_SUBTREE -> """
                     AND EXISTS (SELECT 1 FROM organization_membership receipt_owner_membership
                      WHERE receipt_owner_membership.tenant_id = contract_receivable.tenant_id
                        AND receipt_owner_membership.actor_id = contract_receivable.owner_actor_id
                        AND receipt_owner_membership.status = 'ACTIVE'
                        AND receipt_owner_membership.department_id IN (%s))
                    """.formatted(visibility.ownerDepartmentIds().stream()
                    .map(DepartmentId::getValue).sorted().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        };
        return """
                COALESCE(SUM((
                    SELECT COALESCE(SUM(contract_receivable.amount), 0)
                    FROM crm_receivable contract_receivable
                    WHERE contract_receivable.tenant_id = crm_contract.tenant_id
                      AND contract_receivable.contract_id = crm_contract.id
                      AND contract_receivable.status = '%s'%s
                )), 0) AS received_amount
                """.formatted(ReceivableStatus.ACTIVE.name(), scope);
    }

    private void applyKeyword(QueryWrapper<ContractPO> wrapper, ContractQuery query) {
        if (query.keyword() == null) {
            return;
        }
        if (query.keywordCandidates().isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        boolean name = query.keywordCandidates().containsKey(
                new FieldCode(ContractFieldCatalog.NAME));
        boolean number = query.keywordCandidates().containsKey(
                new FieldCode(ContractFieldCatalog.NUMBER));
        wrapper.and(nested -> {
            if (name) {
                nested.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(query.keyword()));
            }
            if (number) {
                if (name) {
                    nested.or();
                }
                nested.apply("contract_number_key LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.normalizedIdentifierPrefixLiteral(
                                new SearchKeyword(query.keyword())));
            }
        });
    }

    private void applyCondition(QueryWrapper<ContractPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("contract filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            QueryFilterConditions.applyBuiltin(query, column, condition);
            return;
        }
        QueryFilterConditions.applyCustom(query, condition, "crm_contract",
                "crm_contract_field_value", "contract_id");
    }

    @Override
    public ContractSummary summarize(TenantId tenantId, ContractVisibility visibility) {
        requireTenant(tenantId);
        Map<String, Object> result = contractMapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ContractStatus.ACTIVE.name())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(total_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ContractSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public DashboardMetricSummary summarizeCreated(
            TenantId tenantId, ContractVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        if (range == null) {
            throw new IllegalArgumentException("contract dashboard range is required");
        }
        Map<String, Object> result = contractMapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ContractStatus.ACTIVE.name())
                        .ge("created_time", range.from().atStartOfDay())
                        .le("created_time", range.to().atTime(23, 59, 59, 999_999_000))
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(total_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new DashboardMetricSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public ContractExpirationSummary summarizeExpiration(
            TenantId tenantId, ContractVisibility visibility, ReportDateRange range,
            LocalDate asOfDate) {
        requireTenant(tenantId);
        if (range == null || asOfDate == null) {
            throw new IllegalArgumentException("contract expiration dashboard window is required");
        }
        long expiringCount = countEndingBetween(tenantId, visibility,
                range.from().isAfter(asOfDate) ? range.from() : asOfDate, range.to());
        LocalDate expiredTo = range.to().isBefore(asOfDate.minusDays(1))
                ? range.to() : asOfDate.minusDays(1);
        long expiredCount = range.from().isAfter(expiredTo)
                ? 0L : countEndingBetween(tenantId, visibility, range.from(), expiredTo);
        return new ContractExpirationSummary(expiringCount, expiredCount);
    }

    @Override
    public ContractCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ContractVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        Map<String, Object> result = contractMapper.selectMaps(visible(tenantId, visibility)
                        .eq("customer_id", customerId.getValue())
                        .eq("status", ContractStatus.ACTIVE.name())
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(total_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new ContractCustomerSummary(number(result.get("total_count")),
                decimal(result.get("total_amount")));
    }

    @Override
    public List<ReportTrendPoint> reportActiveTrend(
            TenantId tenantId, ContractVisibility visibility,
            ReportDateRange range, ReportGranularity granularity) {
        requireTenant(tenantId);
        requireReport(range, granularity);
        String period = contractPeriod(granularity);
        return contractMapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ContractStatus.ACTIVE.name())
                        .isNotNull("order_date")
                        .ge("order_date", range.from())
                        .le("order_date", range.to())
                        .select(period + " AS period_start",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(total_amount), 0) AS aggregate_amount")
                        .groupBy(period).orderByAsc(period))
                .stream().map(row -> new ReportTrendPoint(
                        date(row.get("period_start")), number(row.get("aggregate_count")),
                        decimal(row.get("aggregate_amount"))))
                .toList();
    }

    @Override
    public List<ReportRankingValue> reportActiveRanking(
            TenantId tenantId, ContractVisibility visibility,
            ReportDateRange range, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireReportLimit(limit);
        if (range == null) {
            throw new IllegalArgumentException("contract report range is required");
        }
        return contractMapper.selectMaps(visible(tenantId, visibility)
                        .eq("status", ContractStatus.ACTIVE.name())
                        .isNotNull("order_date")
                        .ge("order_date", range.from())
                        .le("order_date", range.to())
                        .select("owner_actor_id AS owner_actor_id",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(total_amount), 0) AS aggregate_amount")
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
            TenantId tenantId, ContractVisibility visibility, CustomReportQuery query) {
        return customReportQuery.contract(tenantId, visibility, query);
    }

    private void synchronize(
            TenantId tenantId, ContractId id, Contract contract,
            Map<FieldCode, FieldDefinition> fields) {
        collaboratorMapper.delete(new LambdaQueryWrapper<ContractCollaboratorPO>()
                .eq(ContractCollaboratorPO::getTenantId, tenantId.getValue())
                .eq(ContractCollaboratorPO::getContractId, id.getValue()));
        contract.getCollaborators().forEach(value ->
                collaboratorMapper.insert(converter.toPO(tenantId, id, value)));
        productMapper.delete(new LambdaQueryWrapper<ContractProductLinePO>()
                .eq(ContractProductLinePO::getTenantId, tenantId.getValue())
                .eq(ContractProductLinePO::getContractId, id.getValue()));
        contract.getProductLines().forEach(value ->
                productMapper.insert(converter.toPO(tenantId, id, value)));
        fieldValueMapper.delete(new LambdaQueryWrapper<ContractFieldValuePO>()
                .eq(ContractFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ContractFieldValuePO::getContractId, id.getValue()));
        uniqueValueMapper.delete(new LambdaQueryWrapper<ContractUniqueFieldValuePO>()
                .eq(ContractUniqueFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ContractUniqueFieldValuePO::getContractId, id.getValue()));
        for (ContractFieldValue value : contract.getCustomFieldValues()) {
            FieldDefinition field = fields.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("contract field definition is missing");
            }
            fieldValueMapper.insert(converter.toPO(tenantId, id, value, field));
            if (contract.isActive() && field.isUnique()) {
                uniqueValueMapper.insert(unique(tenantId, id, field, value.values().getFirst()));
            }
        }
    }

    private List<Contract> hydrated(TenantId tenantId, List<ContractPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roots.stream().map(ContractPO::getId).distinct().toList();
        List<ContractCollaboratorPO> collaborators = collaboratorMapper.selectList(
                new LambdaQueryWrapper<ContractCollaboratorPO>()
                        .eq(ContractCollaboratorPO::getTenantId, tenantId.getValue())
                        .in(ContractCollaboratorPO::getContractId, ids)
                        .orderByAsc(ContractCollaboratorPO::getActorId));
        List<ContractProductLinePO> products = productMapper.selectList(
                new LambdaQueryWrapper<ContractProductLinePO>()
                        .eq(ContractProductLinePO::getTenantId, tenantId.getValue())
                        .in(ContractProductLinePO::getContractId, ids)
                        .orderByAsc(ContractProductLinePO::getDisplayOrder));
        List<ContractFieldValuePO> values = fieldValueMapper.selectList(
                new LambdaQueryWrapper<ContractFieldValuePO>()
                        .eq(ContractFieldValuePO::getTenantId, tenantId.getValue())
                        .in(ContractFieldValuePO::getContractId, ids)
                        .orderByAsc(ContractFieldValuePO::getFieldId));
        Set<Long> fieldIds = values.stream().map(ContractFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fields = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.CONTRACT.name())
                .in(CustomFieldDefinitionPO::getId, fieldIds)).stream()
                .map(fieldDefinitionConverter::toDomain)
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
        Map<Long, List<ContractCollaboratorPO>> collaboratorMap = collaborators.stream()
                .collect(Collectors.groupingBy(ContractCollaboratorPO::getContractId));
        Map<Long, List<ContractProductLinePO>> productMap = products.stream()
                .collect(Collectors.groupingBy(ContractProductLinePO::getContractId));
        Map<Long, List<ContractFieldValuePO>> valueMap = values.stream()
                .collect(Collectors.groupingBy(ContractFieldValuePO::getContractId));
        return roots.stream().map(po -> converter.toDomain(po,
                collaboratorMap.getOrDefault(po.getId(), List.of()),
                productMap.getOrDefault(po.getId(), List.of()),
                valueMap.getOrDefault(po.getId(), List.of()), fields)).toList();
    }

    private LambdaQueryWrapper<ContractPO> root(TenantId tenantId) {
        return new LambdaQueryWrapper<ContractPO>()
                .eq(ContractPO::getTenantId, tenantId.getValue())
                .ne(ContractPO::getStatus, ContractStatus.DELETED.name());
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private long countEndingBetween(
            TenantId tenantId, ContractVisibility visibility, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return 0L;
        }
        Long count = contractMapper.selectCount(visible(tenantId, visibility)
                .eq("status", ContractStatus.ACTIVE.name())
                .ge("end_date", from)
                .le("end_date", to));
        return count == null ? 0L : count;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private QueryWrapper<ContractPO> visible(
            TenantId tenantId, ContractVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("contract visibility is required");
        }
        QueryWrapper<ContractPO> query = new QueryWrapper<ContractPO>()
                .eq("tenant_id", tenantId.getValue())
                .ne("status", ContractStatus.DELETED.name());
        if (visibility.dataScope() == DataScope.TENANT) {
            return query;
        }
        query.and(nested -> {
            if (visibility.dataScope() == DataScope.SELF) {
                nested.eq("owner_actor_id", visibility.callerMemberId().getValue());
            } else {
                List<Long> departments = visibility.ownerDepartmentIds().stream()
                        .map(DepartmentId::getValue).sorted().toList();
                nested.apply("""
                        EXISTS (SELECT 1 FROM organization_membership owner_membership
                         WHERE owner_membership.tenant_id = crm_contract.tenant_id
                           AND owner_membership.actor_id = crm_contract.owner_actor_id
                           AND owner_membership.status = 'ACTIVE'
                           AND owner_membership.department_id IN (%s))
                        """.formatted(placeholders(departments.size())), departments.toArray());
            }
            if (visibility.accessMode() != ContractAccessMode.OWNER_SCOPE) {
                String write = visibility.accessMode() == ContractAccessMode.WRITE
                        ? " AND collaborator.access_level = 'WRITE'" : "";
                nested.or().apply("""
                        EXISTS (SELECT 1 FROM crm_contract_collaborator collaborator
                         WHERE collaborator.tenant_id = crm_contract.tenant_id
                           AND collaborator.contract_id = crm_contract.id
                           AND collaborator.actor_id = {0}
                           AND (collaborator.effective_until IS NULL
                                OR collaborator.effective_until >= CURRENT_DATE)%s)
                        """.formatted(write), visibility.callerMemberId().getValue());
            }
        });
        return query;
    }

    private String contractPeriod(ReportGranularity granularity) {
        if (granularity == ReportGranularity.DAY) {
            return "order_date";
        }
        if (granularity == ReportGranularity.WEEK) {
            return "DATE_SUB(order_date, INTERVAL WEEKDAY(order_date) DAY)";
        }
        if (granularity == ReportGranularity.MONTH) {
            return "DATE_SUB(order_date, "
                    + "INTERVAL (DAYOFMONTH(order_date) - 1) DAY)";
        }
        throw new IllegalArgumentException("contract report granularity is invalid");
    }

    private void requireReport(ReportDateRange range, ReportGranularity granularity) {
        if (range == null || granularity == null) {
            throw new IllegalArgumentException("contract report query is invalid");
        }
    }

    private int requireReportLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("contract report limit is invalid");
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
            throw new IllegalStateException("contract report period is missing");
        }
        return LocalDate.parse(value.toString());
    }

    private ContractUniqueFieldValuePO unique(
            TenantId tenantId, ContractId id, FieldDefinition field, String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
        ContractUniqueFieldValuePO po = new ContractUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setContractId(id.getValue());
        po.setObjectType(BusinessObjectType.CONTRACT.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedValue(normalized);
        po.setNormalizedHash(hash(normalized));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private Map<FieldCode, FieldDefinition> definitions(Collection<FieldDefinition> values) {
        if (values == null) {
            return Map.of();
        }
        return values.stream().collect(Collectors.toMap(FieldDefinition::getCode,
                Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private void requireTenantMatch(TenantId tenantId, Contract contract) {
        requireTenant(tenantId);
        if (contract == null || contract.getTenantId() == null
                || !tenantId.sameValueAs(contract.getTenantId())) {
            throw new IllegalArgumentException("contract tenant does not match");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant is required");
        }
    }

    private String placeholders(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(index -> "{" + index + "}").collect(Collectors.joining(","));
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
