package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Private deployment boundary for a report emitted by an independent B24 conformance runner.
 * There is deliberately no properties-backed implementation: configuration cannot attest to
 * its own filesystem, scanner or crash behavior.
 */
public interface SecureLocalAttachmentConformanceReportSource {
    Optional<Report> currentReport();

    record Report(String contractVersion, String adapterBuildDigest, String engineBuildDigest,
                  String bindingDigest, String storageRootDigest, String policyDigest, String detectorProfile,
                  String detectorCatalogRevision, String scannerProfile, String allowlistRevision,
                  String definitionGeneration, AttachmentContentScanner.DefinitionBasis definitionBasis,
                  Instant definitionsValidUntil, Instant expiresAt, Set<String> passedScenarios) {
        public Report {
            passedScenarios = passedScenarios == null ? Set.of() : Set.copyOf(passedScenarios);
        }
    }
}
