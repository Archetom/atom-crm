package io.github.archetom.crm.application.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkRecurrencePropertiesTest {
    @Test
    void autoFingerprintUsesRuntimeEvidenceAndADeclaredMismatchFailsClosed() {
        WorkRecurrenceProperties properties = new WorkRecurrenceProperties();
        assertTrue(properties.policy().valid());

        properties.setTzdbFingerprint("TZDB_not_this_runtime");
        assertFalse(properties.policy().valid());
    }

    @Test
    void invalidReconciliationDelayFailsClosedWithASafeSchedulerFallback() {
        WorkRecurrenceProperties properties = new WorkRecurrenceProperties();
        properties.setReconciliationDelay("not-a-duration");

        assertFalse(properties.policy().valid());
        assertEquals(Duration.ofSeconds(5), properties.safeReconciliationDelay());
    }
}
