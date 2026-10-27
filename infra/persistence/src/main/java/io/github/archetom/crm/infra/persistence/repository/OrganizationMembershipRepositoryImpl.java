package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OrganizationAlreadyExistsException;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.OrganizationMembershipPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OrganizationMembershipMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.OrganizationMembershipPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

/** Repository that keeps authorization membership tenant-scoped. */
@Repository
@RequiredArgsConstructor
public class OrganizationMembershipRepositoryImpl implements OrganizationMembershipRepository {

    private final OrganizationMembershipMapper membershipMapper;
    private final OrganizationMembershipPOConverter membershipPOConverter;

    @Override
    public OrganizationMembership save(TenantId tenantId, OrganizationMembership membership) {
        requireTenantMatch(tenantId, membership);
        OrganizationMembershipPO po = membershipPOConverter.toPO(membership);
        try {
            if (po.getId() == null) {
                if (membershipMapper.insert(po) != 1) {
                    throw new IllegalStateException("Organization membership insert did not affect exactly one row");
                }
            } else if (membershipMapper.update(po, tenantFilter(tenantId).eq(OrganizationMembershipPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new OrganizationAlreadyExistsException();
        }
        membership.onPersisted(new MembershipId(po.getId()), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return membership;
    }

    @Override
    public Optional<OrganizationMembership> findByMember(TenantId tenantId, MemberId memberId) {
        if (memberId == null) return Optional.empty();
        return Optional.ofNullable(membershipPOConverter.toDomain(membershipMapper.selectOne(tenantFilter(tenantId)
                .eq(OrganizationMembershipPO::getActorId, memberId.getValue()))));
    }

    @Override
    public Optional<OrganizationMembership> findByMemberForShare(
            TenantId tenantId, MemberId memberId) {
        if (memberId == null) return Optional.empty();
        return Optional.ofNullable(membershipPOConverter.toDomain(
                membershipMapper.selectOne(tenantFilter(tenantId)
                        .eq(OrganizationMembershipPO::getActorId, memberId.getValue())
                        .last("FOR SHARE"))));
    }

    @Override
    public List<OrganizationMembership> findByMembers(TenantId tenantId, Collection<MemberId> memberIds) {
        requireTenant(tenantId);
        if (memberIds == null || memberIds.isEmpty()) return List.of();
        List<Long> ids = memberIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(MemberId::getValue)
                .distinct()
                .toList();
        if (ids.isEmpty()) return List.of();
        return membershipMapper.selectList(tenantFilter(tenantId)
                        .in(OrganizationMembershipPO::getActorId, ids)
                        .orderByAsc(OrganizationMembershipPO::getActorId))
                .stream().map(membershipPOConverter::toDomain).toList();
    }

    @Override
    public List<OrganizationMembership> findActiveByDepartments(
            TenantId tenantId, Collection<DepartmentId> departmentIds) {
        requireTenant(tenantId);
        if (departmentIds == null || departmentIds.isEmpty()) return List.of();
        List<Long> ids = departmentIds.stream().filter(java.util.Objects::nonNull)
                .map(DepartmentId::getValue).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return membershipMapper.selectList(tenantFilter(tenantId)
                        .in(OrganizationMembershipPO::getDepartmentId, ids)
                        .eq(OrganizationMembershipPO::getStatus, "ACTIVE")
                        .orderByAsc(OrganizationMembershipPO::getActorId))
                .stream().map(membershipPOConverter::toDomain).toList();
    }

    @Override
    public List<OrganizationMembership> findActiveByMember(MemberId memberId) {
        if (memberId == null) return List.of();
        return membershipMapper.selectList(new LambdaQueryWrapper<OrganizationMembershipPO>()
                        .eq(OrganizationMembershipPO::getActorId, memberId.getValue())
                        .eq(OrganizationMembershipPO::getStatus, "ACTIVE")
                        .orderByAsc(OrganizationMembershipPO::getTenantId))
                .stream().map(membershipPOConverter::toDomain).toList();
    }

    @Override
    public boolean existsActiveInTenant(TenantId tenantId) {
        return membershipMapper.selectCount(tenantFilter(tenantId)
                .eq(OrganizationMembershipPO::getStatus, "ACTIVE")) > 0;
    }

    @Override
    public boolean existsActiveInDepartment(TenantId tenantId, DepartmentId departmentId) {
        if (departmentId == null) return false;
        return membershipMapper.selectCount(tenantFilter(tenantId)
                .eq(OrganizationMembershipPO::getDepartmentId, departmentId.getValue())
                .eq(OrganizationMembershipPO::getStatus, "ACTIVE")) > 0;
    }

    @Override
    public boolean existsInDepartment(TenantId tenantId, DepartmentId departmentId) {
        if (departmentId == null) return false;
        return membershipMapper.selectCount(tenantFilter(tenantId)
                .eq(OrganizationMembershipPO::getDepartmentId, departmentId.getValue())) > 0;
    }

    private LambdaQueryWrapper<OrganizationMembershipPO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<OrganizationMembershipPO>()
                .eq(OrganizationMembershipPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, OrganizationMembership membership) {
        requireTenant(tenantId);
        if (membership == null || membership.getTenantId() == null
                || !tenantId.sameValueAs(membership.getTenantId())) {
            throw new IllegalArgumentException("membership tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
    }
}
