package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolMembership;
import io.github.archetom.crm.domain.entity.ContactPoolRule;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ContactPoolAlreadyExistsException;
import io.github.archetom.crm.domain.exception.ContactPoolDomainException;
import io.github.archetom.crm.domain.model.ContactPoolStatus;
import io.github.archetom.crm.domain.repository.ContactPoolRepository;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ContactPoolPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolAdministratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolClaimHistoryMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolEligibleDepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolEligibleMemberMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolMembershipMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolRuleMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolAdministratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolClaimHistoryPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolContactCountPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolEligibleDepartmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolEligibleMemberPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolMembershipPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolRulePO;
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

@Repository
@RequiredArgsConstructor
public class ContactPoolRepositoryImpl implements ContactPoolRepository {

    private final ContactPoolMapper poolMapper;
    private final OrganizationTenantRepository organizationTenantRepository;
    private final ContactPoolAdministratorMapper administratorMapper;
    private final ContactPoolEligibleMemberMapper eligibleMemberMapper;
    private final ContactPoolEligibleDepartmentMapper eligibleDepartmentMapper;
    private final ContactPoolRuleMapper ruleMapper;
    private final ContactPoolMembershipMapper membershipMapper;
    private final ContactPoolClaimHistoryMapper claimHistoryMapper;
    private final ContactPoolPOConverter converter;

    @Override
    public ContactPool save(TenantId tenantId, ContactPool pool) {
        requireTenantMatch(tenantId, pool);
        ContactPoolPO po = converter.toPO(pool);
        try {
            if (po.getId() == null) {
                requireCreationCapacity(tenantId);
                if (poolMapper.insert(po) != 1) {
                    throw new IllegalStateException("Contact pool insert did not affect one row");
                }
            } else if (poolMapper.update(po, root(tenantId).eq(ContactPoolPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            ContactPoolId poolId = new ContactPoolId(po.getId());
            replaceChildren(tenantId, poolId, pool);
            pool.onPersisted(poolId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return pool;
        } catch (DuplicateKeyException exception) {
            throw new ContactPoolAlreadyExistsException();
        }
    }

    @Override
    public Optional<ContactPool> findById(TenantId tenantId, ContactPoolId poolId) {
        requireTenant(tenantId);
        if (poolId == null) return Optional.empty();
        ContactPoolPO po = poolMapper.selectOne(root(tenantId)
                .eq(ContactPoolPO::getId, poolId.getValue())
                .ne(ContactPoolPO::getStatus, ContactPoolStatus.DELETED.name()));
        return hydrate(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<ContactPool> findByIdForUpdate(TenantId tenantId, ContactPoolId poolId) {
        requireTenant(tenantId);
        if (poolId == null) return Optional.empty();
        ContactPoolPO po = poolMapper.selectOne(root(tenantId)
                .eq(ContactPoolPO::getId, poolId.getValue())
                .ne(ContactPoolPO::getStatus, ContactPoolStatus.DELETED.name())
                .last("FOR UPDATE"));
        return hydrate(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public PageResult<ContactPool> findAll(TenantId tenantId, int page, int size) {
        requireTenant(tenantId);
        IPage<ContactPoolPO> result = poolMapper.selectPage(new Page<>(page, size), root(tenantId)
                .ne(ContactPoolPO::getStatus, ContactPoolStatus.DELETED.name())
                .orderByDesc(ContactPoolPO::getUpdatedTime).orderByDesc(ContactPoolPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                hydrate(tenantId, result.getRecords()));
    }

    @Override
    public List<ContactPool> findAutomaticReleaseAfter(
            TenantId tenantId, ContactPoolId afterPoolId, int limit) {
        requireTenant(tenantId);
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("contact pool scan limit is invalid");
        }
        LambdaQueryWrapper<ContactPoolPO> query = root(tenantId)
                .eq(ContactPoolPO::getStatus, ContactPoolStatus.ACTIVE.name())
                .eq(ContactPoolPO::getAutomaticReleaseEnabled, true);
        if (afterPoolId != null) {
            query.gt(ContactPoolPO::getId, afterPoolId.getValue());
        }
        return hydrate(tenantId, poolMapper.selectList(query
                .orderByAsc(ContactPoolPO::getId).last("LIMIT " + limit)));
    }

    @Override
    public boolean hasActiveAutomaticRelease(TenantId tenantId) {
        requireTenant(tenantId);
        return poolMapper.existsActiveAutomaticRelease(tenantId.getValue());
    }

    @Override
    public ContactPoolId lockAutomationCursor(TenantId tenantId) {
        requireTenant(tenantId);
        poolMapper.ensureAutomationCursor(tenantId.getValue());
        Long lastPoolId = poolMapper.selectAutomationCursorForUpdate(tenantId.getValue());
        return lastPoolId == null ? null : new ContactPoolId(lastPoolId);
    }

    @Override
    public void saveAutomationCursor(TenantId tenantId, ContactPoolId lastPoolId) {
        requireTenant(tenantId);
        Long value = lastPoolId == null ? null : lastPoolId.getValue();
        if (poolMapper.updateAutomationCursor(tenantId.getValue(), value) != 1) {
            throw new IllegalStateException("Contact pool automation cursor was not locked");
        }
    }

    @Override
    public boolean existsByName(TenantId tenantId, String name, ContactPoolId excludedId) {
        requireTenant(tenantId);
        LambdaQueryWrapper<ContactPoolPO> query = root(tenantId)
                .eq(ContactPoolPO::getName, name)
                .ne(ContactPoolPO::getStatus, ContactPoolStatus.DELETED.name());
        if (excludedId != null) query.ne(ContactPoolPO::getId, excludedId.getValue());
        return poolMapper.selectCount(query) > 0;
    }

    @Override
    public long countContacts(TenantId tenantId, ContactPoolId poolId) {
        require(tenantId, poolId);
        return membershipMapper.selectCount(new LambdaQueryWrapper<ContactPoolMembershipPO>()
                .eq(ContactPoolMembershipPO::getTenantId, tenantId.getValue())
                .eq(ContactPoolMembershipPO::getPoolId, poolId.getValue()));
    }

    @Override
    public Map<ContactPoolId, Long> countContacts(
            TenantId tenantId, Collection<ContactPoolId> poolIds) {
        requireTenant(tenantId);
        LinkedHashMap<ContactPoolId, Long> counts = new LinkedHashMap<>();
        if (poolIds != null) {
            poolIds.stream().filter(java.util.Objects::nonNull).distinct()
                    .forEach(poolId -> counts.put(poolId, 0L));
        }
        if (counts.isEmpty()) {
            return Map.of();
        }
        Map<Long, ContactPoolId> ids = counts.keySet().stream().collect(Collectors.toMap(
                ContactPoolId::getValue, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
        for (ContactPoolContactCountPO count : poolMapper.selectContactCounts(
                tenantId.getValue(), ids.keySet().stream().toList())) {
            ContactPoolId poolId = ids.get(count.getPoolId());
            if (poolId != null) {
                counts.put(poolId, count.getContactCount());
            }
        }
        return Map.copyOf(counts);
    }

    @Override
    public void addMembership(ContactPoolMembership membership) {
        if (membership == null) throw new IllegalArgumentException("pool membership must not be null");
        ContactPoolMembershipPO po = new ContactPoolMembershipPO();
        po.setTenantId(membership.tenantId().getValue());
        po.setPoolId(membership.poolId().getValue());
        po.setContactId(membership.contactId().getValue());
        po.setPreviousOwnerActorId(membership.previousOwnerMemberId() == null
                ? null : membership.previousOwnerMemberId().getValue());
        po.setEnteredTime(membership.enteredTime());
        try {
            membershipMapper.insert(po);
        } catch (DuplicateKeyException exception) {
            throw new ContactPoolAlreadyExistsException();
        }
    }

    @Override
    public Optional<ContactPoolMembership> findMembershipForUpdate(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId) {
        require(tenantId, poolId);
        if (contactId == null) return Optional.empty();
        ContactPoolMembershipPO po = membershipMapper.selectOne(
                new LambdaQueryWrapper<ContactPoolMembershipPO>()
                        .eq(ContactPoolMembershipPO::getTenantId, tenantId.getValue())
                        .eq(ContactPoolMembershipPO::getPoolId, poolId.getValue())
                        .eq(ContactPoolMembershipPO::getContactId, contactId.getValue())
                        .last("FOR UPDATE"));
        return Optional.ofNullable(po).map(this::membership);
    }

    @Override
    public void removeMembership(TenantId tenantId, ContactPoolId poolId, ContactId contactId) {
        require(tenantId, poolId);
        if (contactId == null) throw new IllegalArgumentException("contact ID must not be null");
        if (membershipMapper.delete(new LambdaQueryWrapper<ContactPoolMembershipPO>()
                .eq(ContactPoolMembershipPO::getTenantId, tenantId.getValue())
                .eq(ContactPoolMembershipPO::getPoolId, poolId.getValue())
                .eq(ContactPoolMembershipPO::getContactId, contactId.getValue())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    @Override
    public int transferMemberships(
            TenantId tenantId, ContactPoolId sourcePoolId, ContactPoolId targetPoolId) {
        require(tenantId, sourcePoolId);
        if (targetPoolId == null || sourcePoolId.sameValueAs(targetPoolId)) {
            throw new IllegalArgumentException("target contact pool is invalid");
        }
        return poolMapper.transferMemberships(
                tenantId.getValue(), sourcePoolId.getValue(), targetPoolId.getValue());
    }

    @Override
    public int countClaims(
            TenantId tenantId, ContactPoolId poolId, MemberId memberId, LocalDate claimDate) {
        require(tenantId, poolId);
        if (memberId == null || claimDate == null) throw new IllegalArgumentException("claim key is invalid");
        LocalDateTime start = claimDate.atStartOfDay();
        LocalDateTime end = claimDate.plusDays(1).atStartOfDay();
        return Math.toIntExact(claimHistoryMapper.selectCount(
                new LambdaQueryWrapper<ContactPoolClaimHistoryPO>()
                        .eq(ContactPoolClaimHistoryPO::getTenantId, tenantId.getValue())
                        .eq(ContactPoolClaimHistoryPO::getPoolId, poolId.getValue())
                        .eq(ContactPoolClaimHistoryPO::getActorId, memberId.getValue())
                        .eq(ContactPoolClaimHistoryPO::getAssigned, false)
                        .ge(ContactPoolClaimHistoryPO::getClaimedTime, start)
                        .lt(ContactPoolClaimHistoryPO::getClaimedTime, end)));
    }

    @Override
    public void recordClaim(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId,
            MemberId memberId, LocalDateTime claimedTime, boolean assigned) {
        require(tenantId, poolId);
        if (contactId == null || memberId == null || claimedTime == null) {
            throw new IllegalArgumentException("claim history is invalid");
        }
        ContactPoolClaimHistoryPO po = new ContactPoolClaimHistoryPO();
        po.setTenantId(tenantId.getValue());
        po.setPoolId(poolId.getValue());
        po.setContactId(contactId.getValue());
        po.setActorId(memberId.getValue());
        po.setAssigned(assigned);
        po.setClaimedTime(claimedTime);
        claimHistoryMapper.insert(po);
    }

    @Override
    public List<ContactId> findAutomationCandidates(
            TenantId tenantId, ContactPool pool, ContactPoolRule rule,
            LocalDateTime cutoff, int limit) {
        requireTenantMatch(tenantId, pool);
        if (pool.getId() == null || rule == null || cutoff == null || limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("automation query is invalid");
        }
        return poolMapper.selectAutomationCandidateIds(tenantId.getValue(), pool.getId().getValue(),
                        rule.type().name(), cutoff, limit).stream()
                .map(ContactId::new).toList();
    }

    private void replaceChildren(TenantId tenantId, ContactPoolId poolId, ContactPool pool) {
        administratorMapper.delete(new LambdaQueryWrapper<ContactPoolAdministratorPO>()
                .eq(ContactPoolAdministratorPO::getTenantId, tenantId.getValue())
                .eq(ContactPoolAdministratorPO::getPoolId, poolId.getValue()));
        eligibleMemberMapper.delete(new LambdaQueryWrapper<ContactPoolEligibleMemberPO>()
                .eq(ContactPoolEligibleMemberPO::getTenantId, tenantId.getValue())
                .eq(ContactPoolEligibleMemberPO::getPoolId, poolId.getValue()));
        eligibleDepartmentMapper.delete(new LambdaQueryWrapper<ContactPoolEligibleDepartmentPO>()
                .eq(ContactPoolEligibleDepartmentPO::getTenantId, tenantId.getValue())
                .eq(ContactPoolEligibleDepartmentPO::getPoolId, poolId.getValue()));
        ruleMapper.delete(new LambdaQueryWrapper<ContactPoolRulePO>()
                .eq(ContactPoolRulePO::getTenantId, tenantId.getValue())
                .eq(ContactPoolRulePO::getPoolId, poolId.getValue()));
        if (pool.getStatus() == ContactPoolStatus.DELETED) return;
        pool.getAdministratorMemberIds().forEach(id -> administratorMapper.insert(
                administrator(tenantId, poolId, id)));
        pool.getEligibleMemberIds().forEach(id -> eligibleMemberMapper.insert(
                eligibleMember(tenantId, poolId, id)));
        pool.getEligibleDepartmentIds().forEach(id -> eligibleDepartmentMapper.insert(
                eligibleDepartment(tenantId, poolId, id)));
        pool.getRules().forEach(rule -> ruleMapper.insert(rule(tenantId, poolId, rule)));
    }

    private List<ContactPool> hydrate(TenantId tenantId, List<ContactPoolPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> poolIds = roots.stream().map(ContactPoolPO::getId).toList();
        Map<Long, List<Long>> admins = administratorMapper.selectList(
                        new LambdaQueryWrapper<ContactPoolAdministratorPO>()
                                .eq(ContactPoolAdministratorPO::getTenantId, tenantId.getValue())
                                .in(ContactPoolAdministratorPO::getPoolId, poolIds))
                .stream().collect(Collectors.groupingBy(ContactPoolAdministratorPO::getPoolId,
                        LinkedHashMap::new, Collectors.mapping(ContactPoolAdministratorPO::getActorId,
                                Collectors.toList())));
        Map<Long, List<Long>> members = eligibleMemberMapper.selectList(
                        new LambdaQueryWrapper<ContactPoolEligibleMemberPO>()
                                .eq(ContactPoolEligibleMemberPO::getTenantId, tenantId.getValue())
                                .in(ContactPoolEligibleMemberPO::getPoolId, poolIds))
                .stream().collect(Collectors.groupingBy(ContactPoolEligibleMemberPO::getPoolId,
                        LinkedHashMap::new, Collectors.mapping(ContactPoolEligibleMemberPO::getActorId,
                                Collectors.toList())));
        Map<Long, List<Long>> departments = eligibleDepartmentMapper.selectList(
                        new LambdaQueryWrapper<ContactPoolEligibleDepartmentPO>()
                                .eq(ContactPoolEligibleDepartmentPO::getTenantId, tenantId.getValue())
                                .in(ContactPoolEligibleDepartmentPO::getPoolId, poolIds))
                .stream().collect(Collectors.groupingBy(ContactPoolEligibleDepartmentPO::getPoolId,
                        LinkedHashMap::new, Collectors.mapping(ContactPoolEligibleDepartmentPO::getDepartmentId,
                                Collectors.toList())));
        Map<Long, List<ContactPoolRulePO>> rules = ruleMapper.selectList(
                        new LambdaQueryWrapper<ContactPoolRulePO>()
                                .eq(ContactPoolRulePO::getTenantId, tenantId.getValue())
                                .in(ContactPoolRulePO::getPoolId, poolIds)
                                .orderByAsc(ContactPoolRulePO::getRuleType))
                .stream().collect(Collectors.groupingBy(ContactPoolRulePO::getPoolId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(po -> converter.toDomain(po,
                admins.getOrDefault(po.getId(), List.of()),
                members.getOrDefault(po.getId(), List.of()),
                departments.getOrDefault(po.getId(), List.of()),
                rules.getOrDefault(po.getId(), List.of()))).toList();
    }

    private ContactPoolMembership membership(ContactPoolMembershipPO po) {
        return new ContactPoolMembership(new TenantId(po.getTenantId()),
                new ContactPoolId(po.getPoolId()), new ContactId(po.getContactId()),
                po.getPreviousOwnerActorId() == null ? null : new MemberId(po.getPreviousOwnerActorId()),
                po.getEnteredTime());
    }

    private ContactPoolAdministratorPO administrator(
            TenantId tenantId, ContactPoolId poolId, MemberId memberId) {
        ContactPoolAdministratorPO po = new ContactPoolAdministratorPO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue()); po.setActorId(memberId.getValue());
        return po;
    }

    private ContactPoolEligibleMemberPO eligibleMember(
            TenantId tenantId, ContactPoolId poolId, MemberId memberId) {
        ContactPoolEligibleMemberPO po = new ContactPoolEligibleMemberPO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue()); po.setActorId(memberId.getValue());
        return po;
    }

    private ContactPoolEligibleDepartmentPO eligibleDepartment(
            TenantId tenantId, ContactPoolId poolId, DepartmentId departmentId) {
        ContactPoolEligibleDepartmentPO po = new ContactPoolEligibleDepartmentPO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue());
        po.setDepartmentId(departmentId.getValue());
        return po;
    }

    private ContactPoolRulePO rule(
            TenantId tenantId, ContactPoolId poolId, ContactPoolRule rule) {
        ContactPoolRulePO po = new ContactPoolRulePO();
        po.setTenantId(tenantId.getValue()); po.setPoolId(poolId.getValue());
        po.setRuleType(rule.type().name()); po.setInactiveDays(rule.inactiveDays());
        return po;
    }

    private LambdaQueryWrapper<ContactPoolPO> root(TenantId tenantId) {
        return new LambdaQueryWrapper<ContactPoolPO>().eq(ContactPoolPO::getTenantId, tenantId.getValue());
    }

    private void requireCreationCapacity(TenantId tenantId) {
        if (!organizationTenantRepository.lockForTenantChanges(tenantId)) {
            throw new ContactPoolDomainException("contact pool tenant is not initialized");
        }
        long currentPools = poolMapper.selectNonDeletedCountForUpdate(tenantId.getValue());
        if (currentPools >= ContactPool.MAX_POOLS_PER_TENANT) {
            throw new ContactPoolDomainException("contact pool tenant limit was reached");
        }
    }

    private void requireTenantMatch(TenantId tenantId, ContactPool pool) {
        requireTenant(tenantId);
        if (pool == null || pool.getTenantId() == null || !tenantId.sameValueAs(pool.getTenantId())) {
            throw new IllegalArgumentException("contact pool tenant mismatch");
        }
    }

    private void require(TenantId tenantId, ContactPoolId poolId) {
        requireTenant(tenantId);
        if (poolId == null) throw new IllegalArgumentException("contact pool ID must not be null");
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
    }
}
