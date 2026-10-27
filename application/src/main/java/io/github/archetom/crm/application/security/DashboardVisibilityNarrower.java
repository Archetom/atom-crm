package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.ContractVisibility;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ReceivableVisibility;
import io.github.archetom.crm.domain.policy.DashboardDataScopePolicy;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Rebuilds a source visibility after applying a presentation-only dashboard maximum. */
@Component
@RequiredArgsConstructor
public class DashboardVisibilityNarrower {

    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    public CustomerAccessResolver.CustomerAccess narrow(
            CustomerAccessResolver.CustomerAccess source,
            DashboardDataScope preferredScope) {
        requireSource(source == null ? null : source.tenantId(),
                source == null ? null : source.memberId(),
                source == null ? null : source.visibility().callerMemberId());
        ResolvedScope resolved = resolve(source.tenantId(), source.memberId(),
                source.visibility().dataScope(), preferredScope);
        return new CustomerAccessResolver.CustomerAccess(
                source.tenantId(), source.memberId(), new CustomerVisibility(
                resolved.dataScope(), source.memberId(), resolved.departmentIds(),
                source.visibility().accessMode()));
    }

    public ContactAccessResolver.ContactAccess narrow(
            ContactAccessResolver.ContactAccess source,
            DashboardDataScope preferredScope) {
        requireSource(source == null ? null : source.tenantId(),
                source == null ? null : source.memberId(),
                source == null ? null : source.visibility().callerMemberId());
        ResolvedScope resolved = resolve(source.tenantId(), source.memberId(),
                source.visibility().dataScope(), preferredScope);
        return new ContactAccessResolver.ContactAccess(
                source.tenantId(), source.memberId(), new ContactVisibility(
                resolved.dataScope(), source.memberId(), resolved.departmentIds(),
                source.visibility().accessMode()));
    }

    public LeadAccessResolver.LeadAccess narrow(
            LeadAccessResolver.LeadAccess source,
            DashboardDataScope preferredScope) {
        requireSource(source == null ? null : source.tenantId(),
                source == null ? null : source.memberId(),
                source == null ? null : source.visibility().callerMemberId());
        ResolvedScope resolved = resolve(source.tenantId(), source.memberId(),
                source.visibility().dataScope(), preferredScope);
        return new LeadAccessResolver.LeadAccess(
                source.tenantId(), source.memberId(), new LeadVisibility(
                resolved.dataScope(), source.memberId(), resolved.departmentIds(),
                source.visibility().accessMode()));
    }

    public OpportunityAccessResolver.OpportunityAccess narrow(
            OpportunityAccessResolver.OpportunityAccess source,
            DashboardDataScope preferredScope) {
        requireSource(source == null ? null : source.tenantId(),
                source == null ? null : source.memberId(),
                source == null ? null : source.visibility().callerMemberId());
        ResolvedScope resolved = resolve(source.tenantId(), source.memberId(),
                source.visibility().dataScope(), preferredScope);
        return new OpportunityAccessResolver.OpportunityAccess(
                source.tenantId(), source.memberId(), new OpportunityVisibility(
                resolved.dataScope(), source.memberId(), resolved.departmentIds(),
                source.visibility().accessMode()));
    }

    public ContractAccessResolver.ContractAccess narrow(
            ContractAccessResolver.ContractAccess source,
            DashboardDataScope preferredScope) {
        requireSource(source == null ? null : source.tenantId(),
                source == null ? null : source.memberId(),
                source == null ? null : source.visibility().callerMemberId());
        ResolvedScope resolved = resolve(source.tenantId(), source.memberId(),
                source.visibility().dataScope(), preferredScope);
        return new ContractAccessResolver.ContractAccess(
                source.tenantId(), source.memberId(), new ContractVisibility(
                resolved.dataScope(), source.memberId(), resolved.departmentIds(),
                source.visibility().accessMode()));
    }

    public ReceivableAccessResolver.ReceivableAccess narrow(
            ReceivableAccessResolver.ReceivableAccess source,
            DashboardDataScope preferredScope) {
        requireSource(source == null ? null : source.tenantId(),
                source == null ? null : source.memberId(),
                source == null ? null : source.visibility().callerMemberId());
        ResolvedScope resolved = resolve(source.tenantId(), source.memberId(),
                source.visibility().dataScope(), preferredScope);
        return new ReceivableAccessResolver.ReceivableAccess(
                source.tenantId(), source.memberId(), new ReceivableVisibility(
                resolved.dataScope(), source.memberId(), resolved.departmentIds(),
                source.visibility().accessMode()));
    }

    private ResolvedScope resolve(
            TenantId tenantId, MemberId memberId, DataScope sourceScope,
            DashboardDataScope preferredScope) {
        OrganizationMembership membership = membershipRepository
                .findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> denied("Dashboard member visibility is unavailable"));
        DataScope narrowed = DashboardDataScopePolicy.narrow(
                sourceScope, preferredScope, membership.getDepartmentId());
        return new ResolvedScope(narrowed,
                departments(tenantId, membership.getDepartmentId(), narrowed));
    }

    private Set<DepartmentId> departments(
            TenantId tenantId, DepartmentId currentDepartmentId, DataScope scope) {
        if (scope == DataScope.SELF || scope == DataScope.TENANT) {
            return Set.of();
        }
        if (currentDepartmentId == null) {
            throw new IllegalStateException("department dashboard scope was not downgraded");
        }
        LinkedHashSet<DepartmentId> values = new LinkedHashSet<>();
        values.add(currentDepartmentId);
        if (scope == DataScope.DEPARTMENT_SUBTREE) {
            departmentRepository.findDescendants(tenantId, currentDepartmentId).stream()
                    .filter(Department::isActive)
                    .map(Department::getId)
                    .forEach(values::add);
        }
        return Set.copyOf(values);
    }

    private void requireSource(
            TenantId tenantId, MemberId memberId, MemberId visibilityMemberId) {
        if (tenantId == null || memberId == null || visibilityMemberId == null
                || !memberId.sameValueAs(visibilityMemberId)) {
            throw new IllegalStateException("dashboard source visibility is inconsistent");
        }
    }

    private NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }

    private record ResolvedScope(DataScope dataScope, Set<DepartmentId> departmentIds) {
    }
}
