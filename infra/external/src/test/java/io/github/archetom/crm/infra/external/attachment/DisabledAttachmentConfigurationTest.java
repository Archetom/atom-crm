package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DisabledAttachmentConfigurationTest {

    @Test
    void defaultCompositionCannotAccidentallyAdvertiseFileReadiness() {
        DisabledAttachmentConfiguration configuration = new DisabledAttachmentConfiguration();
        assertFalse(configuration.attachmentRuntimePolicy().enabled());
        assertEquals(AttachmentReadiness.NOT_READY,
                configuration.attachmentStorageReadinessResolver()
                        .resolve(new TenantId(1L), AttachmentOperation.STORE_SINGLE_STREAM).readiness());
        assertEquals(AttachmentReadiness.NOT_READY,
                configuration.attachmentInspectionReadinessResolver()
                        .resolve(new TenantId(1L)).readiness());
    }
}
