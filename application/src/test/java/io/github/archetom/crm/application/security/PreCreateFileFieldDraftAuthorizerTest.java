package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreCreateFileFieldDraftAuthorizerTest {
    private static final TenantId TENANT = new TenantId(1L);
    private static final MemberId MEMBER = new MemberId(2L);
    private final AuthenticatedCaller caller = mock(AuthenticatedCaller.class);
    private final OrganizationAccessGuard accessGuard = mock(OrganizationAccessGuard.class);
    private final ReturnVisitAccessResolver returnVisitAccess = mock(ReturnVisitAccessResolver.class);
    private final PreCreateFileFieldDraftAuthorizer authorizer =
            new PreCreateFileFieldDraftAuthorizer(accessGuard, returnVisitAccess);

    @Test
    void productDraftRequiresCurrentProductsWrite() {
        when(accessGuard.requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE)).thenReturn(TENANT);

        authorizer.requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.PRODUCT);

        verify(accessGuard).requireActiveMembership(caller, AuthorityCatalog.PRODUCTS_WRITE);
    }

    @Test
    void returnVisitDraftRequiresCurrentScopedWrite() {
        when(returnVisitAccess.access(caller, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE))
                .thenReturn(new ReturnVisitAccessResolver.ReturnVisitAccess(TENANT, MEMBER, null));

        authorizer.requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.RETURN_VISIT);

        verify(returnVisitAccess).access(caller, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE);
    }

    @Test
    void revokedReturnVisitScopeRejectsTheExistingDraft() {
        when(returnVisitAccess.access(caller, AuthorityCatalog.RETURN_VISITS_WRITE, ReturnVisitAccessMode.WRITE))
                .thenThrow(new NonRetryableApplicationException(ApplicationErrorCode.ACCESS_DENIED));

        assertThrows(NonRetryableApplicationException.class,
                () -> authorizer.requireCurrentWrite(caller, TENANT, MEMBER, BusinessObjectType.RETURN_VISIT));
    }
}
