package io.github.archetom.crm;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.properties.PublicMarketingFileProperties;
import io.github.archetom.crm.application.service.PublicMarketingFileRuntimeStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicMarketingFileHealthIndicatorTest {

    @Test
    void reportsOnlyFixedAnonymousLaneStates() {
        AttachmentRuntimePolicy runtime = mock(AttachmentRuntimePolicy.class);
        PublicMarketingFileProperties properties = new PublicMarketingFileProperties();
        PublicMarketingFileRuntimeStatus status = new PublicMarketingFileRuntimeStatus();
        PublicMarketingFileHealthIndicator indicator = new PublicMarketingFileHealthIndicator(
                properties, runtime, status);

        assertEquals("DISABLED", indicator.health().getDetails().get("state"));

        properties.setEnabled(true);
        properties.setMaxRequestsPerFormPerMinute(0);
        assertEquals("CONFIG_INVALID", indicator.health().getDetails().get("state"));

        properties.setMaxRequestsPerFormPerMinute(120);
        when(runtime.enabled()).thenReturn(false);
        assertEquals("FILES_NOT_READY", indicator.health().getDetails().get("state"));

        when(runtime.enabled()).thenReturn(true);
        assertEquals("FILES_NOT_READY", indicator.health().getDetails().get("state"));

        status.recordRuntimeFailure();
        assertEquals("RUNTIME_FAILURE", indicator.health().getDetails().get("state"));

        status.recordReadinessReady();
        assertEquals("RUNTIME_FAILURE", indicator.health().getDetails().get("state"));

        status.recordAdapterSuccess();
        assertEquals("READY", indicator.health().getDetails().get("state"));
    }
}
