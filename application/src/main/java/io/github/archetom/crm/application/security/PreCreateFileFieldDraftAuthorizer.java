package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rechecks the business authority for a targetless FILE draft before it reserves or consumes
 * upload capacity. A targetless draft has no record ID to route through AttachmentTargetAccess.
 */
@Component
@RequiredArgsConstructor
public class PreCreateFileFieldDraftAuthorizer {
    private final OrganizationAccessGuard accessGuard;
    private final ReturnVisitAccessResolver returnVisitAccessResolver;

    public void requireCurrentWrite(AuthenticatedCaller caller, TenantId tenantId, MemberId memberId,
                                    BusinessObjectType objectType) {
        if (caller == null || tenantId == null || memberId == null || objectType == null) {
            throw denied();
        }
        if (objectType == BusinessObjectType.RETURN_VISIT) {
            var access = returnVisitAccessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_WRITE,
                    ReturnVisitAccessMode.WRITE);
            if (!tenantId.sameValueAs(access.tenantId()) || !memberId.sameValueAs(access.memberId())) {
                throw denied();
            }
            return;
        }
        TenantId resolved = accessGuard.requireActiveMembership(caller, writeAuthority(objectType));
        if (!tenantId.sameValueAs(resolved)) {
            throw denied();
        }
    }

    private static String writeAuthority(BusinessObjectType objectType) {
        return switch (objectType) {
            case CUSTOMER -> AuthorityCatalog.CUSTOMERS_WRITE;
            case CONTACT -> AuthorityCatalog.CONTACTS_WRITE;
            case LEAD -> AuthorityCatalog.LEADS_WRITE;
            case OPPORTUNITY -> AuthorityCatalog.OPPORTUNITIES_WRITE;
            case CONTRACT -> AuthorityCatalog.CONTRACTS_WRITE;
            case INVOICE -> AuthorityCatalog.INVOICES_WRITE;
            case RECEIVABLE -> AuthorityCatalog.RECEIVABLES_WRITE;
            case RECEIVABLE_PLAN -> AuthorityCatalog.RECEIVABLE_PLANS_WRITE;
            case PRODUCT -> AuthorityCatalog.PRODUCTS_WRITE;
            case OA_REQUEST -> throw new NonRetryableApplicationException(
                    ApplicationErrorCode.PARAMETER_INVALID, "file field draft object type is unsupported");
            case RETURN_VISIT -> throw new IllegalStateException("return visit requires scoped authorization");
        };
    }

    private static NonRetryableApplicationException denied() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED,
                "file field draft business access is unavailable");
    }
}
