package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.exception.MarketingFormDomainException;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.MarketingFormSubmission;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.model.MarketingFormSubmissionDateRange;
import io.github.archetom.crm.domain.model.MarketingFormSubmissionTrendPoint;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.repository.MarketingFormSubmissionRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.MarketingFormSubmissionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MarketingFormSubmissionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormSubmissionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarketingFormSubmissionRepositoryImpl
        implements MarketingFormSubmissionRepository {

    private static final LocalDate MAXIMUM_DATE = LocalDate.of(9999, 12, 31);

    private final MarketingFormSubmissionMapper mapper;
    private final MarketingFormSubmissionPOConverter converter;

    @Override
    public MarketingFormSubmission append(
            TenantId tenantId, MarketingFormId formId,
            long formVersion, MarketingFormTargetType targetType, long targetRecordId,
            String idempotencyKey, String submissionDigest) {
        requireAppend(tenantId, formId, formVersion, targetType, targetRecordId,
                idempotencyKey, submissionDigest);
        MarketingFormSubmissionPO po = new MarketingFormSubmissionPO();
        po.setTenantId(tenantId.getValue());
        po.setMarketingFormId(formId.getValue());
        po.setFormVersion(formVersion);
        po.setTargetType(targetType.name());
        if (targetType == MarketingFormTargetType.LEAD) {
            po.setLeadId(targetRecordId);
        } else {
            po.setCustomerId(targetRecordId);
        }
        po.setIdempotencyKey(idempotencyKey);
        po.setSubmissionDigest(submissionDigest);
        try {
            if (mapper.insertWithDatabaseUtcTime(po) != 1 || po.getId() == null) {
                throw new IllegalStateException(
                        "Marketing form submission insert did not affect exactly one row");
            }
            MarketingFormSubmissionPO persisted = mapper.selectOne(
                    baseQuery(tenantId, formId)
                            .eq("id", po.getId()));
            if (persisted == null || persisted.getSubmittedTime() == null) {
                throw new IllegalStateException(
                        "Persisted marketing form submission could not be reloaded");
            }
            return converter.toDomain(persisted);
        } catch (DuplicateKeyException exception) {
            throw new MarketingFormDomainException(
                    "marketing form submission conflicts with existing data");
        }
    }

    @Override
    public Optional<MarketingFormSubmission> findByIdempotencyKey(
            TenantId tenantId, MarketingFormId formId, String idempotencyKey) {
        if (tenantId == null || formId == null || idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("marketing form submission idempotency key is invalid");
        }
        return Optional.ofNullable(mapper.selectOne(baseQuery(tenantId, formId)
                .eq("idempotency_key", idempotencyKey))).map(converter::toDomain);
    }

    @Override
    public List<MarketingFormSubmissionTrendPoint> findTrend(
            TenantId tenantId, MarketingFormId formId,
            MarketingFormSubmissionDateRange range, ReportGranularity granularity) {
        requireQuery(tenantId, formId);
        if (range == null || granularity == null) {
            throw new IllegalArgumentException(
                    "marketing form submission trend query is invalid");
        }
        String period = period(granularity);
        QueryWrapper<MarketingFormSubmissionPO> query = baseQuery(tenantId, formId)
                .ge("submitted_time", range.from().atStartOfDay());
        applyUpperBound(query, range.to());
        return mapper.selectMaps(query
                        .select(period + " AS period_start",
                                "COUNT(*) AS accepted_count")
                        .groupBy(period).orderByAsc(period))
                .stream().map(row -> new MarketingFormSubmissionTrendPoint(
                        date(row.get("period_start")),
                        number(row.get("accepted_count")).longValue()))
                .toList();
    }

    @Override
    public PageResult<MarketingFormSubmission> findVisibleLeadLinks(
            TenantId tenantId, MarketingFormId formId,
            MarketingFormSubmissionDateRange range, LeadVisibility visibility,
            int page, int size) {
        requireQuery(tenantId, formId);
        if (visibility == null || page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "marketing form submission link query is invalid");
        }
        QueryWrapper<MarketingFormSubmissionPO> query = baseQuery(tenantId, formId);
        if (range != null) {
            query.ge("submitted_time", range.from().atStartOfDay());
            applyUpperBound(query, range.to());
        }
        applyVisibleLead(query, visibility);
        IPage<MarketingFormSubmissionPO> result = mapper.selectPage(
                new Page<>(page, size),
                query.orderByDesc("submitted_time").orderByDesc("id"));
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0, result.getTotal()),
                result.getRecords().stream().map(converter::toDomain).toList());
    }

    @Override
    public PageResult<MarketingFormSubmission> findVisibleCustomerLinks(
            TenantId tenantId, MarketingFormId formId,
            MarketingFormSubmissionDateRange range, CustomerVisibility visibility,
            int page, int size) {
        requireQuery(tenantId, formId);
        if (visibility == null || page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "marketing form submission link query is invalid");
        }
        QueryWrapper<MarketingFormSubmissionPO> query = baseQuery(tenantId, formId);
        if (range != null) {
            query.ge("submitted_time", range.from().atStartOfDay());
            applyUpperBound(query, range.to());
        }
        applyVisibleCustomer(query, visibility);
        IPage<MarketingFormSubmissionPO> result = mapper.selectPage(
                new Page<>(page, size),
                query.orderByDesc("submitted_time").orderByDesc("id"));
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0, result.getTotal()),
                result.getRecords().stream().map(converter::toDomain).toList());
    }

    private QueryWrapper<MarketingFormSubmissionPO> baseQuery(
            TenantId tenantId, MarketingFormId formId) {
        return new QueryWrapper<MarketingFormSubmissionPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("marketing_form_id", formId.getValue());
    }

    private void applyVisibleLead(
            QueryWrapper<MarketingFormSubmissionPO> query,
            LeadVisibility visibility) {
        List<Object> arguments = new ArrayList<>();
        String restriction = visibilityRestriction(visibility, arguments);
        query.apply("""
                EXISTS (
                    SELECT 1
                    FROM crm_lead visible_lead
                    WHERE visible_lead.tenant_id = crm_marketing_form_submission.tenant_id
                      AND visible_lead.id = crm_marketing_form_submission.lead_id
                      AND visible_lead.status IN ('ACTIVE', 'CONVERTED')
                      %s
                )
                """.formatted(restriction), arguments.toArray());
    }

    private void applyVisibleCustomer(
            QueryWrapper<MarketingFormSubmissionPO> query, CustomerVisibility visibility) {
        List<Object> arguments = new ArrayList<>();
        String restriction = customerVisibilityRestriction(visibility, arguments);
        query.apply("""
                EXISTS (
                    SELECT 1
                    FROM crm_customer visible_customer
                    WHERE visible_customer.tenant_id = crm_marketing_form_submission.tenant_id
                      AND visible_customer.id = crm_marketing_form_submission.customer_id
                      AND visible_customer.status = 'ACTIVE'
                      %s
                )
                """.formatted(restriction), arguments.toArray());
    }

    private String visibilityRestriction(
            LeadVisibility visibility, List<Object> arguments) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return "";
        }
        String ownerPredicate;
        if (visibility.dataScope() == DataScope.SELF) {
            ownerPredicate = "visible_lead.owner_actor_id = {0}";
            arguments.add(visibility.callerMemberId().getValue());
        } else {
            List<Long> departments = visibility.ownerDepartmentIds().stream()
                    .map(DepartmentId::getValue).sorted().toList();
            String placeholders = placeholders(departments.size(), 0);
            arguments.addAll(departments);
            ownerPredicate = """
                    EXISTS (
                        SELECT 1 FROM organization_membership owner_membership
                        WHERE owner_membership.tenant_id = visible_lead.tenant_id
                          AND owner_membership.actor_id = visible_lead.owner_actor_id
                          AND owner_membership.status = 'ACTIVE'
                          AND owner_membership.department_id IN (%s)
                    )
                    """.formatted(placeholders);
        }
        if (visibility.accessMode() == LeadAccessMode.OWNER_SCOPE) {
            return "AND (" + ownerPredicate + ")";
        }
        int actorIndex = arguments.size();
        arguments.add(visibility.callerMemberId().getValue());
        String access = visibility.accessMode() == LeadAccessMode.WRITE
                ? " AND collaborator.access_level = 'WRITE'" : "";
        String collaboratorPredicate = """
                EXISTS (
                    SELECT 1 FROM crm_lead_collaborator collaborator
                    WHERE collaborator.tenant_id = visible_lead.tenant_id
                      AND collaborator.lead_id = visible_lead.id
                      AND collaborator.actor_id = {%d}%s
                )
                """.formatted(actorIndex, access);
        return "AND ((" + ownerPredicate + ") OR (" + collaboratorPredicate + "))";
    }

    private String customerVisibilityRestriction(
            CustomerVisibility visibility, List<Object> arguments) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return "";
        }
        String ownerPredicate;
        if (visibility.dataScope() == DataScope.SELF) {
            ownerPredicate = "visible_customer.owner_actor_id = {0}";
            arguments.add(visibility.callerMemberId().getValue());
        } else {
            List<Long> departments = visibility.ownerDepartmentIds().stream()
                    .map(DepartmentId::getValue).sorted().toList();
            String placeholders = placeholders(departments.size(), 0);
            arguments.addAll(departments);
            ownerPredicate = """
                    EXISTS (
                        SELECT 1 FROM organization_membership owner_membership
                        WHERE owner_membership.tenant_id = visible_customer.tenant_id
                          AND owner_membership.actor_id = visible_customer.owner_actor_id
                          AND owner_membership.status = 'ACTIVE'
                          AND owner_membership.department_id IN (%s)
                    )
                    """.formatted(placeholders);
        }
        if (visibility.accessMode() == CustomerAccessMode.OWNER_SCOPE) {
            return "AND (" + ownerPredicate + ")";
        }
        int actorIndex = arguments.size();
        arguments.add(visibility.callerMemberId().getValue());
        String access = visibility.accessMode() == CustomerAccessMode.WRITE
                ? " AND collaborator.access_level = 'WRITE'" : "";
        String collaboratorPredicate = """
                EXISTS (
                    SELECT 1 FROM crm_customer_collaborator collaborator
                    WHERE collaborator.tenant_id = visible_customer.tenant_id
                      AND collaborator.customer_id = visible_customer.id
                      AND collaborator.actor_id = {%d}%s
                )
                """.formatted(actorIndex, access);
        return "AND ((" + ownerPredicate + ") OR (" + collaboratorPredicate + "))";
    }

    private String period(ReportGranularity granularity) {
        if (granularity == ReportGranularity.DAY) {
            return "DATE(submitted_time)";
        }
        if (granularity == ReportGranularity.WEEK) {
            return "DATE_SUB(DATE(submitted_time), INTERVAL WEEKDAY(submitted_time) DAY)";
        }
        if (granularity == ReportGranularity.MONTH) {
            return "DATE_SUB(DATE(submitted_time), "
                    + "INTERVAL (DAYOFMONTH(submitted_time) - 1) DAY)";
        }
        throw new IllegalArgumentException(
                "marketing form submission granularity is invalid");
    }

    private void applyUpperBound(
            QueryWrapper<MarketingFormSubmissionPO> query, LocalDate to) {
        if (MAXIMUM_DATE.equals(to)) {
            query.le("submitted_time", to.atTime(23, 59, 59, 999_999_000));
        } else {
            query.lt("submitted_time", to.plusDays(1).atStartOfDay());
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
            throw new IllegalStateException(
                    "marketing form submission trend period is missing");
        }
        return LocalDate.parse(value.toString());
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private String placeholders(int count, int start) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add("{" + (start + index) + "}");
        }
        return String.join(",", values);
    }

    private void requireQuery(TenantId tenantId, MarketingFormId formId) {
        requireTenant(tenantId);
        if (formId == null) {
            throw new IllegalArgumentException("marketing form ID is required");
        }
    }

    private void requireAppend(
            TenantId tenantId, MarketingFormId formId,
            long formVersion, MarketingFormTargetType targetType, long targetRecordId,
            String idempotencyKey, String submissionDigest) {
        requireTenant(tenantId);
        if (formId == null || formVersion < 0 || targetType == null || targetRecordId < 1) {
            throw new IllegalArgumentException(
                    "marketing form submission append is invalid");
        }
        if (idempotencyKey == null
                || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || submissionDigest == null || !submissionDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("marketing form submission idempotency is invalid");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
