package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.EmbeddedPage;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.CustomizationAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.EmbeddedPageStatus;
import io.github.archetom.crm.domain.repository.EmbeddedPageRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.EmbeddedPageId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.EmbeddedPagePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.EmbeddedPageMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.EmbeddedPagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmbeddedPageRepositoryImpl implements EmbeddedPageRepository {

    private final EmbeddedPageMapper mapper;
    private final EmbeddedPagePOConverter converter;

    @Override
    public EmbeddedPage save(TenantId tenantId, EmbeddedPage page) {
        requireTenantMatch(tenantId, page);
        EmbeddedPagePO po = converter.toPO(page);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) {
                    throw new IllegalStateException("Embedded page insert did not affect exactly one row");
                }
            } else if (mapper.update(po, tenantFilter(tenantId)
                    .eq(EmbeddedPagePO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
        } catch (DuplicateKeyException exception) {
            throw new CustomizationAlreadyExistsException();
        }
        page.onPersisted(new EmbeddedPageId(po.getId()), po.getVersion(),
                po.getCreatedTime(), po.getUpdatedTime());
        return page;
    }

    @Override
    public Optional<EmbeddedPage> findById(TenantId tenantId, EmbeddedPageId pageId) {
        if (pageId == null) {
            requireTenant(tenantId);
            return Optional.empty();
        }
        return Optional.ofNullable(converter.toDomain(mapper.selectOne(tenantFilter(tenantId)
                .eq(EmbeddedPagePO::getId, pageId.getValue()))));
    }

    @Override
    public PageResult<EmbeddedPage> findPage(
            TenantId tenantId, BusinessObjectType objectType, ClientPlatform platform,
            EmbeddedPageStatus status, int page, int size) {
        LambdaQueryWrapper<EmbeddedPagePO> query = tenantFilter(tenantId);
        if (objectType != null) {
            query.eq(EmbeddedPagePO::getObjectType, objectType.name());
        }
        if (platform != null) {
            query.eq(EmbeddedPagePO::getPlatform, platform.name());
        }
        if (status != null) {
            query.eq(EmbeddedPagePO::getStatus, status.name());
        }
        IPage<EmbeddedPagePO> result = mapper.selectPage(new Page<>(page, size), query
                .orderByAsc(EmbeddedPagePO::getObjectType, EmbeddedPagePO::getPlatform,
                        EmbeddedPagePO::getSortOrder, EmbeddedPagePO::getId));
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0, result.getTotal()),
                result.getRecords().stream().map(converter::toDomain).toList());
    }

    @Override
    public List<EmbeddedPage> findActive(
            TenantId tenantId, BusinessObjectType objectType, ClientPlatform platform) {
        if (objectType == null || platform == null) {
            requireTenant(tenantId);
            throw new IllegalArgumentException("object type and platform must not be null");
        }
        return mapper.selectList(tenantFilter(tenantId)
                        .eq(EmbeddedPagePO::getObjectType, objectType.name())
                        .eq(EmbeddedPagePO::getPlatform, platform.name())
                        .eq(EmbeddedPagePO::getStatus, EmbeddedPageStatus.ACTIVE.name())
                        .orderByAsc(EmbeddedPagePO::getSortOrder, EmbeddedPagePO::getId))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void delete(TenantId tenantId, EmbeddedPage page) {
        requireTenantMatch(tenantId, page);
        if (page.getId() == null || mapper.delete(tenantFilter(tenantId)
                .eq(EmbeddedPagePO::getId, page.getId().getValue())
                .eq(EmbeddedPagePO::getVersion, page.getVersion())) != 1) {
            throw new AggregateVersionConflictException();
        }
    }

    private LambdaQueryWrapper<EmbeddedPagePO> tenantFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<EmbeddedPagePO>()
                .eq(EmbeddedPagePO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, EmbeddedPage page) {
        requireTenant(tenantId);
        if (page == null || page.getTenantId() == null || !tenantId.sameValueAs(page.getTenantId())) {
            throw new IllegalArgumentException("embedded page tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }
}
