package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.EmbeddedPage;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ClientPlatform;
import io.github.archetom.crm.domain.model.EmbeddedPageStatus;
import io.github.archetom.crm.domain.valueobject.EmbeddedPageId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Optional;

public interface EmbeddedPageRepository {

    EmbeddedPage save(TenantId tenantId, EmbeddedPage page);

    Optional<EmbeddedPage> findById(TenantId tenantId, EmbeddedPageId pageId);

    PageResult<EmbeddedPage> findPage(
            TenantId tenantId, BusinessObjectType objectType, ClientPlatform platform,
            EmbeddedPageStatus status, int page, int size);

    List<EmbeddedPage> findActive(
            TenantId tenantId, BusinessObjectType objectType, ClientPlatform platform);

    void delete(TenantId tenantId, EmbeddedPage page);
}
