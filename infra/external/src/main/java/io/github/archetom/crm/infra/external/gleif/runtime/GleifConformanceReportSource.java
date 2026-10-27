package io.github.archetom.crm.infra.external.gleif.runtime;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** Separate deployment evidence boundary; runtime never generates its own source attestation. */
interface GleifConformanceReportSource {
    Optional<Report> currentReport();

    record Report(String contractVersion, String adapterBuildDigest, String policyDigest,
                  String raCatalogDigest, String quotaMode, Instant expiresAt,
                  Set<String> passedScenarios) {
        public Report { passedScenarios = passedScenarios == null ? Set.of() : Set.copyOf(passedScenarios); }
    }
}
