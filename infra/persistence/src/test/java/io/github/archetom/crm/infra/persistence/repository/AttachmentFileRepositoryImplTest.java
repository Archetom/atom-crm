package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.AttachmentFilePOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentFileMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.AttachmentRelationMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttachmentFileRepositoryImplTest {

    @Test
    void availablePageUsesOneCountAndOneTypedJoinRegardlessOfPageSize() {
        AttachmentFileMapper fileMapper = mock(AttachmentFileMapper.class);
        AttachmentRelationMapper relationMapper = mock(AttachmentRelationMapper.class);
        AttachmentFilePOConverter converter = mock(AttachmentFilePOConverter.class);
        AttachmentFileRepositoryImpl repository = new AttachmentFileRepositoryImpl(fileMapper, relationMapper, converter);
        TenantId tenantId = new TenantId(1L);
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.CUSTOMER, 2L);
        when(fileMapper.countAvailableByTarget(1L, "CUSTOMER", 2L, null, null)).thenReturn(100L);
        when(fileMapper.selectAvailablePageByTarget(1L, "CUSTOMER", 2L, null, null, 0L, 1L))
                .thenReturn(List.of());

        PageResult<?> page = repository.findAvailablePage(tenantId, target, 1L, 1L);

        assertEquals(100L, page.total());
        assertEquals(List.of(), page.items());
        verify(fileMapper).countAvailableByTarget(1L, "CUSTOMER", 2L, null, null);
        verify(fileMapper).selectAvailablePageByTarget(1L, "CUSTOMER", 2L, null, null, 0L, 1L);
        verifyNoInteractions(relationMapper, converter);
    }

    @Test
    void productImagePageUsesOnlyTheClosedProductImageGroup() {
        AttachmentFileMapper fileMapper = mock(AttachmentFileMapper.class);
        AttachmentRelationMapper relationMapper = mock(AttachmentRelationMapper.class);
        AttachmentFilePOConverter converter = mock(AttachmentFilePOConverter.class);
        AttachmentFileRepositoryImpl repository = new AttachmentFileRepositoryImpl(fileMapper, relationMapper, converter);
        TenantId tenantId = new TenantId(1L);
        AttachmentTarget target = new AttachmentTarget(AttachmentTargetType.PRODUCT, 2L);
        when(fileMapper.countAvailableByTarget(1L, "PRODUCT", 2L, null, "PRIMARY")).thenReturn(0L);

        PageResult<?> page = repository.findAvailablePage(tenantId, target, 1L, 9L,
                null, ProductImageType.PRIMARY);

        assertEquals(0L, page.total());
        verify(fileMapper).countAvailableByTarget(1L, "PRODUCT", 2L, null, "PRIMARY");
        verifyNoInteractions(relationMapper, converter);
    }
}
