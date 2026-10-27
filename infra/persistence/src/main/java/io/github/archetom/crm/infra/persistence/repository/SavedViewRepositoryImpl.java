package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.CustomizationAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.SavedViewPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.SavedViewMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.SavedViewPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SavedViewRepositoryImpl implements SavedViewRepository {

    private final SavedViewMapper mapper;
    private final SavedViewPOConverter converter;

    @Override
    public SavedView save(TenantId tenantId, SavedView view) {
        requireTenantMatch(tenantId, view);
        SavedViewPO po = converter.toPO(view);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Saved view insert did not affect exactly one row");
                }
            } else if (mapper.update(po, memberFilter(tenantId, view.getMemberId())
                    .eq(SavedViewPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomizationAlreadyExistsException();
        }
        view.onPersisted(new SavedViewId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return view;
    }

    @Override
    public SavedView saveAsDefault(TenantId tenantId, SavedView view) {
        requireTenantMatch(tenantId, view);
        if (view.getId() == null || !view.isDefaultView()) {
            throw new IllegalArgumentException("persisted default saved view is required");
        }
        mapper.update(null, new LambdaUpdateWrapper<SavedViewPO>()
                .eq(SavedViewPO::getTenantId, tenantId.getValue())
                .eq(SavedViewPO::getActorId, view.getMemberId().getValue())
                .eq(SavedViewPO::getObjectType, view.getObjectType().name())
                .eq(SavedViewPO::getDefaultView, true)
                .ne(SavedViewPO::getId, view.getId().getValue())
                .set(SavedViewPO::getDefaultView, false)
                .setSql("version = version + 1")
                .setSql("updated_time = CURRENT_TIMESTAMP(6)"));
        return save(tenantId, view);
    }

    @Override
    public Optional<SavedView> findById(
            TenantId tenantId, MemberId memberId, SavedViewId viewId) {
        if (viewId == null) {
            requireMember(tenantId, memberId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(memberFilter(tenantId, memberId)
                .eq(SavedViewPO::getId, viewId.getValue()))));
    }

    @Override
    public List<SavedView> findAll(
            TenantId tenantId, MemberId memberId, BusinessObjectType objectType,
            boolean includeHidden) {
        LambdaQueryWrapper<SavedViewPO> query = memberFilter(tenantId, memberId)
                .eq(SavedViewPO::getObjectType, requireObject(objectType).name());
        if (!includeHidden) {
            query.eq(SavedViewPO::getHidden, false);
        }
        return mapper.selectList(query.orderByDesc(SavedViewPO::getDefaultView)
                        .orderByAsc(SavedViewPO::getSortOrder, SavedViewPO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean existsReferencingField(
            TenantId tenantId, BusinessObjectType objectType, FieldCode fieldCode) {
        if (tenantId == null || fieldCode == null) {
            throw new IllegalArgumentException("tenant and field code must not be null");
        }
        return mapper.existsReferencingField(
                tenantId.getValue(), requireObject(objectType).name(), fieldCode.getValue());
    }

    @Override
    public void delete(TenantId tenantId, SavedView view) {
        requireTenantMatch(tenantId, view);
        if (view.getId() == null || mapper.delete(memberFilter(tenantId, view.getMemberId())
                .eq(SavedViewPO::getId, view.getId().getValue())
                .eq(SavedViewPO::getVersion, view.getVersion())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    private LambdaQueryWrapper<SavedViewPO> memberFilter(TenantId tenantId, MemberId memberId) {
        requireMember(tenantId, memberId);
        return new LambdaQueryWrapper<SavedViewPO>()
                .eq(SavedViewPO::getTenantId, tenantId.getValue())
                .eq(SavedViewPO::getActorId, memberId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, SavedView view) {
        requireMember(tenantId, view == null ? null : view.getMemberId());
        if (view.getTenantId() == null || !tenantId.sameValueAs(view.getTenantId())) {
            throw new IllegalArgumentException("saved view tenant does not match repository tenant");
        }
    }

    private void requireMember(TenantId tenantId, MemberId memberId) {
        if (tenantId == null || memberId == null) {
            throw new IllegalArgumentException("tenant and member must not be null");
        }
    }

    private BusinessObjectType requireObject(BusinessObjectType objectType) {
        if (objectType == null) {
            throw new IllegalArgumentException("object type must not be null");
        }
        return objectType;
    }
}
