package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OrganizationAlreadyExistsException;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.MemberProfilePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MemberProfileMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.MemberProfilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collection;
import java.util.Optional;

/** Repository for profiles tied to a verified actor identity. */
@Repository
@RequiredArgsConstructor
public class MemberProfileRepositoryImpl implements MemberProfileRepository {

    private final MemberProfileMapper memberProfileMapper;
    private final MemberProfilePOConverter memberProfilePOConverter;

    @Override
    public MemberProfile save(MemberProfile profile) {
        if (profile == null || profile.getId() == null) {
            throw new IllegalArgumentException("member profile and its actor ID must not be null");
        }
        MemberProfilePO po = memberProfilePOConverter.toPO(profile);
        try {
            if (memberProfileMapper.selectById(po.getActorId()) == null) {
                if (memberProfileMapper.insert(po) != 1) {
                    throw new IllegalStateException("Member profile insert did not affect exactly one row");
                }
            } else if (memberProfileMapper.updateById(po) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new OrganizationAlreadyExistsException();
        }
        profile.onPersisted(po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return profile;
    }

    @Override
    public Optional<MemberProfile> findById(MemberId memberId) {
        if (memberId == null) return Optional.empty();
        return Optional.ofNullable(memberProfilePOConverter.toDomain(
                memberProfileMapper.selectById(memberId.getValue())));
    }

    @Override
    public PageResult<MemberProfile> findActiveByTenant(TenantId tenantId, String keyword, int page, int size) {
        if (tenantId == null) throw new IllegalArgumentException("tenant ID must not be null");
        IPage<MemberProfilePO> result = memberProfileMapper.selectActiveProfilePage(
                new Page<>(page, size), tenantId.getValue(), keyword);
        return toPageResult(result);
    }

    @Override
    public PageResult<MemberProfile> findActiveByDepartment(
            TenantId tenantId, DepartmentId departmentId, String keyword, int page, int size) {
        if (tenantId == null || departmentId == null) {
            throw new IllegalArgumentException("tenant ID and department ID must not be null");
        }
        IPage<MemberProfilePO> result = memberProfileMapper.selectActiveDepartmentProfilePage(
                new Page<>(page, size), tenantId.getValue(), departmentId.getValue(), keyword);
        return toPageResult(result);
    }

    @Override
    public PageResult<MemberProfile> findActiveByDepartments(
            TenantId tenantId, Collection<DepartmentId> departmentIds, String keyword, int page, int size) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
        if (departmentIds == null || departmentIds.isEmpty()) {
            return new PageResult<>(page, size, 0L, java.util.List.of());
        }
        java.util.List<Long> ids = departmentIds.stream()
                .filter(java.util.Objects::nonNull)
                .map(DepartmentId::getValue)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new PageResult<>(page, size, 0L, java.util.List.of());
        }
        IPage<MemberProfilePO> result = memberProfileMapper.selectActiveDepartmentProfilesPage(
                new Page<>(page, size), tenantId.getValue(), ids, keyword);
        return toPageResult(result);
    }

    private PageResult<MemberProfile> toPageResult(IPage<MemberProfilePO> result) {
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(result.getTotal(), 0L),
                result.getRecords().stream().map(memberProfilePOConverter::toDomain).toList());
    }
}
