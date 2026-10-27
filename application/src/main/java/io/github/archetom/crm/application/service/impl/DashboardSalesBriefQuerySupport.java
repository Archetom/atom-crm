package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContractAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DashboardBriefMetric;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.ReceivableAccessMode;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Permission-filtered, server-owned inputs for the dashboard sales brief. */
@Component
@RequiredArgsConstructor
class DashboardSalesBriefQuerySupport {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final LeadAccessResolver leadAccessResolver;
    private final OpportunityAccessResolver opportunityAccessResolver;
    private final ContractAccessResolver contractAccessResolver;
    private final ReceivableAccessResolver receivableAccessResolver;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final DashboardVisibilityNarrower visibilityNarrower;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final ContractRepository contractRepository;
    private final ReceivableRepository receivableRepository;
    private final ActivityRepository activityRepository;

    List<BriefMetricVO> summarize(
            AuthenticatedCaller caller,
            TenantId tenantId,
            DashboardDataScope preferredScope,
            ReportDateRange current,
            ReportDateRange previous) {
        return List.of(DashboardBriefMetric.values()).stream()
                .map(metric -> summarizeMetric(caller, tenantId, preferredScope,
                        current, previous, metric))
                .toList();
    }

    DashboardMetricSummary summarizeConvertedCustomer(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("dashboard customer conversion range is required");
        }
        CustomerAccessResolver.CustomerAccess source = customerAccessResolver.access(
                caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        CustomerAccessResolver.CustomerAccess scoped = visibilityNarrower.narrow(source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.CUSTOMER,
                CustomerFieldCatalog.DEAL_STATUS, CustomerFieldCatalog.STATUS);
        return customerRepository.summarizeConverted(tenantId, scoped.visibility(), range);
    }

    private BriefMetricVO summarizeMetric(
            AuthenticatedCaller caller,
            TenantId tenantId,
            DashboardDataScope preferredScope,
            ReportDateRange current,
            ReportDateRange previous,
            DashboardBriefMetric metric) {
        try {
            MetricPair pair = switch (metric) {
                case NEW_CUSTOMER -> customers(caller, tenantId, preferredScope, current, previous);
                case NEW_CONTACT -> contacts(caller, tenantId, preferredScope, current, previous);
                case NEW_LEAD -> leads(caller, tenantId, preferredScope, current, previous);
                case NEW_OPPORTUNITY, OPPORTUNITY_AMOUNT -> opportunities(
                        caller, tenantId, preferredScope, current, previous, metric.isAmountMetric());
                case NEW_CONTRACT, CONTRACT_AMOUNT -> contracts(
                        caller, tenantId, preferredScope, current, previous, metric.isAmountMetric());
                case RECEIVABLE_AMOUNT -> receivables(caller, tenantId, preferredScope, current, previous);
                case NEW_FOLLOW_UP -> followUps(caller, tenantId, preferredScope, current, previous);
            };
            BigDecimal currentValue = value(pair.current(), metric);
            BigDecimal previousValue = pair.previous() == null ? null : value(pair.previous(), metric);
            return new BriefMetricVO(metric.name(), AVAILABLE,
                    metric.isAmountMetric() ? "AMOUNT" : "COUNT",
                    currentValue, previousValue,
                    previousValue == null ? null : comparisonRate(currentValue, previousValue));
        } catch (NonRetryableApplicationException exception) {
            if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                throw exception;
            }
            return new BriefMetricVO(metric.name(), UNAVAILABLE,
                    metric.isAmountMetric() ? "AMOUNT" : "COUNT", null, null, null);
        }
    }

    private MetricPair customers(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous) {
        CustomerAccessResolver.CustomerAccess source = customerAccessResolver.access(
                caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        CustomerAccessResolver.CustomerAccess scoped = visibilityNarrower.narrow(source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.CUSTOMER,
                CustomerFieldCatalog.CREATED_TIME, CustomerFieldCatalog.STATUS);
        return new MetricPair(
                customerRepository.summarizeCreated(tenantId, scoped.visibility(), current),
                previous == null ? null
                        : customerRepository.summarizeCreated(tenantId, scoped.visibility(), previous));
    }

    private MetricPair contacts(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous) {
        ContactAccessResolver.ContactAccess source = contactAccessResolver.access(
                caller, AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        ContactAccessResolver.ContactAccess scoped = visibilityNarrower.narrow(source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.CONTACT,
                ContactFieldCatalog.CREATED_TIME, ContactFieldCatalog.STATUS);
        return new MetricPair(
                contactRepository.summarizeCreated(tenantId, scoped.visibility(), current),
                previous == null ? null
                        : contactRepository.summarizeCreated(tenantId, scoped.visibility(), previous));
    }

    private MetricPair leads(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous) {
        LeadAccessResolver.LeadAccess source = leadAccessResolver.access(
                caller, AuthorityCatalog.LEADS_READ, LeadAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        LeadAccessResolver.LeadAccess scoped = visibilityNarrower.narrow(source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.LEAD,
                LeadFieldCatalog.CREATED_TIME, LeadFieldCatalog.STATUS);
        return new MetricPair(
                leadRepository.summarizeCreated(tenantId, scoped.visibility(), current),
                previous == null ? null
                        : leadRepository.summarizeCreated(tenantId, scoped.visibility(), previous));
    }

    private MetricPair opportunities(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous, boolean amount) {
        OpportunityAccessResolver.OpportunityAccess source = opportunityAccessResolver.access(
                caller, AuthorityCatalog.OPPORTUNITIES_READ, OpportunityAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        OpportunityAccessResolver.OpportunityAccess scoped = visibilityNarrower.narrow(
                source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.OPPORTUNITY,
                amount ? new String[]{OpportunityFieldCatalog.CREATED_TIME,
                        OpportunityFieldCatalog.STATUS, OpportunityFieldCatalog.ESTIMATED_AMOUNT}
                        : new String[]{OpportunityFieldCatalog.CREATED_TIME,
                        OpportunityFieldCatalog.STATUS});
        return new MetricPair(
                opportunityRepository.summarizeCreated(tenantId, scoped.visibility(), current),
                previous == null ? null
                        : opportunityRepository.summarizeCreated(tenantId, scoped.visibility(), previous));
    }

    private MetricPair contracts(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous, boolean amount) {
        ContractAccessResolver.ContractAccess source = contractAccessResolver.access(
                caller, AuthorityCatalog.CONTRACTS_READ, ContractAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        ContractAccessResolver.ContractAccess scoped = visibilityNarrower.narrow(source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.CONTRACT,
                amount ? new String[]{ContractFieldCatalog.CREATED_TIME, ContractFieldCatalog.STATUS,
                        ContractFieldCatalog.TOTAL_AMOUNT}
                        : new String[]{ContractFieldCatalog.CREATED_TIME, ContractFieldCatalog.STATUS});
        return new MetricPair(
                contractRepository.summarizeCreated(tenantId, scoped.visibility(), current),
                previous == null ? null
                        : contractRepository.summarizeCreated(tenantId, scoped.visibility(), previous));
    }

    private MetricPair receivables(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous) {
        ReceivableAccessResolver.ReceivableAccess source = receivableAccessResolver.access(
                caller, AuthorityCatalog.RECEIVABLES_READ, ReceivableAccessMode.READ);
        requireSameTenant(tenantId, source.tenantId());
        ReceivableAccessResolver.ReceivableAccess scoped = visibilityNarrower.narrow(
                source, preferredScope);
        requireFields(tenantId, scoped.memberId(), BusinessObjectType.RECEIVABLE,
                ReceivableFieldCatalog.RECEIVED_DATE, ReceivableFieldCatalog.STATUS,
                ReceivableFieldCatalog.AMOUNT);
        return new MetricPair(
                receivableRepository.summarizeReceived(tenantId, scoped.visibility(), current),
                previous == null ? null
                        : receivableRepository.summarizeReceived(tenantId, scoped.visibility(), previous));
    }

    private MetricPair followUps(
            AuthenticatedCaller caller, TenantId tenantId, DashboardDataScope preferredScope,
            ReportDateRange current, ReportDateRange previous) {
        if (preferredScope != DashboardDataScope.SELF) {
            throw denied("follow-up dashboard summary only supports self scope");
        }
        TenantId activityTenant = organizationAccessGuard.requireActiveMembership(
                caller, AuthorityCatalog.ACTIVITIES_READ);
        requireSameTenant(tenantId, activityTenant);
        MemberId memberId = new MemberId(caller.actorId());
        return new MetricPair(
                activityRepository.summarizeCreatedFollowUps(tenantId, memberId, current),
                previous == null ? null : activityRepository.summarizeCreatedFollowUps(
                        tenantId, memberId, previous));
    }

    private void requireFields(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType, String... fieldCodes) {
        Map<FieldCode, EffectiveFieldAccess> fields = fieldAccessResolver.resolve(
                tenantId, memberId, objectType);
        for (String fieldCode : fieldCodes) {
            EffectiveFieldAccess access = fields.get(new FieldCode(fieldCode));
            if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                    || access.maskType() != FieldMaskType.NONE) {
                throw denied("dashboard source field is unavailable");
            }
        }
    }

    private BigDecimal value(DashboardMetricSummary summary, DashboardBriefMetric metric) {
        return metric.isAmountMetric()
                ? summary.amount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(summary.count());
    }

    private BigDecimal comparisonRate(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() == 0 ? BigDecimal.ZERO.setScale(2) : null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private void requireSameTenant(TenantId expected, TenantId actual) {
        if (expected == null || actual == null || !expected.sameValueAs(actual)) {
            throw new IllegalStateException("dashboard sales brief source tenant is inconsistent");
        }
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }

    record BriefMetricVO(
            String metric, String status, String unit, BigDecimal value,
            BigDecimal previousValue, BigDecimal comparisonRate) {
    }

    private record MetricPair(DashboardMetricSummary current, DashboardMetricSummary previous) {
    }
}
