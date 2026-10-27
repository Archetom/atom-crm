package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.model.DataScope;
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
import java.util.Locale;
import java.util.Set;

/**
 * Resolves one authority's data scope into the values every record-scoped aggregate needs.
 *
 * <p>Each aggregate keeps its own typed visibility value, but the way that value is derived —
 * authority plus active membership, the caller's granted scope, then the department set that scope
 * implies — is one rule. Holding it here means a change to it applies everywhere at once, instead
 * of relying on eight copies being edited together; a missed copy would silently widen or narrow
 * one aggregate's visibility without failing to compile and without failing a test.</p>
 */
@Component
@RequiredArgsConstructor
public class DataScopeVisibilityResolver {

    private final OrganizationAccessGuard organizationAccessGuard;
    private final TenantDataScopeResolver dataScopeResolver;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * @param aggregateLabel the aggregate name as it appears in the caller-facing rejection, such
     *                       as {@code "Customer"} or {@code "Return visit"}
     */
    public ResolvedScope resolve(AuthenticatedCaller caller, String authority, String aggregateLabel) {
        TenantId tenantId = organizationAccessGuard.requireActiveMembership(caller, authority);
        MemberId memberId = new MemberId(caller.actorId());
        DataScope dataScope = dataScopeResolver.resolve(caller, authority)
                .orElseThrow(() -> denied(aggregateLabel + " data scope is unavailable"));
        OrganizationMembership membership = membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> denied("Caller is not an active member of this tenant"));
        return new ResolvedScope(tenantId, memberId, dataScope,
                departments(tenantId, membership, dataScope, aggregateLabel));
    }

    private Set<DepartmentId> departments(TenantId tenantId, OrganizationMembership membership,
                                          DataScope dataScope, String aggregateLabel) {
        if (dataScope == DataScope.SELF || dataScope == DataScope.TENANT) {
            return Set.of();
        }
        DepartmentId departmentId = membership.getDepartmentId();
        if (departmentId == null) {
            throw denied("Department " + aggregateLabel.toLowerCase(Locale.ROOT)
                    + " data scope is unavailable");
        }
        LinkedHashSet<DepartmentId> values = new LinkedHashSet<>();
        values.add(departmentId);
        if (dataScope == DataScope.DEPARTMENT_SUBTREE) {
            departmentRepository.findDescendants(tenantId, departmentId).stream()
                    .filter(Department::isActive)
                    .map(Department::getId)
                    .forEach(values::add);
        }
        return Set.copyOf(values);
    }

    private static NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED, message);
    }

    /** The verified caller's tenant, member identity and the department set its scope covers. */
    public record ResolvedScope(
            TenantId tenantId, MemberId memberId, DataScope dataScope, Set<DepartmentId> departmentIds) {
    }
}
