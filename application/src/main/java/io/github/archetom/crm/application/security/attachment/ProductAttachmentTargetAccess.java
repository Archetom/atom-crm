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
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Product is tenant-wide: its attachment access never derives scope from owner or category. */
@Component
@RequiredArgsConstructor
public class ProductAttachmentTargetAccess implements AttachmentTargetAccess {
    private final OrganizationAccessGuard accessGuard;
    private final OrganizationTenantRepository tenantRepository;
    private final ProductRepository productRepository;

    @Override public AttachmentTargetType targetType() { return AttachmentTargetType.PRODUCT; }

    @Override
    public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.PRODUCTS_READ);
    }

    @Override
    public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
        return visible(caller, target, AuthorityCatalog.PRODUCTS_WRITE);
    }

    @Override
    public AttachmentTargetLifecycle lockForAttachmentMutation(
            AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
        TenantId tenantId = switch (operation) {
            case CREATE_INTENT, UPLOAD, STATUS, WITHDRAW -> authorizeCollaborate(caller, target);
            case DOWNLOAD -> authorizeRead(caller, target);
            case PUBLISH, PARENT_LIFECYCLE -> throw new IllegalArgumentException(
                    "worker attachment target lock requires its dedicated internal coordinator");
        };
        return lock(tenantId, target);
    }

    @Override
    public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
        requireTarget(target);
        return lock(tenantId, target);
    }

    private TenantId visible(AuthenticatedCaller caller, AttachmentTarget target, String authority) {
        requireTarget(target);
        TenantId tenantId = accessGuard.requireActiveMembership(caller, authority);
        productRepository.findActiveById(tenantId, new ProductId(target.id())).orElseThrow(
                ProductAttachmentTargetAccess::notFound);
        return tenantId;
    }

    private AttachmentTargetLifecycle lock(TenantId tenantId, AttachmentTarget target) {
        if (tenantId == null || !tenantRepository.lockForTenantChanges(tenantId)) {
            throw new IllegalStateException("attachment target tenant is unavailable");
        }
        Product product = productRepository.findByIdForAttachmentMutation(tenantId,
                new ProductId(target.id())).orElseThrow(ProductAttachmentTargetAccess::notFound);
        return new AttachmentTargetLifecycle(tenantId, product.getStatus() == ProductStatus.ACTIVE);
    }

    private static void requireTarget(AttachmentTarget target) {
        if (target == null || target.type() != AttachmentTargetType.PRODUCT) {
            throw new IllegalArgumentException("attachment target is unsupported");
        }
    }

    private static NonRetryableApplicationException notFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
}
