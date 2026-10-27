package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrmAttachmentTargetAuthorizationTest {
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(2L, 1L, Set.of());
    private static final TenantId TENANT = new TenantId(1L);

    @Test
    void allEightCrmTargetsUseTheirTypedAdapterForReadAndCollaborateAuthorization() {
        CrmAttachmentTargetAccessSupport support = mock(CrmAttachmentTargetAccessSupport.class);
        when(support.authorizeRead(any(), any())).thenReturn(TENANT);
        when(support.authorizeCollaborate(any(), any())).thenReturn(TENANT);
        List<AttachmentTargetAccess> accesses = List.of(
                new CustomerAttachmentTargetAccess(support), new ContactAttachmentTargetAccess(support),
                new LeadAttachmentTargetAccess(support), new OpportunityAttachmentTargetAccess(support),
                new ContractAttachmentTargetAccess(support), new InvoiceAttachmentTargetAccess(support),
                new ReceivableAttachmentTargetAccess(support), new ReceivablePlanAttachmentTargetAccess(support));

        for (AttachmentTargetAccess access : accesses) {
            AttachmentTarget target = new AttachmentTarget(access.targetType(), 9L);
            assertSame(TENANT, access.authorizeRead(CALLER, target));
            assertSame(TENANT, access.authorizeCollaborate(CALLER, target));
            verify(support).authorizeRead(CALLER, target);
            verify(support).authorizeCollaborate(CALLER, target);
        }
    }
}
