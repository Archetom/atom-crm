package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.support.ConstraintViolations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OpportunityFieldValue;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OpportunityAlreadyExistsException;
import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.DashboardSalesFunnelValue;
import io.github.archetom.crm.domain.model.CustomReportQuery;
import io.github.archetom.crm.domain.model.CustomReportRow;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityCustomerSummary;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityQuery;
import io.github.archetom.crm.domain.model.OpportunitySortDirection;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.model.OpportunitySummary;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.model.ReportRankingValue;
import io.github.archetom.crm.domain.model.ReportTrendPoint;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.OpportunityPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityCollaboratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityContactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityProductLineMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityProductLinePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.query.MySqlCustomReportQuery;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Opportunity repository with an explicit tenant predicate on every root and child query. */
@Repository
@RequiredArgsConstructor
public class OpportunityRepositoryImpl implements OpportunityRepository {

    private static final String OPPORTUNITY_CONTACT_ROOT_CUSTOMER_FK =
            "fk_crm_opportunity_contact_root_customer";
    private static final String OPPORTUNITY_CONTACT_TARGET_CUSTOMER_FK =
            "fk_crm_opportunity_contact_target_customer";

    private static final String CONTRACT_CUSTOMER_FK =
            "fk_crm_contract_opportunity_customer";
    private static final String PIPELINE_STAGE_PAIR_FK =
            "fk_crm_opportunity_pipeline_stage_pair";
    private static final int MAX_INTERNAL_LIMIT = 2_000;
    private static final Map<FieldCode, String> BUILTIN_COLUMNS = builtinColumns();

    private final OpportunityMapper opportunityMapper;
    private final OpportunityContactMapper contactMapper;
    private final OpportunityCollaboratorMapper collaboratorMapper;
    private final OpportunityFieldValueMapper fieldValueMapper;
    private final OpportunityUniqueFieldValueMapper uniqueFieldValueMapper;
    private final OpportunityProductLineMapper productLineMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final OpportunityPOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final ObjectMapper objectMapper;
    private final MySqlCustomReportQuery customReportQuery;

    @Override
    public Opportunity save(
            TenantId tenantId, Opportunity opportunity,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, opportunity);
        OpportunityPO po = converter.toPO(opportunity);
        Map<FieldCode, FieldDefinition> fieldsByCode = definitionsByCode(fieldDefinitions);
        try {
            if (po.getId() == null) {
                if (opportunityMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "Opportunity insert did not affect exactly one row");
                }
            } else {
                LambdaUpdateWrapper<OpportunityPO> update =
                        new LambdaUpdateWrapper<OpportunityPO>()
                                .eq(OpportunityPO::getTenantId, tenantId.getValue())
                                .eq(OpportunityPO::getId, po.getId());
                if (opportunityMapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }
            OpportunityId opportunityId = new OpportunityId(po.getId());
            synchronizeChildren(tenantId, opportunityId, opportunity, fieldsByCode);
            opportunity.onPersisted(opportunityId, po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return opportunity;
        } catch (DuplicateKeyException exception) {
            throw new OpportunityAlreadyExistsException();
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.causeContains(exception, CONTRACT_CUSTOMER_FK)) {
                throw new OpportunityDomainException(
                        "opportunity customer cannot change while referenced by a contract");
            }
            if (ConstraintViolations.causeContains(exception, OPPORTUNITY_CONTACT_ROOT_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, OPPORTUNITY_CONTACT_TARGET_CUSTOMER_FK)) {
                throw new OpportunityDomainException(
                        "opportunity customer and related contacts must match");
            }
            if (ConstraintViolations.causeContains(exception, PIPELINE_STAGE_PAIR_FK)) {
                throw new OpportunityDomainException(
                        "opportunity pipeline stage is unavailable");
            }
            throw exception;
        }
    }


    @Override
    public Optional<Opportunity> findById(
            TenantId tenantId, OpportunityId opportunityId) {
        requireTenant(tenantId);
        if (opportunityId == null) {
            return Optional.empty();
        }
        OpportunityPO po = opportunityMapper.selectOne(tenantRootFilter(tenantId)
                .eq(OpportunityPO::getId, opportunityId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public Optional<Opportunity> findByIdForAttachmentMutation(
            TenantId tenantId, OpportunityId opportunityId) {
        requireTenant(tenantId);
        if (opportunityId == null) return Optional.empty();
        OpportunityPO po = opportunityMapper.selectOne(tenantRootFilter(tenantId)
                .eq(OpportunityPO::getId, opportunityId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public Optional<Opportunity> findVisibleById(
            TenantId tenantId, OpportunityId opportunityId,
            OpportunityVisibility visibility) {
        requireTenant(tenantId);
        if (opportunityId == null) {
            return Optional.empty();
        }
        OpportunityPO po = opportunityMapper.selectOne(
                visibleRootQuery(tenantId, visibility)
                        .eq("id", opportunityId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public Optional<Opportunity> findVisibleByIdForUpdate(
            TenantId tenantId, OpportunityId opportunityId, OpportunityVisibility visibility) {
        requireTenant(tenantId);
        if (opportunityId == null) return Optional.empty();
        OpportunityPO po = opportunityMapper.selectOne(
                visibleRootQuery(tenantId, visibility)
                        .eq("id", opportunityId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po))
                .stream().findFirst();
    }

    @Override
    public PageResult<Opportunity> findOpportunities(
            TenantId tenantId, OpportunityQuery query) {
        requireTenant(tenantId);
        QueryWrapper<OpportunityPO> wrapper = queryWrapper(tenantId, query);
        boolean ascending = query.sort().direction() == OpportunitySortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<OpportunityPO> result = opportunityMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0L, result.getTotal()), hydrated(tenantId, result.getRecords()));
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<OpportunityId> ids,
            OpportunityVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("opportunity visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(OpportunityId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return opportunityMapper.selectList(visibleRootQuery(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(OpportunityPO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Opportunity> findVisibleByIds(
            TenantId tenantId, Collection<OpportunityId> opportunityIds,
            OpportunityVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        List<Long> ids = ids(opportunityIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<OpportunityPO> query = visibleRootQuery(tenantId, visibility)
                .in("id", ids).orderByAsc("id").last("LIMIT " + boundedLimit);
        return hydrated(tenantId, opportunityMapper.selectList(query));
    }

    @Override
    public OpportunitySummary summarize(
            TenantId tenantId, OpportunityQuery query) {
        requireTenant(tenantId);
        QueryWrapper<OpportunityPO> wrapper = queryWrapper(tenantId, query)
                .select("COUNT(*) AS total_count",
                        "COALESCE(SUM(estimated_amount), 0) AS estimated_amount");
        Map<String, Object> result = opportunityMapper.selectMaps(wrapper).stream()
                .findFirst().orElse(Map.of());
        return new OpportunitySummary(number(result.get("total_count")).longValue(),
                decimal(result.get("estimated_amount")));
    }

    @Override
    public DashboardMetricSummary summarizeCreated(
            TenantId tenantId, OpportunityVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        if (range == null) {
            throw new IllegalArgumentException("opportunity dashboard range is required");
        }
        QueryWrapper<OpportunityPO> query = visibleRootQuery(tenantId, visibility)
                .ge("created_time", range.from().atStartOfDay());
        applyDateTimeUpperBound(query, "created_time", range.to());
        Map<String, Object> result = opportunityMapper.selectMaps(query
                        .select("COUNT(*) AS total_count",
                                "COALESCE(SUM(estimated_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new DashboardMetricSummary(number(result.get("total_count")).longValue(),
                decimal(result.get("total_amount")));
    }

    @Override
    public DashboardMetricSummary summarizeOutcome(
            TenantId tenantId, OpportunityVisibility visibility,
            OpportunityOutcome outcome, ReportDateRange range) {
        requireTenant(tenantId);
        if (outcome == null || outcome == OpportunityOutcome.OPEN || range == null) {
            throw new IllegalArgumentException("opportunity outcome dashboard query is invalid");
        }
        QueryWrapper<OpportunityPO> query = visibleRootQuery(tenantId, visibility)
                .eq("outcome", outcome.name())
                .isNotNull("outcome_time")
                .ge("outcome_time", range.from().atStartOfDay());
        applyDateTimeUpperBound(query, "outcome_time", range.to());
        Map<String, Object> result = opportunityMapper.selectMaps(query
                        .select("COUNT(*) AS total_count"))
                .stream().findFirst().orElse(Map.of());
        return new DashboardMetricSummary(number(result.get("total_count")).longValue(),
                BigDecimal.ZERO.setScale(2));
    }

    @Override
    public List<DashboardSalesFunnelValue> summarizeSalesFunnel(
            TenantId tenantId, OpportunityVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        if (range == null) {
            throw new IllegalArgumentException("opportunity dashboard range is required");
        }
        QueryWrapper<OpportunityPO> query = visibleRootQuery(tenantId, visibility)
                .ge("created_time", range.from().atStartOfDay());
        applyDateTimeUpperBound(query, "created_time", range.to());
        return opportunityMapper.selectMaps(query
                        .select("pipeline_code AS pipeline_code", "stage_code AS stage_code",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(estimated_amount), 0) AS aggregate_amount")
                        .groupBy("pipeline_code", "stage_code")
                        .orderByAsc("pipeline_code").orderByAsc("stage_code"))
                .stream().map(row -> new DashboardSalesFunnelValue(
                        string(row.get("pipeline_code")), string(row.get("stage_code")),
                        number(row.get("aggregate_count")).longValue(),
                        decimal(row.get("aggregate_amount"))))
                .toList();
    }

    @Override
    public OpportunityCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, OpportunityVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        Map<String, Object> result = opportunityMapper.selectMaps(
                        visibleRootQuery(tenantId, visibility)
                                .eq("customer_id", customerId.getValue())
                                .eq("outcome", OpportunityOutcome.OPEN.name())
                                .select("COUNT(*) AS total_count",
                                        "COALESCE(SUM(estimated_amount), 0) AS total_amount"))
                .stream().findFirst().orElse(Map.of());
        return new OpportunityCustomerSummary(number(result.get("total_count")).longValue(),
                decimal(result.get("total_amount")));
    }

    @Override
    public List<ReportTrendPoint> reportWonTrend(
            TenantId tenantId, OpportunityVisibility visibility,
            ReportDateRange range, ReportGranularity granularity) {
        requireTenant(tenantId);
        requireReport(range, granularity);
        String period = opportunityPeriod(granularity);
        QueryWrapper<OpportunityPO> query = visibleRootQuery(tenantId, visibility)
                .eq("outcome", "WON")
                .isNotNull("outcome_time")
                .ge("outcome_time", range.from().atStartOfDay());
        applyDateTimeUpperBound(query, "outcome_time", range.to());
        return opportunityMapper.selectMaps(query
                        .select(period + " AS period_start",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(estimated_amount), 0) AS aggregate_amount")
                        .groupBy(period).orderByAsc(period))
                .stream().map(row -> new ReportTrendPoint(
                        date(row.get("period_start")),
                        number(row.get("aggregate_count")).longValue(),
                        decimal(row.get("aggregate_amount"))))
                .toList();
    }

    @Override
    public List<ReportRankingValue> reportWonRanking(
            TenantId tenantId, OpportunityVisibility visibility,
            ReportDateRange range, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireReportLimit(limit);
        if (range == null) {
            throw new IllegalArgumentException("opportunity report range is required");
        }
        QueryWrapper<OpportunityPO> query = visibleRootQuery(tenantId, visibility)
                .eq("outcome", "WON")
                .isNotNull("outcome_time")
                .ge("outcome_time", range.from().atStartOfDay());
        applyDateTimeUpperBound(query, "outcome_time", range.to());
        return opportunityMapper.selectMaps(query
                        .select("owner_actor_id AS owner_actor_id",
                                "COUNT(*) AS aggregate_count",
                                "COALESCE(SUM(estimated_amount), 0) AS aggregate_amount")
                        .groupBy("owner_actor_id")
                        .orderByDesc("aggregate_amount")
                        .orderByDesc("aggregate_count")
                        .orderByAsc("owner_actor_id")
                        .last("LIMIT " + boundedLimit))
                .stream().map(row -> new ReportRankingValue(
                        new MemberId(number(row.get("owner_actor_id")).longValue()),
                        number(row.get("aggregate_count")).longValue(),
                        decimal(row.get("aggregate_amount"))))
                .toList();
    }

    @Override
    public List<CustomReportRow> runCustomReport(
            TenantId tenantId, OpportunityVisibility visibility, CustomReportQuery query) {
        return customReportQuery.opportunity(tenantId, visibility, query);
    }

    private QueryWrapper<OpportunityPO> queryWrapper(
            TenantId tenantId, OpportunityQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("opportunity query must not be null");
        }
        QueryWrapper<OpportunityPO> wrapper = visibleRootQuery(
                tenantId, query.visibility());
        if (query.keyword() != null) {
            if (query.keywordCandidates().isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'",
                        SqlLikePattern.literal(query.keyword()));
            }
        }
        if (query.name() != null) {
            wrapper.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'",
                    SqlLikePattern.literal(query.name()));
        }
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.customerId() != null) {
            wrapper.eq("customer_id", query.customerId().getValue());
        }
        if (query.outcome() != null) {
            wrapper.eq("outcome", query.outcome().name());
        }
        applyStarred(wrapper, query.visibility(), query.starred());
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        return wrapper;
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.OpportunitySort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("opportunity custom sort is invalid");
        }
        String scalar = """
                (SELECT opportunity_field_sort.scalar_value
                FROM crm_opportunity_field_value opportunity_field_sort
                WHERE opportunity_field_sort.tenant_id = crm_opportunity.tenant_id
                  AND opportunity_field_sort.opportunity_id = crm_opportunity.id
                  AND opportunity_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private void synchronizeChildren(
            TenantId tenantId, OpportunityId opportunityId,
            Opportunity opportunity, Map<FieldCode, FieldDefinition> fieldsByCode) {
        contactMapper.delete(childFilter(tenantId, opportunityId,
                OpportunityContactPO::getTenantId,
                OpportunityContactPO::getOpportunityId));
        opportunity.getContactIds().forEach(contactId -> contactMapper.insert(
                converter.toPO(tenantId, opportunityId, opportunity.getCustomerId(), contactId,
                        opportunity.getPrimaryContactId())));

        collaboratorMapper.delete(childFilter(tenantId, opportunityId,
                OpportunityCollaboratorPO::getTenantId,
                OpportunityCollaboratorPO::getOpportunityId));
        opportunity.getCollaborators().forEach(value -> collaboratorMapper.insert(
                converter.toPO(tenantId, opportunityId, value)));

        productLineMapper.delete(childFilter(tenantId, opportunityId,
                OpportunityProductLinePO::getTenantId,
                OpportunityProductLinePO::getOpportunityId));
        opportunity.getProductLines().forEach(value -> productLineMapper.insert(
                converter.toPO(tenantId, opportunityId, value)));

        fieldValueMapper.delete(childFilter(tenantId, opportunityId,
                OpportunityFieldValuePO::getTenantId,
                OpportunityFieldValuePO::getOpportunityId));
        uniqueFieldValueMapper.delete(childFilter(tenantId, opportunityId,
                OpportunityUniqueFieldValuePO::getTenantId,
                OpportunityUniqueFieldValuePO::getOpportunityId));
        for (OpportunityFieldValue value : opportunity.getCustomFieldValues()) {
            FieldDefinition field = fieldsByCode.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException(
                        "opportunity field definition is missing");
            }
            fieldValueMapper.insert(converter.toPO(
                    tenantId, opportunityId, value, field));
            if (opportunity.isActive() && field.isUnique()) {
                uniqueFieldValueMapper.insert(uniqueValue(
                        tenantId, opportunityId, field, value.values().getFirst()));
            }
        }
    }

    private List<Opportunity> hydrated(
            TenantId tenantId, List<OpportunityPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> rootIds = roots.stream().map(OpportunityPO::getId).distinct().toList();
        List<OpportunityContactPO> contacts = contactMapper.selectList(
                childListFilter(tenantId, rootIds,
                        OpportunityContactPO::getTenantId,
                        OpportunityContactPO::getOpportunityId)
                        .orderByDesc(OpportunityContactPO::getPrimaryContact)
                        .orderByAsc(OpportunityContactPO::getContactId));
        List<OpportunityCollaboratorPO> collaborators = collaboratorMapper.selectList(
                childListFilter(tenantId, rootIds,
                        OpportunityCollaboratorPO::getTenantId,
                        OpportunityCollaboratorPO::getOpportunityId)
                        .orderByAsc(OpportunityCollaboratorPO::getActorId));
        List<OpportunityFieldValuePO> values = fieldValueMapper.selectList(
                childListFilter(tenantId, rootIds,
                        OpportunityFieldValuePO::getTenantId,
                        OpportunityFieldValuePO::getOpportunityId)
                        .orderByAsc(OpportunityFieldValuePO::getFieldId));
        List<OpportunityProductLinePO> productLines = productLineMapper.selectList(
                childListFilter(tenantId, rootIds,
                        OpportunityProductLinePO::getTenantId,
                        OpportunityProductLinePO::getOpportunityId)
                        .orderByAsc(OpportunityProductLinePO::getDisplayOrder));
        Set<Long> fieldIds = values.stream().map(OpportunityFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fieldsById = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                                .eq(CustomFieldDefinitionPO::getObjectType,
                                        BusinessObjectType.OPPORTUNITY.name())
                                .in(CustomFieldDefinitionPO::getId, fieldIds))
                        .stream().map(fieldDefinitionConverter::toDomain)
                        .collect(Collectors.toMap(
                                field -> field.getId().getValue(), Function.identity()));
        Map<Long, List<OpportunityContactPO>> contactMap = group(
                contacts, OpportunityContactPO::getOpportunityId);
        Map<Long, List<OpportunityCollaboratorPO>> collaboratorMap = group(
                collaborators, OpportunityCollaboratorPO::getOpportunityId);
        Map<Long, List<OpportunityFieldValuePO>> valueMap = group(
                values, OpportunityFieldValuePO::getOpportunityId);
        Map<Long, List<OpportunityProductLinePO>> productLineMap = group(
                productLines, OpportunityProductLinePO::getOpportunityId);
        return roots.stream().map(po -> converter.toDomain(po,
                contactMap.getOrDefault(po.getId(), List.of()),
                collaboratorMap.getOrDefault(po.getId(), List.of()),
                valueMap.getOrDefault(po.getId(), List.of()),
                productLineMap.getOrDefault(po.getId(), List.of()), fieldsById)).toList();
    }

    private QueryWrapper<OpportunityPO> visibleRootQuery(
            TenantId tenantId, OpportunityVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException(
                    "opportunity visibility must not be null");
        }
        QueryWrapper<OpportunityPO> query = new QueryWrapper<OpportunityPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", OpportunityStatus.ACTIVE.name());
        applyVisibility(query, visibility);
        return query;
    }

    private void applyVisibility(
            QueryWrapper<OpportunityPO> query, OpportunityVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        query.and(nested -> {
            if (visibility.dataScope() == DataScope.SELF) {
                nested.eq("owner_actor_id", visibility.callerMemberId().getValue());
            } else {
                List<Long> departments = visibility.ownerDepartmentIds().stream()
                        .map(DepartmentId::getValue).sorted().toList();
                nested.apply("""
                        EXISTS (
                            SELECT 1 FROM organization_membership owner_membership
                            WHERE owner_membership.tenant_id = crm_opportunity.tenant_id
                              AND owner_membership.actor_id = crm_opportunity.owner_actor_id
                              AND owner_membership.status = 'ACTIVE'
                              AND owner_membership.department_id IN (%s)
                        )
                        """.formatted(placeholders(departments.size())),
                        departments.toArray());
            }
            if (visibility.accessMode() != OpportunityAccessMode.OWNER_SCOPE) {
                String access = visibility.accessMode() == OpportunityAccessMode.WRITE
                        ? " AND collaborator.access_level = 'WRITE'" : "";
                nested.or().apply("""
                        EXISTS (
                            SELECT 1 FROM crm_opportunity_collaborator collaborator
                            WHERE collaborator.tenant_id = crm_opportunity.tenant_id
                              AND collaborator.opportunity_id = crm_opportunity.id
                              AND collaborator.actor_id = {0}%s
                        )
                        """.formatted(access),
                        visibility.callerMemberId().getValue());
            }
        });
    }

    private String opportunityPeriod(ReportGranularity granularity) {
        if (granularity == ReportGranularity.DAY) {
            return "DATE(outcome_time)";
        }
        if (granularity == ReportGranularity.WEEK) {
            return "DATE_SUB(DATE(outcome_time), INTERVAL WEEKDAY(outcome_time) DAY)";
        }
        if (granularity == ReportGranularity.MONTH) {
            return "DATE_SUB(DATE(outcome_time), "
                    + "INTERVAL (DAYOFMONTH(outcome_time) - 1) DAY)";
        }
        throw new IllegalArgumentException("opportunity report granularity is invalid");
    }

    private void requireReport(ReportDateRange range, ReportGranularity granularity) {
        if (range == null || granularity == null) {
            throw new IllegalArgumentException("opportunity report query is invalid");
        }
    }

    private int requireReportLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("opportunity report limit is invalid");
        }
        return limit;
    }

    private void applyDateTimeUpperBound(
            QueryWrapper<OpportunityPO> query, String column, LocalDate to) {
        if (to.equals(LocalDate.of(9999, 12, 31))) {
            query.le(column, to.atTime(23, 59, 59, 999_999_000));
        } else {
            query.lt(column, to.plusDays(1).atStartOfDay());
        }
    }

    private LocalDate date(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value == null) {
            throw new IllegalStateException("opportunity report period is missing");
        }
        return LocalDate.parse(value.toString());
    }

    private void applyStarred(
            QueryWrapper<OpportunityPO> query,
            OpportunityVisibility visibility, Boolean starred) {
        if (starred == null) {
            return;
        }
        String exists = """
                EXISTS (
                    SELECT 1 FROM crm_opportunity_star star
                    WHERE star.tenant_id = crm_opportunity.tenant_id
                      AND star.opportunity_id = crm_opportunity.id
                      AND star.actor_id = {0}
                )
                """;
        query.apply(Boolean.TRUE.equals(starred) ? exists : "NOT " + exists,
                visibility.callerMemberId().getValue());
    }

    private void applyCondition(
            QueryWrapper<OpportunityPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException(
                    "opportunity filter condition must not be null");
        }
        if (new FieldCode(OpportunityFieldCatalog.PRIMARY_CONTACT)
                .equals(condition.fieldCode())) {
            applyPrimaryContactCondition(query, condition);
            return;
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            applyBuiltinCondition(query, column, condition);
        } else {
            applyCustomCondition(query, condition);
        }
    }

    private void applyPrimaryContactCondition(
            QueryWrapper<OpportunityPO> query, FilterCondition condition) {
        String base = """
                SELECT 1 FROM crm_opportunity_contact opportunity_contact
                WHERE opportunity_contact.tenant_id = crm_opportunity.tenant_id
                  AND opportunity_contact.opportunity_id = crm_opportunity.id
                  AND opportunity_contact.is_primary = 1
                """;
        if (condition.operator() == FilterOperator.EQ) {
            query.apply("EXISTS (" + base + " AND opportunity_contact.contact_id = {0})",
                    condition.values().getFirst());
        } else if (condition.operator() == FilterOperator.IN) {
            String placeholders = placeholders(condition.values().size());
            query.apply("EXISTS (" + base
                            + " AND opportunity_contact.contact_id IN (" + placeholders + "))",
                    condition.values().toArray());
        } else if (condition.operator() == FilterOperator.IS_EMPTY) {
            query.apply("NOT EXISTS (" + base + ")");
        } else if (condition.operator() == FilterOperator.IS_NOT_EMPTY) {
            query.apply("EXISTS (" + base + ")");
        }
    }

    private void applyBuiltinCondition(
            QueryWrapper<OpportunityPO> query, String column,
            FilterCondition condition) {
        List<String> values = condition.values();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.EQ) {
            query.eq(column, values.getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.ne(column, values.getFirst());
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) > 0",
                    values.getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) = 0",
                    values.getFirst());
        } else if (operator == FilterOperator.IS_EMPTY) {
            query.and(value -> value.isNull(column).or().eq(column, ""));
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.isNotNull(column).ne(column, "");
        } else if (operator == FilterOperator.GT) {
            query.gt(column, values.getFirst());
        } else if (operator == FilterOperator.GTE) {
            query.ge(column, values.getFirst());
        } else if (operator == FilterOperator.LT) {
            query.lt(column, values.getFirst());
        } else if (operator == FilterOperator.LTE) {
            query.le(column, values.getFirst());
        } else if (operator == FilterOperator.IN) {
            query.in(column, values);
        } else if (operator == FilterOperator.BETWEEN) {
            query.between(column, values.get(0), values.get(1));
        }
    }

    private void applyCustomCondition(
            QueryWrapper<OpportunityPO> query, FilterCondition condition) {
        String base = """
                SELECT 1
                FROM crm_opportunity_field_value field_value
                JOIN custom_field_definition field_definition
                  ON field_definition.tenant_id = field_value.tenant_id
                 AND field_definition.object_type = field_value.object_type
                 AND field_definition.id = field_value.field_id
                WHERE field_value.tenant_id = crm_opportunity.tenant_id
                  AND field_value.opportunity_id = crm_opportunity.id
                  AND field_definition.field_code = {0}
                """;
        String fieldCode = condition.fieldCode().getValue();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.IS_EMPTY) {
            query.apply("NOT EXISTS (" + base + ")", fieldCode);
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.apply("EXISTS (" + base + ")", fieldCode);
        } else if (operator == FilterOperator.EQ) {
            query.apply("EXISTS (" + base
                            + " AND JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.apply("EXISTS (" + base
                            + " AND NOT JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.IN) {
            query.apply("EXISTS (" + base
                            + " AND JSON_OVERLAPS(field_value.values_json, CAST({1} AS JSON)))",
                    fieldCode, json(condition.values()));
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("EXISTS (" + base
                            + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("NOT EXISTS (" + base
                            + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.BETWEEN) {
            query.apply("EXISTS (" + base + " AND "
                            + orderedExpression("{1}", ">=") + " AND "
                            + orderedExpression("{2}", "<=") + ")",
                    fieldCode, condition.values().get(0), condition.values().get(1));
        } else if (operator == FilterOperator.GT || operator == FilterOperator.GTE
                || operator == FilterOperator.LT || operator == FilterOperator.LTE) {
            String symbol;
            if (operator == FilterOperator.GT) {
                symbol = ">";
            } else if (operator == FilterOperator.GTE) {
                symbol = ">=";
            } else if (operator == FilterOperator.LT) {
                symbol = "<";
            } else {
                symbol = "<=";
            }
            query.apply("EXISTS (" + base + " AND "
                            + orderedExpression("{1}", symbol) + ")",
                    fieldCode, condition.values().getFirst());
        }
    }

    private String orderedExpression(String parameter, String operator) {
        return "((field_definition.field_type IN ('INTEGER','DECIMAL') AND "
                + "CAST(field_value.scalar_value AS DECIMAL(30,10)) " + operator
                + " CAST(" + parameter + " AS DECIMAL(30,10))) OR "
                + "(field_definition.field_type NOT IN ('INTEGER','DECIMAL') AND "
                + "field_value.scalar_value " + operator + " " + parameter + "))";
    }

    private OpportunityUniqueFieldValuePO uniqueValue(
            TenantId tenantId, OpportunityId opportunityId,
            FieldDefinition field, String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        OpportunityUniqueFieldValuePO po = new OpportunityUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setOpportunityId(opportunityId.getValue());
        po.setObjectType(BusinessObjectType.OPPORTUNITY.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedHash(hash(normalized));
        po.setNormalizedValue(normalized);
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> LambdaQueryWrapper<T> childFilter(
            TenantId tenantId, OpportunityId opportunityId,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> rootColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .eq(rootColumn, opportunityId.getValue());
    }

    private <T> LambdaQueryWrapper<T> childListFilter(
            TenantId tenantId, List<Long> opportunityIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> rootColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .in(rootColumn, opportunityIds);
    }

    private <T> Map<Long, List<T>> group(
            List<T> values, Function<T, Long> opportunityId) {
        return values.stream().collect(Collectors.groupingBy(opportunityId,
                LinkedHashMap::new, Collectors.toList()));
    }

    private LambdaQueryWrapper<OpportunityPO> tenantRootFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<OpportunityPO>()
                .eq(OpportunityPO::getTenantId, tenantId.getValue());
    }

    private Map<FieldCode, FieldDefinition> definitionsByCode(
            Collection<FieldDefinition> definitions) {
        if (definitions == null) {
            return Map.of();
        }
        return definitions.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private List<Long> ids(Collection<OpportunityId> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull)
                .map(OpportunityId::getValue).distinct().toList();
    }

    private String placeholders(int count) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add("{" + index + "}");
        }
        return String.join(",", values);
    }

    private int requireLimit(int limit) {
        if (limit < 1 || limit > MAX_INTERNAL_LIMIT) {
            throw new IllegalArgumentException(
                    "opportunity query limit is invalid");
        }
        return limit;
    }

    private void requireTenantMatch(
            TenantId tenantId, Opportunity opportunity) {
        requireTenant(tenantId);
        if (opportunity == null || opportunity.getTenantId() == null
                || !tenantId.sameValueAs(opportunity.getTenantId())) {
            throw new IllegalArgumentException(
                    "opportunity tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Opportunity query value cannot be serialized", exception);
        }
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private String string(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("opportunity dashboard group is missing");
        }
        return text;
    }

    private static Map<FieldCode, String> builtinColumns() {
        Map<FieldCode, String> values = new HashMap<>();
        values.put(new FieldCode(OpportunityFieldCatalog.NAME), "name");
        values.put(new FieldCode(OpportunityFieldCatalog.CUSTOMER), "customer_id");
        values.put(new FieldCode(OpportunityFieldCatalog.PIPELINE_CODE), "pipeline_code");
        values.put(new FieldCode(OpportunityFieldCatalog.STAGE_CODE), "stage_code");
        values.put(new FieldCode(OpportunityFieldCatalog.EXPECTED_CLOSE_DATE),
                "expected_close_date");
        values.put(new FieldCode(OpportunityFieldCatalog.ESTIMATED_AMOUNT),
                "estimated_amount");
        values.put(new FieldCode(OpportunityFieldCatalog.NEXT_CONTACT_TIME),
                "next_contact_time");
        values.put(new FieldCode(OpportunityFieldCatalog.DESCRIPTION), "description");
        values.put(new FieldCode(OpportunityFieldCatalog.OWNER_ACTOR), "owner_actor_id");
        values.put(new FieldCode(OpportunityFieldCatalog.OUTCOME), "outcome");
        values.put(new FieldCode(OpportunityFieldCatalog.OUTCOME_TIME), "outcome_time");
        values.put(new FieldCode(OpportunityFieldCatalog.OUTCOME_REMARK), "outcome_remark");
        values.put(new FieldCode(OpportunityFieldCatalog.STATUS), "status");
        values.put(new FieldCode(OpportunityFieldCatalog.CREATED_TIME), "created_time");
        values.put(new FieldCode(OpportunityFieldCatalog.UPDATED_TIME), "updated_time");
        return Map.copyOf(values);
    }
}
