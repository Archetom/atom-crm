package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolMembership;
import io.github.archetom.crm.domain.entity.CustomerPoolRule;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.CustomerPoolAlreadyExistsException;
import io.github.archetom.crm.domain.exception.CustomerPoolDomainException;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.model.CustomerPoolHistorySummary;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomerPoolPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolAdministratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolClaimHistoryMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolEligibleDepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolEligibleMemberMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolMembershipMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolReleaseHistoryMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolRuleMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolAdministratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolClaimHistoryPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolCustomerCountPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolEligibleDepartmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolEligibleMemberPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolMembershipPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolReleaseHistoryPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolRulePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
@RequiredArgsConstructor
public class CustomerPoolRepositoryImpl implements CustomerPoolRepository {

    private final CustomerPoolMapper poolMapper;
    private final OrganizationTenantRepository organizationTenantRepository;
    private final CustomerPoolAdministratorMapper administratorMapper;
    private final CustomerPoolEligibleMemberMapper eligibleMemberMapper;
    private final CustomerPoolEligibleDepartmentMapper eligibleDepartmentMapper;
    private final CustomerPoolRuleMapper ruleMapper;
    private final CustomerPoolMembershipMapper membershipMapper;
    private final CustomerPoolClaimHistoryMapper claimHistoryMapper;
    private final CustomerPoolReleaseHistoryMapper releaseHistoryMapper;
    private final CustomerPoolPOConverter converter;

    @Override
    public CustomerPool save(TenantId tenantId, CustomerPool pool) {
        requireTenantMatch(tenantId, pool);
        CustomerPoolPO po = converter.toPO(pool);
        try {
            if (po.getId() == null) {
                requireCreationCapacity(tenantId);
                if (poolMapper.insert(po) != 1) {
                    throw new IllegalStateException("Customer pool insert did not affect one row");
                }
            } else if (poolMapper.update(po, root(tenantId).eq(CustomerPoolPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            CustomerPoolId poolId = new CustomerPoolId(po.getId());
            replaceChildren(tenantId, poolId, pool);
            pool.onPersisted(poolId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return pool;
        } catch (DuplicateKeyException exception) {
            throw new CustomerPoolAlreadyExistsException();
        }
    }

    @Override
    public Optional<CustomerPool> findById(TenantId tenantId, CustomerPoolId poolId) {
        requireTenant(tenantId);
        if (poolId == null) return Optional.empty();
        CustomerPoolPO po = poolMapper.selectOne(root(tenantId)
                .eq(CustomerPoolPO::getId, poolId.getValue())
                .ne(CustomerPoolPO::getStatus, CustomerPoolStatus.DELETED.name()));
        return hydrate(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<CustomerPool> findByIdForUpdate(TenantId tenantId, CustomerPoolId poolId) {
        requireTenant(tenantId);
        if (poolId == null) return Optional.empty();
        CustomerPoolPO po = poolMapper.selectOne(root(tenantId)
                .eq(CustomerPoolPO::getId, poolId.getValue())
                .ne(CustomerPoolPO::getStatus, CustomerPoolStatus.DELETED.name())
                .last("FOR UPDATE"));
        return hydrate(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public PageResult<CustomerPool> findAll(TenantId tenantId, int page, int size) {
        requireTenant(tenantId);
        IPage<CustomerPoolPO> result = poolMapper.selectPage(new Page<>(page, size), root(tenantId)
                .ne(CustomerPoolPO::getStatus, CustomerPoolStatus.DELETED.name())
                .orderByDesc(CustomerPoolPO::getUpdatedTime).orderByDesc(CustomerPoolPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                hydrate(tenantId, result.getRecords()));
    }

    @Override
    public List<CustomerPool> findAutomaticReleaseAfter(
            TenantId tenantId, CustomerPoolId afterPoolId, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("customer pool scan limit is invalid");
        }
        LambdaQueryWrapper<CustomerPoolPO> query = root(tenantId)
                .eq(CustomerPoolPO::getStatus, CustomerPoolStatus.ACTIVE.name())
                .eq(CustomerPoolPO::getAutomaticReleaseEnabled, true);
        if (afterPoolId != null) {
            query.gt(CustomerPoolPO::getId, afterPoolId.getValue());
        }
        return hydrate(tenantId, poolMapper.selectList(query
                .orderByAsc(CustomerPoolPO::getId).last("LIMIT " + limit)));
    }

    @Override
    public boolean hasActiveAutomaticRelease(TenantId tenantId) {
        requireTenant(tenantId);
        return poolMapper.existsActiveAutomaticRelease(tenantId.getValue());
    }

    @Override
    public CustomerPoolId lockAutomationCursor(TenantId tenantId) {
        requireTenant(tenantId);
        poolMapper.ensureAutomationCursor(tenantId.getValue());
        Long lastPoolId = poolMapper.selectAutomationCursorForUpdate(tenantId.getValue());
        return lastPoolId == null ? null : new CustomerPoolId(lastPoolId);
    }

    @Override
    public void saveAutomationCursor(TenantId tenantId, CustomerPoolId lastPoolId) {
        requireTenant(tenantId);
        Long value = lastPoolId == null ? null : lastPoolId.getValue();
        if (poolMapper.updateAutomationCursor(tenantId.getValue(), value) != 1) {
            throw new IllegalStateException("Customer pool automation cursor was not locked");
        }
    }

    @Override
    public boolean existsByName(TenantId tenantId, String name, CustomerPoolId excludedId) {
        requireTenant(tenantId);
        LambdaQueryWrapper<CustomerPoolPO> query = root(tenantId)
                .eq(CustomerPoolPO::getName, name)
                .ne(CustomerPoolPO::getStatus, CustomerPoolStatus.DELETED.name());
        if (excludedId != null) query.ne(CustomerPoolPO::getId, excludedId.getValue());
        return poolMapper.selectCount(query) > 0;
    }

    @Override
    public long countCustomers(TenantId tenantId, CustomerPoolId poolId) {
        require(tenantId, poolId);
        return membershipMapper.selectCount(new LambdaQueryWrapper<CustomerPoolMembershipPO>()
                .eq(CustomerPoolMembershipPO::getTenantId, tenantId.getValue())
                .eq(CustomerPoolMembershipPO::getPoolId, poolId.getValue()));
    }

    @Override
    public Map<CustomerPoolId, Long> countCustomers(
            TenantId tenantId, Collection<CustomerPoolId> poolIds) {
        requireTenant(tenantId);
        LinkedHashMap<CustomerPoolId, Long> counts = new LinkedHashMap<>();
        if (poolIds != null) {
            poolIds.stream().filter(java.util.Objects::nonNull).distinct()
                    .forEach(poolId -> counts.put(poolId, 0L));
        }
        if (counts.isEmpty()) {
            return Map.of();
        }
        Map<Long, CustomerPoolId> ids = counts.keySet().stream().collect(Collectors.toMap(
                CustomerPoolId::getValue, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
        for (CustomerPoolCustomerCountPO count : poolMapper.selectCustomerCounts(
                tenantId.getValue(), ids.keySet().stream().toList())) {
            CustomerPoolId poolId = ids.get(count.getPoolId());
            if (poolId != null) {
                counts.put(poolId, count.getCustomerCount());
            }
        }
        return Map.copyOf(counts);
    }

    @Override
    public void addMembership(CustomerPoolMembership membership) {
        if (membership == null) throw new IllegalArgumentException("pool membership must not be null");
        CustomerPoolMembershipPO po = new CustomerPoolMembershipPO();
        po.setTenantId(membership.tenantId().getValue());
        po.setPoolId(membership.poolId().getValue());
        po.setCustomerId(membership.customerId().getValue());
        po.setPreviousOwnerActorId(membership.previousOwnerMemberId() == null
                ? null : membership.previousOwnerMemberId().getValue());
        po.setEnteredTime(membership.enteredTime());
        try {
            membershipMapper.insert(po);
        } catch (DuplicateKeyException exception) {
            throw new CustomerPoolAlreadyExistsException();
        }
    }

    @Override
    public Optional<CustomerPoolMembership> findMembershipForUpdate(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId) {
        require(tenantId, poolId);
        if (customerId == null) return Optional.empty();
        CustomerPoolMembershipPO po = membershipMapper.selectOne(
                new LambdaQueryWrapper<CustomerPoolMembershipPO>()
                        .eq(CustomerPoolMembershipPO::getTenantId, tenantId.getValue())
                        .eq(CustomerPoolMembershipPO::getPoolId, poolId.getValue())
                        .eq(CustomerPoolMembershipPO::getCustomerId, customerId.getValue())
                        .last("FOR UPDATE"));
        return Optional.ofNullable(po).map(this::membership);
    }

    @Override
    public void removeMembership(TenantId tenantId, CustomerPoolId poolId, CustomerId customerId) {
        require(tenantId, poolId);
        if (customerId == null) throw new IllegalArgumentException("customer ID must not be null");
        if (membershipMapper.delete(new LambdaQueryWrapper<CustomerPoolMembershipPO>()
                .eq(CustomerPoolMembershipPO::getTenantId, tenantId.getValue())
                .eq(CustomerPoolMembershipPO::getPoolId, poolId.getValue())
                .eq(CustomerPoolMembershipPO::getCustomerId, customerId.getValue())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    @Override
    public int transferMemberships(
            TenantId tenantId, CustomerPoolId sourcePoolId, CustomerPoolId targetPoolId) {
        require(tenantId, sourcePoolId);
        if (targetPoolId == null || sourcePoolId.sameValueAs(targetPoolId)) {
            throw new IllegalArgumentException("target customer pool is invalid");
        }
        return poolMapper.transferMemberships(
                tenantId.getValue(), sourcePoolId.getValue(), targetPoolId.getValue());
    }

    @Override
    public int countClaims(
            TenantId tenantId, CustomerPoolId poolId, MemberId memberId, LocalDate claimDate) {
        require(tenantId, poolId);
        if (memberId == null || claimDate == null) throw new IllegalArgumentException("claim key is invalid");
        LocalDateTime start = claimDate.atStartOfDay();
        LocalDateTime end = claimDate.plusDays(1).atStartOfDay();
        return Math.toIntExact(claimHistoryMapper.selectCount(
                new LambdaQueryWrapper<CustomerPoolClaimHistoryPO>()
                        .eq(CustomerPoolClaimHistoryPO::getTenantId, tenantId.getValue())
                        .eq(CustomerPoolClaimHistoryPO::getPoolId, poolId.getValue())
                        .eq(CustomerPoolClaimHistoryPO::getActorId, memberId.getValue())
                        .eq(CustomerPoolClaimHistoryPO::getAssigned, false)
                        .ge(CustomerPoolClaimHistoryPO::getClaimedTime, start)
                        .lt(CustomerPoolClaimHistoryPO::getClaimedTime, end)));
    }

    @Override
    public void recordClaim(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId,
            MemberId memberId, LocalDateTime claimedTime, boolean assigned) {
        require(tenantId, poolId);
        if (customerId == null || memberId == null || claimedTime == null) {
            throw new IllegalArgumentException("claim history is invalid");
        }
        CustomerPoolClaimHistoryPO po = new CustomerPoolClaimHistoryPO();
        po.setTenantId(tenantId.getValue());
        po.setPoolId(poolId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setActorId(memberId.getValue());
        po.setAssigned(assigned);
        po.setClaimedTime(claimedTime);
        claimHistoryMapper.insert(po);
    }

    @Override
    public void recordRelease(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId,
            MemberId previousOwnerMemberId, LocalDateTime releasedTime) {
        require(tenantId, poolId);
        if (customerId == null || releasedTime == null) {
            throw new IllegalArgumentException("release history is invalid");
        }
        CustomerPoolReleaseHistoryPO po = new CustomerPoolReleaseHistoryPO();
        po.setTenantId(tenantId.getValue());
        po.setPoolId(poolId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setPreviousOwnerActorId(previousOwnerMemberId == null
                ? null : previousOwnerMemberId.getValue());
        po.setReleasedTime(releasedTime);
        releaseHistoryMapper.insert(po);
    }

    @Override
    public CustomerPoolHistorySummary summarizeEventHistory(
            TenantId tenantId, Collection<CustomerPoolId> accessiblePoolIds,
            CustomerVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        if (visibility == null || range == null) {
            throw new IllegalArgumentException("customer pool history summary is invalid");
        }
        List<Long> poolIds = accessiblePoolIds == null ? List.of() : accessiblePoolIds.stream()
                .filter(java.util.Objects::nonNull).map(CustomerPoolId::getValue).distinct().toList();
        if (poolIds.isEmpty()) {
            return new CustomerPoolHistorySummary(0L, 0L);
        }
        LocalDateTime from = range.from().atStartOfDay();
        LocalDateTime to = range.to().atTime(23, 59, 59, 999_999_000);
        QueryWrapper<CustomerPoolReleaseHistoryPO> releases = new QueryWrapper<CustomerPoolReleaseHistoryPO>()
                .eq("tenant_id", tenantId.getValue())
                .in("pool_id", poolIds)
                .ge("released_time", from)
                .le("released_time", to);
        applyActorVisibility(releases, visibility, "crm_customer_pool_release_history",
                "previous_owner_actor_id");
        QueryWrapper<CustomerPoolClaimHistoryPO> claims = new QueryWrapper<CustomerPoolClaimHistoryPO>()
                .eq("tenant_id", tenantId.getValue())
                .in("pool_id", poolIds)
                .eq("is_assigned", false)
                .ge("claimed_time", from)
                .le("claimed_time", to);
        applyActorVisibility(claims, visibility, "crm_customer_pool_claim_history", "actor_id");
        return new CustomerPoolHistorySummary(
                Math.max(0L, releaseHistoryMapper.selectCount(releases)),
                Math.max(0L, claimHistoryMapper.selectCount(claims)));
    }

    @Override
    public List<CustomerId> findAutomationCandidates(
            TenantId tenantId, CustomerPool pool, CustomerPoolRule rule,
            LocalDateTime cutoff, int limit) {
        requireTenantMatch(tenantId, pool);
        if (pool.getId() == null || rule == null || cutoff == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("automation query is invalid");
        }
        return poolMapper.selectAutomationCandidateIds(tenantId.getValue(), pool.getId().getValue(),
                        rule.type().name(), cutoff, limit).stream()
                .map(CustomerId::new).toList();
    }

    private void replaceChildren(TenantId tenantId, CustomerPoolId poolId, CustomerPool pool) {
        administratorMapper.delete(new LambdaQueryWrapper<CustomerPoolAdministratorPO>()
                .eq(CustomerPoolAdministratorPO::getTenantId, tenantId.getValue())
                .eq(CustomerPoolAdministratorPO::getPoolId, poolId.getValue()));
        eligibleMemberMapper.delete(new LambdaQueryWrapper<CustomerPoolEligibleMemberPO>()
                .eq(CustomerPoolEligibleMemberPO::getTenantId, tenantId.getValue())
                .eq(CustomerPoolEligibleMemberPO::getPoolId, poolId.getValue()));
        eligibleDepartmentMapper.delete(new LambdaQueryWrapper<CustomerPoolEligibleDepartmentPO>()
                .eq(CustomerPoolEligibleDepartmentPO::getTenantId, tenantId.getValue())
                .eq(CustomerPoolEligibleDepartmentPO::getPoolId, poolId.getValue()));
        ruleMapper.delete(new LambdaQueryWrapper<CustomerPoolRulePO>()
                .eq(CustomerPoolRulePO::getTenantId, tenantId.getValue())
                .eq(CustomerPoolRulePO::getPoolId, poolId.getValue()));
        if (pool.getStatus() == CustomerPoolStatus.DELETED) return;
        pool.getAdministratorMemberIds().forEach(id -> administratorMapper.insert(
                administrator(tenantId, poolId, id)));
        pool.getEligibleMemberIds().forEach(id -> eligibleMemberMapper.insert(
                eligibleMember(tenantId, poolId, id)));
        pool.getEligibleDepartmentIds().forEach(id -> eligibleDepartmentMapper.insert(
                eligibleDepartment(tenantId, poolId, id)));
        pool.getRules().forEach(rule -> ruleMapper.insert(rule(tenantId, poolId, rule)));
    }

    private List<CustomerPool> hydrate(TenantId tenantId, List<CustomerPoolPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> poolIds = roots.stream().map(CustomerPoolPO::getId).toList();
        Map<Long, List<Long>> admins = administratorMapper.selectList(
                        new LambdaQueryWrapper<CustomerPoolAdministratorPO>()
                                .eq(CustomerPoolAdministratorPO::getTenantId, tenantId.getValue())
                                .in(CustomerPoolAdministratorPO::getPoolId, poolIds))
                .stream().collect(Collectors.groupingBy(CustomerPoolAdministratorPO::getPoolId,
                        LinkedHashMap::new, Collectors.mapping(CustomerPoolAdministratorPO::getActorId,
                                Collectors.toList())));
        Map<Long, List<Long>> members = eligibleMemberMapper.selectList(
                        new LambdaQueryWrapper<CustomerPoolEligibleMemberPO>()
                                .eq(CustomerPoolEligibleMemberPO::getTenantId, tenantId.getValue())
                                .in(CustomerPoolEligibleMemberPO::getPoolId, poolIds))
                .stream().collect(Collectors.groupingBy(CustomerPoolEligibleMemberPO::getPoolId,
                        LinkedHashMap::new, Collectors.mapping(CustomerPoolEligibleMemberPO::getActorId,
                                Collectors.toList())));
        Map<Long, List<Long>> departments = eligibleDepartmentMapper.selectList(
                        new LambdaQueryWrapper<CustomerPoolEligibleDepartmentPO>()
                                .eq(CustomerPoolEligibleDepartmentPO::getTenantId, tenantId.getValue())
                                .in(CustomerPoolEligibleDepartmentPO::getPoolId, poolIds))
                .stream().collect(Collectors.groupingBy(CustomerPoolEligibleDepartmentPO::getPoolId,
                        LinkedHashMap::new, Collectors.mapping(CustomerPoolEligibleDepartmentPO::getDepartmentId,
                                Collectors.toList())));
        Map<Long, List<CustomerPoolRulePO>> rules = ruleMapper.selectList(
                        new LambdaQueryWrapper<CustomerPoolRulePO>()
                                .eq(CustomerPoolRulePO::getTenantId, tenantId.getValue())
                                .in(CustomerPoolRulePO::getPoolId, poolIds)
                                .orderByAsc(CustomerPoolRulePO::getRuleType))
                .stream().collect(Collectors.groupingBy(CustomerPoolRulePO::getPoolId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(po -> converter.toDomain(po,
                admins.getOrDefault(po.getId(), List.of()),
                members.getOrDefault(po.getId(), List.of()),
                departments.getOrDefault(po.getId(), List.of()),
                rules.getOrDefault(po.getId(), List.of()))).toList();
    }

    private CustomerPoolMembership membership(CustomerPoolMembershipPO po) {
        return new CustomerPoolMembership(new TenantId(po.getTenantId()),
                new CustomerPoolId(po.getPoolId()), new CustomerId(po.getCustomerId()),
                po.getPreviousOwnerActorId() == null ? null : new MemberId(po.getPreviousOwnerActorId()),
                po.getEnteredTime());
    }

    private void applyActorVisibility(
            QueryWrapper<?> query, CustomerVisibility visibility,
            String historyTable, String actorColumn) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            query.eq(actorColumn, visibility.callerMemberId().getValue());
            return;
        }
        List<Long> departmentIds = visibility.ownerDepartmentIds().stream()
                .map(DepartmentId::getValue).toList();
        if (departmentIds.isEmpty()) {
            throw new IllegalArgumentException("customer pool history department scope is invalid");
        }
        String placeholders = IntStream.range(0, departmentIds.size())
                .mapToObj(index -> "{" + index + "}").collect(Collectors.joining(", "));
        query.apply("""
                EXISTS (
                    SELECT 1
                    FROM organization_membership event_actor
                    WHERE event_actor.tenant_id = %s.tenant_id
                      AND event_actor.actor_id = %s.%s
                      AND event_actor.status = 'ACTIVE'
                      AND event_actor.department_id IN (%s)
                )
                """.formatted(historyTable, historyTable, actorColumn, placeholders),
                departmentIds.toArray());
    }

    private CustomerPoolAdministratorPO administrator(
            TenantId tenantId, CustomerPoolId poolId, MemberId memberId) {
        CustomerPoolAdministratorPO po = new CustomerPoolAdministratorPO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue()); po.setActorId(memberId.getValue());
        return po;
    }

    private CustomerPoolEligibleMemberPO eligibleMember(
            TenantId tenantId, CustomerPoolId poolId, MemberId memberId) {
        CustomerPoolEligibleMemberPO po = new CustomerPoolEligibleMemberPO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue()); po.setActorId(memberId.getValue());
        return po;
    }

    private CustomerPoolEligibleDepartmentPO eligibleDepartment(
            TenantId tenantId, CustomerPoolId poolId, DepartmentId departmentId) {
        CustomerPoolEligibleDepartmentPO po = new CustomerPoolEligibleDepartmentPO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue());
        po.setDepartmentId(departmentId.getValue());
        return po;
    }

    private CustomerPoolRulePO rule(
            TenantId tenantId, CustomerPoolId poolId, CustomerPoolRule rule) {
        CustomerPoolRulePO po = new CustomerPoolRulePO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue());
        po.setRuleType(rule.type().name()); po.setInactiveDays(rule.inactiveDays());
        return po;
    }

    private LambdaQueryWrapper<CustomerPoolPO> root(TenantId tenantId) {
        return new LambdaQueryWrapper<CustomerPoolPO>().eq(CustomerPoolPO::getTenantId, tenantId.getValue());
    }

    private void requireCreationCapacity(TenantId tenantId) {
        if (!organizationTenantRepository.lockForTenantChanges(tenantId)) {
            throw new CustomerPoolDomainException("customer pool tenant is not initialized");
        }
        long currentPools = poolMapper.selectNonDeletedCountForUpdate(tenantId.getValue());
        if (currentPools >= CustomerPool.MAX_POOLS_PER_TENANT) {
            throw new CustomerPoolDomainException("customer pool tenant limit was reached");
        }
    }

    private void requireTenantMatch(TenantId tenantId, CustomerPool pool) {
        requireTenant(tenantId);
        if (pool == null || pool.getTenantId() == null || !tenantId.sameValueAs(pool.getTenantId())) {
            throw new IllegalArgumentException("customer pool tenant mismatch");
        }
    }

    private void require(TenantId tenantId, CustomerPoolId poolId) {
        requireTenant(tenantId);
        if (poolId == null) throw new IllegalArgumentException("customer pool ID must not be null");
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
    }
}
