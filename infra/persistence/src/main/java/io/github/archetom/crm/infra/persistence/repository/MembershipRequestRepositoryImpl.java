package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.repository.MembershipRequestRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.MembershipRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.MembershipRequestPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MembershipRequestMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.MembershipRequestPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

/** Repository for requests scoped to their target tenant at read time. */
@Repository
@RequiredArgsConstructor
public class MembershipRequestRepositoryImpl implements MembershipRequestRepository {

    private final MembershipRequestMapper membershipRequestMapper;
    private final MembershipRequestPOConverter membershipRequestPOConverter;

    @Override
    public MembershipRequest save(MembershipRequest request) {
        if (request == null || request.getTargetTenantId() == null) {
            throw new IllegalArgumentException("membership request and target tenant must not be null");
        }
        MembershipRequestPO po = membershipRequestPOConverter.toPO(request);
        try {
            if (po.getId() == null) {
                if (membershipRequestMapper.insert(po) != 1) {
                    throw new IllegalStateException("Membership request insert did not affect exactly one row");
                }
            } else if (membershipRequestMapper.update(po, targetTenantFilter(request.getTargetTenantId())
                    .eq(MembershipRequestPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new io.github.archetom.crm.domain.exception.OrganizationAlreadyExistsException();
        }
        request.onPersisted(new MembershipRequestId(po.getId()), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
        return request;
    }

    @Override
    public Optional<MembershipRequest> findByIdForTarget(TenantId targetTenantId, MembershipRequestId requestId) {
        if (requestId == null) return Optional.empty();
        return Optional.ofNullable(membershipRequestPOConverter.toDomain(membershipRequestMapper.selectOne(
                targetTenantFilter(targetTenantId).eq(MembershipRequestPO::getId, requestId.getValue()))));
    }

    @Override
    public Optional<MembershipRequest> findByIdForApplicant(MemberId applicantId, MembershipRequestId requestId) {
        if (applicantId == null || requestId == null) return Optional.empty();
        return Optional.ofNullable(membershipRequestPOConverter.toDomain(membershipRequestMapper.selectOne(
                new LambdaQueryWrapper<MembershipRequestPO>()
                        .eq(MembershipRequestPO::getApplicantActorId, applicantId.getValue())
                        .eq(MembershipRequestPO::getId, requestId.getValue()))));
    }

    @Override
    public boolean hasPending(MemberId applicantId, TenantId targetTenantId) {
        if (applicantId == null) return false;
        return membershipRequestMapper.selectCount(targetTenantFilter(targetTenantId)
                .eq(MembershipRequestPO::getApplicantActorId, applicantId.getValue())
                .eq(MembershipRequestPO::getStatus, "PENDING")) > 0;
    }

    @Override
    public PageResult<MembershipRequest> findByTargetTenant(TenantId targetTenantId, int page, int size) {
        IPage<MembershipRequestPO> result = membershipRequestMapper.selectPage(new Page<>(page, size),
                targetTenantFilter(targetTenantId).orderByDesc(MembershipRequestPO::getCreatedTime, MembershipRequestPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(result.getTotal(), 0L),
                result.getRecords().stream().map(membershipRequestPOConverter::toDomain).toList());
    }

    @Override
    public PageResult<MembershipRequest> findByApplicant(MemberId applicantId, int page, int size) {
        if (applicantId == null) throw new IllegalArgumentException("applicant ID must not be null");
        IPage<MembershipRequestPO> result = membershipRequestMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MembershipRequestPO>()
                        .eq(MembershipRequestPO::getApplicantActorId, applicantId.getValue())
                        .orderByDesc(MembershipRequestPO::getCreatedTime, MembershipRequestPO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(result.getTotal(), 0L),
                result.getRecords().stream().map(membershipRequestPOConverter::toDomain).toList());
    }

    private LambdaQueryWrapper<MembershipRequestPO> targetTenantFilter(TenantId targetTenantId) {
        if (targetTenantId == null) throw new IllegalArgumentException("target tenant ID must not be null");
        return new LambdaQueryWrapper<MembershipRequestPO>()
                .eq(MembershipRequestPO::getTargetTenantId, targetTenantId.getValue());
    }
}
