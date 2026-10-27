package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentTargetAccessRegistryTest {

    @Test
    void requiresAnExplicitAdapterForEveryClosedTargetType() {
        List<AttachmentTargetAccess> adapters = Arrays.stream(AttachmentTargetType.values())
                .<AttachmentTargetAccess>map(StubAccess::new)
                .toList();

        AttachmentTargetAccessRegistry registry = new AttachmentTargetAccessRegistry(adapters);

        for (AttachmentTargetAccess adapter : adapters) {
            assertSame(adapter, registry.require(adapter.targetType()));
        }
    }

    @Test
    void failsClosedWhenOneOfTheClosedTargetTypesIsNotRegistered() {
        List<AttachmentTargetAccess> adapters = Arrays.stream(AttachmentTargetType.values())
                .filter(type -> type != AttachmentTargetType.RECEIVABLE_PLAN)
                .<AttachmentTargetAccess>map(StubAccess::new)
                .toList();

        assertThrows(IllegalStateException.class, () -> new AttachmentTargetAccessRegistry(adapters));
    }

    @Test
    void rejectsDuplicateTargetRegistrations() {
        AttachmentTargetAccess first = new StubAccess(AttachmentTargetType.CUSTOMER);
        AttachmentTargetAccess second = new StubAccess(AttachmentTargetType.CUSTOMER);

        assertThrows(IllegalStateException.class,
                () -> new AttachmentTargetAccessRegistry(List.of(first, second)));
    }

    private record StubAccess(AttachmentTargetType targetType) implements AttachmentTargetAccess {
        @Override public TenantId authorizeRead(AuthenticatedCaller caller, AttachmentTarget target) {
            throw new UnsupportedOperationException();
        }

        @Override public TenantId authorizeCollaborate(AuthenticatedCaller caller, AttachmentTarget target) {
            throw new UnsupportedOperationException();
        }

        @Override public AttachmentTargetLifecycle lockForAttachmentMutation(
                AuthenticatedCaller caller, AttachmentTarget target, AttachmentTargetOperation operation) {
            throw new UnsupportedOperationException();
        }

        @Override public AttachmentTargetLifecycle lockForWorker(TenantId tenantId, AttachmentTarget target) {
            throw new UnsupportedOperationException();
        }
    }
}
