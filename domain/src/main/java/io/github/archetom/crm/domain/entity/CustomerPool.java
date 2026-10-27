package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.CustomerPoolDomainException;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Versioned tenant-owned customer pool and its eligibility policy. */
@Getter
public class CustomerPool extends AggregateRoot<CustomerPoolId> {

    public static final int MAX_POOLS_PER_TENANT = 500;
    public static final int MAX_ADMINS = 50;
    public static final int MAX_ELIGIBLE_MEMBERS = 500;
    public static final int MAX_ELIGIBLE_DEPARTMENTS = 200;
    public static final int MAX_RULES = 10;

    private CustomerPoolId id;
    private TenantId tenantId;
    private String name;
    private CustomerPoolStatus status;
    private Set<MemberId> administratorMemberIds = Set.of();
    private Set<MemberId> eligibleMemberIds = Set.of();
    private Set<DepartmentId> eligibleDepartmentIds = Set.of();
    private int dailyClaimLimit;
    private int previousOwnerCooldownDays;
    private boolean automaticReleaseEnabled;
    private List<CustomerPoolRule> rules = List.of();
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private CustomerPool() {
    }

    public record Snapshot(
            CustomerPoolId id,
            TenantId tenantId,
            String name,
            CustomerPoolStatus status,
            Set<MemberId> administratorMemberIds,
            Set<MemberId> eligibleMemberIds,
            Set<DepartmentId> eligibleDepartmentIds,
            int dailyClaimLimit,
            int previousOwnerCooldownDays,
            boolean automaticReleaseEnabled,
            List<CustomerPoolRule> rules,
            Long version,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static CustomerPool create(
            TenantId tenantId,
            String name,
            Collection<MemberId> administratorMemberIds,
            Collection<MemberId> eligibleMemberIds,
            Collection<DepartmentId> eligibleDepartmentIds,
            int dailyClaimLimit,
            int previousOwnerCooldownDays,
            boolean automaticReleaseEnabled,
            Collection<CustomerPoolRule> rules) {
        CustomerPool pool = new CustomerPool();
        pool.tenantId = requireTenant(tenantId);
        pool.status = CustomerPoolStatus.ACTIVE;
        pool.changeConfiguration(name, administratorMemberIds, eligibleMemberIds,
                eligibleDepartmentIds, dailyClaimLimit, previousOwnerCooldownDays,
                automaticReleaseEnabled, rules);
        pool.createdTime = pool.updatedTime;
        return pool;
    }

    public static CustomerPool reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new CustomerPoolDomainException("customer pool snapshot must not be empty");
        }
        CustomerPool pool = new CustomerPool();
        pool.id = snapshot.id();
        pool.tenantId = requireTenant(snapshot.tenantId());
        pool.status = snapshot.status() == null ? CustomerPoolStatus.DELETED : snapshot.status();
        pool.name = requireName(snapshot.name());
        pool.administratorMemberIds = members(snapshot.administratorMemberIds(), MAX_ADMINS,
                "customer pool administrators");
        pool.eligibleMemberIds = members(snapshot.eligibleMemberIds(), MAX_ELIGIBLE_MEMBERS,
                "customer pool eligible members");
        pool.eligibleDepartmentIds = departments(snapshot.eligibleDepartmentIds());
        pool.dailyClaimLimit = requireRange(snapshot.dailyClaimLimit(), 0, 10_000,
                "customer pool daily claim limit");
        pool.previousOwnerCooldownDays = requireRange(snapshot.previousOwnerCooldownDays(), 0, 3_650,
                "customer pool previous-owner cooldown");
        pool.automaticReleaseEnabled = snapshot.automaticReleaseEnabled();
        pool.rules = rules(snapshot.rules(), pool.automaticReleaseEnabled);
        pool.restoreVersion(snapshot.version());
        pool.createdTime = snapshot.createdTime();
        pool.updatedTime = snapshot.updatedTime();
        return pool;
    }

    public void changeConfiguration(
            String name,
            Collection<MemberId> administratorMemberIds,
            Collection<MemberId> eligibleMemberIds,
            Collection<DepartmentId> eligibleDepartmentIds,
            int dailyClaimLimit,
            int previousOwnerCooldownDays,
            boolean automaticReleaseEnabled,
            Collection<CustomerPoolRule> rules) {
        ensurePresent();
        String nextName = requireName(name);
        Set<MemberId> nextAdministrators = members(administratorMemberIds, MAX_ADMINS,
                "customer pool administrators");
        if (nextAdministrators.isEmpty()) {
            throw new CustomerPoolDomainException("customer pool requires an administrator");
        }
        Set<MemberId> nextEligibleMembers = members(eligibleMemberIds, MAX_ELIGIBLE_MEMBERS,
                "customer pool eligible members");
        Set<DepartmentId> nextEligibleDepartments = departments(eligibleDepartmentIds);
        if (nextEligibleMembers.isEmpty() && nextEligibleDepartments.isEmpty()) {
            throw new CustomerPoolDomainException("customer pool requires an eligible member or department");
        }
        int nextClaimLimit = requireRange(dailyClaimLimit, 0, 10_000,
                "customer pool daily claim limit");
        int nextCooldown = requireRange(previousOwnerCooldownDays, 0, 3_650,
                "customer pool previous-owner cooldown");
        List<CustomerPoolRule> nextRules = rules(rules, automaticReleaseEnabled);

        this.name = nextName;
        this.administratorMemberIds = nextAdministrators;
        this.eligibleMemberIds = nextEligibleMembers;
        this.eligibleDepartmentIds = nextEligibleDepartments;
        this.dailyClaimLimit = nextClaimLimit;
        this.previousOwnerCooldownDays = nextCooldown;
        this.automaticReleaseEnabled = automaticReleaseEnabled;
        this.rules = nextRules;
        touch();
    }

    public void changeStatus(CustomerPoolStatus status) {
        ensurePresent();
        if (status == null || status == CustomerPoolStatus.DELETED) {
            throw new CustomerPoolDomainException("customer pool status is invalid");
        }
        this.status = status;
        touch();
    }

    public void delete() {
        ensurePresent();
        status = CustomerPoolStatus.DELETED;
        touch();
    }

    public boolean isActive() {
        return status == CustomerPoolStatus.ACTIVE;
    }

    public boolean isAdministrator(MemberId memberId) {
        return memberId != null && administratorMemberIds.contains(memberId);
    }

    public boolean isEligible(MemberId memberId, DepartmentId departmentId) {
        return isAdministrator(memberId)
                || memberId != null && eligibleMemberIds.contains(memberId)
                || departmentId != null && eligibleDepartmentIds.contains(departmentId);
    }

    public void onPersisted(CustomerPoolId id, Long version,
                            LocalDateTime createdTime, LocalDateTime updatedTime) {
        if (id == null || this.id != null && !this.id.sameValueAs(id)) {
            throw new CustomerPoolDomainException("persisted customer pool ID is invalid");
        }
        this.id = id;
        restoreVersion(version);
        if (createdTime != null) this.createdTime = createdTime;
        if (updatedTime != null) this.updatedTime = updatedTime;
    }

    private void ensurePresent() {
        if (status == CustomerPoolStatus.DELETED) {
            throw new CustomerPoolDomainException("deleted customer pool cannot change");
        }
    }

    private void touch() {
        updatedTime = LocalDateTime.now();
    }

    private static TenantId requireTenant(TenantId value) {
        if (value == null) throw new CustomerPoolDomainException("customer pool tenant must not be empty");
        return value;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomerPoolDomainException("customer pool name must not be empty");
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new CustomerPoolDomainException("customer pool name is too long");
        }
        return normalized;
    }

    private static Set<MemberId> members(Collection<MemberId> values, int max, String description) {
        LinkedHashSet<MemberId> unique = new LinkedHashSet<>();
        if (values != null) {
            for (MemberId value : values) {
                if (value == null) {
                    throw new CustomerPoolDomainException(description + " are invalid");
                }
                unique.add(value);
            }
        }
        if (unique.size() > max) {
            throw new CustomerPoolDomainException(description + " are invalid");
        }
        return Set.copyOf(unique);
    }

    private static Set<DepartmentId> departments(Collection<DepartmentId> values) {
        LinkedHashSet<DepartmentId> unique = new LinkedHashSet<>();
        if (values != null) {
            for (DepartmentId value : values) {
                if (value == null) {
                    throw new CustomerPoolDomainException("customer pool eligible departments are invalid");
                }
                unique.add(value);
            }
        }
        if (unique.size() > MAX_ELIGIBLE_DEPARTMENTS) {
            throw new CustomerPoolDomainException("customer pool eligible departments are invalid");
        }
        return Set.copyOf(unique);
    }

    private static List<CustomerPoolRule> rules(
            Collection<CustomerPoolRule> values, boolean enabled) {
        List<CustomerPoolRule> result;
        try {
            result = values == null ? List.of() : List.copyOf(values);
        } catch (NullPointerException exception) {
            throw new CustomerPoolDomainException("customer pool rules are invalid");
        }
        if (result.size() > MAX_RULES
                || new LinkedHashSet<>(result.stream().map(CustomerPoolRule::type).toList()).size()
                != result.size()) {
            throw new CustomerPoolDomainException("customer pool rules are invalid");
        }
        if (enabled && result.isEmpty()) {
            throw new CustomerPoolDomainException("automatic release requires a rule");
        }
        return result;
    }

    private static int requireRange(int value, int min, int max, String description) {
        if (value < min || value > max) {
            throw new CustomerPoolDomainException(description + " is invalid");
        }
        return value;
    }
}
