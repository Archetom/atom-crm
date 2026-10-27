package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.ReportDefinition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.CustomizationAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ReportDefinitionStatus;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ReportDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReportDefinitionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ReportDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReportDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ReportDefinitionPO;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReportDefinitionRepositoryImpl implements ReportDefinitionRepository {
    private final ReportDefinitionMapper mapper;
    private final ReportDefinitionPOConverter converter;

    @Override
    public ReportDefinition save(TenantId tenantId, ReportDefinition definition) {
        requireMatch(tenantId, definition);
        ReportDefinitionPO po = converter.toPO(definition);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Report definition insert failed");
                }
            } else if (mapper.update(po, memberFilter(tenantId, definition.getCreatorMemberId())
                    .eq(ReportDefinitionPO::getId, po.getId())
                    .eq(ReportDefinitionPO::getStatus, ReportDefinitionStatus.ACTIVE.name())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomizationAlreadyExistsException();
        }
        definition.onPersisted(new ReportDefinitionId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return definition;
    }

    @Override
    public Optional<ReportDefinition> findActiveById(
            TenantId tenantId, MemberId memberId, ReportDefinitionId definitionId) {
        if (definitionId == null) {
            requireMember(tenantId, memberId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(memberFilter(tenantId, memberId)
                .eq(ReportDefinitionPO::getId, definitionId.getValue())
                .eq(ReportDefinitionPO::getStatus, ReportDefinitionStatus.ACTIVE.name()))));
    }

    @Override
    public PageResult<ReportDefinition> findActivePage(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            String normalizedKeyword, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("report definition page is invalid");
        }
        LambdaQueryWrapper<ReportDefinitionPO> filter = memberFilter(tenantId, memberId)
                .eq(ReportDefinitionPO::getStatus, ReportDefinitionStatus.ACTIVE.name());
        if (objectType != null) filter.eq(ReportDefinitionPO::getObjectType, objectType.name());
        if (normalizedKeyword != null) {
            filter.apply("normalized_name LIKE {0} ESCAPE '!'",
                    "%" + SqlLikePattern.literal(normalizedKeyword) + "%");
        }
        IPage<ReportDefinitionPO> result = mapper.selectPage(new Page<>(page, size),
                filter.orderByDesc(ReportDefinitionPO::getUpdatedTime)
                        .orderByDesc(ReportDefinitionPO::getId));
        List<ReportDefinition> values = result.getRecords().stream()
                .map(converter::toDomain).toList();
        return new PageResult<>(result.getCurrent(), result.getSize(),
                Math.max(0, result.getTotal()), values);
    }

    @Override
    public long countActive(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType) {
        if (objectType == null) throw new IllegalArgumentException("report object is required");
        return mapper.selectCount(memberFilter(tenantId, memberId)
                .eq(ReportDefinitionPO::getObjectType, objectType.name())
                .eq(ReportDefinitionPO::getStatus, ReportDefinitionStatus.ACTIVE.name()));
    }

    @Override
    public boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode) {
        if (tenantId == null || objectType == null || fieldCode == null) {
            throw new IllegalArgumentException("report field dependency is invalid");
        }
        return mapper.existsReferencingField(tenantId.getValue(), objectType.name(),
                fieldCode.getValue());
    }

    private LambdaQueryWrapper<ReportDefinitionPO> memberFilter(
            TenantId tenantId, MemberId memberId) {
        requireMember(tenantId, memberId);
        return new LambdaQueryWrapper<ReportDefinitionPO>()
                .eq(ReportDefinitionPO::getTenantId, tenantId.getValue())
                .eq(ReportDefinitionPO::getCreatorMemberId, memberId.getValue());
    }

    private void requireMatch(TenantId tenantId, ReportDefinition definition) {
        if (tenantId == null || definition == null || definition.getTenantId() == null
                || !tenantId.sameValueAs(definition.getTenantId())) {
            throw new IllegalArgumentException("report definition tenant does not match");
        }
    }

    private void requireMember(TenantId tenantId, MemberId memberId) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("report tenant and member are required");
        }
    }
}
