package io.github.archetom.crm.application.properties;

import io.github.archetom.crm.application.automation.WorkRecurrencePolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.zone.ZoneRulesProvider;
import java.time.Duration;

/** Deployment declaration for the B27a recurrence interpretation policy. */
@Data
@ConfigurationProperties(prefix = "atom.work.recurrence")
public class WorkRecurrenceProperties {
    private static final Duration DEFAULT_RECONCILIATION_DELAY = Duration.ofSeconds(5);
    private static final Duration MIN_RECONCILIATION_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RECONCILIATION_DELAY = Duration.ofMinutes(5);

    private boolean enabled = true;
    private String policyRevision = "B27A_V1";
    /** AUTO persists the current JDK tzdb evidence and verifies it on every replica. */
    private String tzdbFingerprint = "AUTO";
    /** Kept raw so an invalid deployment value degrades recurrence instead of breaking startup. */
    private String reconciliationDelay = "PT5S";

    public WorkRecurrencePolicy policy() {
        String runtime = runtimeTzdbFingerprint();
        boolean valid = policyRevision != null && policyRevision.matches("B27A_V[1-9][0-9]{0,8}")
                && tzdbFingerprint != null
                && !"TZDB_UNKNOWN".equals(runtime)
                && ("AUTO".equals(tzdbFingerprint) || tzdbFingerprint.equals(runtime))
                && reconciliationDelayValid();
        return new WorkRecurrencePolicy(enabled, valid,
                valid ? policyRevision : "B27A_V1", runtime);
    }

    public Duration safeReconciliationDelay() {
        try {
            Duration value = Duration.parse(reconciliationDelay);
            return value.compareTo(MIN_RECONCILIATION_DELAY) >= 0
                    && value.compareTo(MAX_RECONCILIATION_DELAY) <= 0 ? value : DEFAULT_RECONCILIATION_DELAY;
        } catch (RuntimeException exception) {
            return DEFAULT_RECONCILIATION_DELAY;
        }
    }

    public boolean reconciliationDelayValid() {
        try {
            Duration value = Duration.parse(reconciliationDelay);
            return value.compareTo(MIN_RECONCILIATION_DELAY) >= 0
                    && value.compareTo(MAX_RECONCILIATION_DELAY) <= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String runtimeTzdbFingerprint() {
        try {
            var versions = ZoneRulesProvider.getVersions("UTC");
            if (versions.isEmpty()) return "TZDB_UNKNOWN";
            return "TZDB_" + versions.lastKey().replaceAll("[^A-Za-z0-9._:-]", "_");
        } catch (RuntimeException exception) {
            return "TZDB_UNKNOWN";
        }
    }
}
