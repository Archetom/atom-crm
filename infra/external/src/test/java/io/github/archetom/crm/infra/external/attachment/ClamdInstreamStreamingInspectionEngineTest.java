package io.github.archetom.crm.infra.external.attachment;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClamdInstreamStreamingInspectionEngineTest {

    @Test
    void refusesTcpTransportInProductionComposition() {
        SecureLocalAttachmentProperties.Clamd clamd = new SecureLocalAttachmentProperties.Clamd();
        clamd.setLoopbackTcpPort(3310);
        clamd.setChunkBytes(64 * 1024);
        clamd.setConnectTimeout(Duration.ofSeconds(1));
        clamd.setResponseTimeout(Duration.ofSeconds(1));
        clamd.setDefinitionEvidenceTtl(Duration.ofMinutes(1));

        assertThrows(IllegalArgumentException.class,
                () -> new ClamdInstreamStreamingInspectionEngine(clamd));
    }

    @Test
    void refusesIncompleteDefinitionEvidenceOnTheAcceptedUnixSocketTransport() {
        SecureLocalAttachmentProperties.Clamd clamd = new SecureLocalAttachmentProperties.Clamd();
        clamd.setUnixSocket(Path.of("/run/clamav/clamd.ctl"));
        clamd.setChunkBytes(64 * 1024);
        clamd.setConnectTimeout(Duration.ofSeconds(1));
        clamd.setResponseTimeout(Duration.ofSeconds(1));

        assertThrows(IllegalArgumentException.class,
                () -> new ClamdInstreamStreamingInspectionEngine(clamd));

        clamd.setDefinitionEvidenceTtl(Duration.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> new ClamdInstreamStreamingInspectionEngine(clamd));
    }

    @Test
    void acceptsOnlyTheUnixSocketTransportForProductionComposition() {
        SecureLocalAttachmentProperties.Clamd clamd = new SecureLocalAttachmentProperties.Clamd();
        clamd.setUnixSocket(Path.of("/run/clamav/clamd.ctl"));
        clamd.setChunkBytes(64 * 1024);
        clamd.setConnectTimeout(Duration.ofSeconds(1));
        clamd.setResponseTimeout(Duration.ofSeconds(1));
        clamd.setDefinitionEvidenceTtl(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> new ClamdInstreamStreamingInspectionEngine(clamd));
    }
}
