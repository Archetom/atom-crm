package io.github.archetom.crm.infra.external.attachment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSecureLocalAttachmentConformanceReportSourceTest {
    @TempDir Path root;

    @Test
    void readsOnlyOwnerOnlyMachineReportAndNeverSynthesizesMissingScenarios() throws Exception {
        Path report = root.resolve("b24.json");
        Files.writeString(report, "{\"contractVersion\":\"B24A_CONFORMANCE_V1\",\"adapterBuildDigest\":\"a\","
                + "\"engineBuildDigest\":\"b\",\"bindingDigest\":\"c\",\"storageRootDigest\":\"d\","
                + "\"policyDigest\":\"e\",\"detectorProfile\":\"BOUNDED_MAGIC_V1\","
                + "\"detectorCatalogRevision\":\"f\",\"scannerProfile\":\"STREAMING_ENGINE_V1\","
                + "\"allowlistRevision\":\"g\",\"definitionGeneration\":\"h\","
                + "\"definitionBasis\":\"PINNED_DEFINITIONS\",\"definitionsValidUntil\":\"2030-01-01T00:00:00Z\","
                + "\"expiresAt\":\"2030-01-01T00:00:00Z\",\"observedChecks\":[\"S-10-LOCAL-FREE-FLOOR\"],"
                + "\"passedScenarios\":[\"S-02\",\"I-01\"]}");
        Files.setPosixFilePermissions(report, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        var loaded = new FileSecureLocalAttachmentConformanceReportSource(report).currentReport();

        assertTrue(loaded.isPresent());
        assertEquals(Set.of("S-02", "I-01"), loaded.orElseThrow().passedScenarios());
        Files.setPosixFilePermissions(report, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ));
        assertFalse(new FileSecureLocalAttachmentConformanceReportSource(report).currentReport().isPresent());
    }
}
