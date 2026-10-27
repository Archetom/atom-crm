package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.ProductCatalogLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.ProductImageDraftService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ProductCategoryRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductCommandServiceImplTest {
    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final CustomizationObjectLock objectLock = mock(CustomizationObjectLock.class);
    private final ProductCatalogLock catalogLock = mock(ProductCatalogLock.class);
    private final CommandServiceTemplate commands = mock(CommandServiceTemplate.class);
    private ProductCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductCommandServiceImpl(products, categories, mock(ProductApplicationSupport.class), accessGuard,
                objectLock, catalogLock, mock(AttachmentParentLifecycle.class), mock(FileFieldBindingService.class),
                mock(ProductImageDraftService.class), commands);
        when(commands.execute(any(), any())).thenAnswer(invocation -> {
            ServiceOperation<?> operation = invocation.getArgument(1);
            operation.validate();
            return new Result<>();
        });
    }

    @Test
    void createWithFileDraftRequiresCurrentFilesWrite() {
        TenantId tenantId = new TenantId(1L);
        ProductCreateRequest request = new ProductCreateRequest().setFileFieldDraftId(1L);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE)).thenReturn(tenantId);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenThrow(denied());

        assertThrows(NonRetryableApplicationException.class, () -> service.create(caller, request));

        verify(accessGuard).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(products, categories, objectLock, catalogLock);
    }

    @Test
    void createWithProductImageDraftRequiresCurrentFilesWrite() {
        TenantId tenantId = new TenantId(1L);
        ProductCreateRequest request = new ProductCreateRequest().setProductImageDraftId(1L)
                .setProductImages(java.util.Map.of("PRIMARY", java.util.List.of(2L)));
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE)).thenReturn(tenantId);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenThrow(denied());

        assertThrows(NonRetryableApplicationException.class, () -> service.create(caller, request));

        verify(accessGuard).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(products, categories, objectLock, catalogLock);
    }

    @Test
    void updateWithFileValuesRequiresCurrentFilesWrite() {
        TenantId tenantId = new TenantId(1L);
        ProductUpdateRequest request = new ProductUpdateRequest().setFileFields(java.util.Map.of("cf_file", java.util.List.of()));
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE)).thenReturn(tenantId);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE)).thenThrow(denied());

        assertThrows(NonRetryableApplicationException.class, () -> service.update(caller, 1L, request));

        verify(accessGuard).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(products, categories, objectLock, catalogLock);
    }

    @Test
    void ordinaryUpdateDoesNotRequireFilesWrite() {
        when(caller.actorId()).thenReturn(1L);
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE)).thenReturn(new TenantId(1L));

        service.update(caller, 1L, new ProductUpdateRequest());

        verify(accessGuard, never()).requireActiveMembership(caller, AuthorityCatalog.FILES_WRITE);
        verifyNoInteractions(products, categories, objectLock, catalogLock);
    }

    private static NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED);
    }
}
