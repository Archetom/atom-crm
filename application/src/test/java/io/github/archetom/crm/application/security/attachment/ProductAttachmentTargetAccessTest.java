package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.domain.entity.Product;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ProductStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.OrganizationTenantRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAttachmentTargetAccessTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final AttachmentTarget TARGET = new AttachmentTarget(AttachmentTargetType.PRODUCT, 9L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(2L, 1L, Set.of());

    @Test
    void intersectsFilesWithProductAuthorityAndActiveRootWithoutOwnerOrCategoryScope() {
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        OrganizationTenantRepository tenantRepository = mock(OrganizationTenantRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        Product product = mock(Product.class);
        when(guard.requireActiveMembership(CALLER, AuthorityCatalog.PRODUCTS_READ)).thenReturn(TENANT);
        when(guard.requireActiveMembership(CALLER, AuthorityCatalog.PRODUCTS_WRITE)).thenReturn(TENANT);
        when(products.findActiveById(eq(TENANT), any(ProductId.class))).thenReturn(Optional.of(product));
        when(tenantRepository.lockForTenantChanges(TENANT)).thenReturn(true);
        when(products.findByIdForAttachmentMutation(eq(TENANT), any(ProductId.class)))
                .thenReturn(Optional.of(product));
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);

        ProductAttachmentTargetAccess access = new ProductAttachmentTargetAccess(guard, tenantRepository, products);

        assertSame(TENANT, access.authorizeRead(CALLER, TARGET));
        assertTrue(access.lockForAttachmentMutation(CALLER, TARGET,
                AttachmentTargetOperation.CREATE_INTENT).attachmentsPermitted());
        verify(guard).requireActiveMembership(CALLER, AuthorityCatalog.PRODUCTS_READ);
        verify(guard).requireActiveMembership(CALLER, AuthorityCatalog.PRODUCTS_WRITE);
        verify(products).findByIdForAttachmentMutation(TENANT, new ProductId(9L));
    }
}
