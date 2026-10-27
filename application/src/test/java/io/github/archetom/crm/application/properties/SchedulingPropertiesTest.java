package io.github.archetom.crm.application.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulingPropertiesTest {
    @Test
    void acceptsTheDocumentedAbsoluteDeploymentBounds() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setEnabled(true);
        properties.setPulseDelay(Duration.ofMinutes(5));
        properties.setAutomationLaneConcurrency(SchedulingProperties.MAX_LANE_CONCURRENCY);
        properties.setAttachmentLaneConcurrency(SchedulingProperties.MAX_LANE_CONCURRENCY);
        properties.setCadenceRevision("B26A_V999999999");
        properties.setPoolCadence(Duration.ofDays(7));
        properties.setLeaseDuration(Duration.ofMinutes(30));
        properties.setRunDeadline(Duration.ofDays(7));
        properties.setRetryBackoff(Duration.ofHours(6));
        properties.setTerminalRetention(Duration.ofDays(90));

        assertTrue(properties.policy().valid());
    }

    @Test
    void rejectsOverflowProneAndSilentlyTruncatedValues() {
        assertInvalid(properties -> properties.setPulseDelay(Duration.ofMinutes(6)));
        assertInvalid(properties -> properties.setAutomationLaneConcurrency(17));
        assertInvalid(properties -> properties.setAttachmentLaneConcurrency(17));
        assertInvalid(properties -> properties.setCadenceRevision("B26A_V0"));
        assertInvalid(properties -> properties.setPoolCadence(Duration.ofDays(8)));
        assertInvalid(properties -> properties.setLeaseDuration(Duration.ofMinutes(31)));
        assertInvalid(properties -> properties.setRunDeadline(Duration.ofDays(8)));
        assertInvalid(properties -> properties.setRetryBackoff(Duration.ofHours(7)));
        assertInvalid(properties -> properties.setTerminalRetention(Duration.ofDays(91)));
        assertInvalid(properties -> {
            properties.setLeaseDuration(Duration.ofMinutes(30));
            properties.setRunDeadline(Duration.ofMinutes(31));
            properties.setRetryBackoff(Duration.ofMinutes(2));
        });
    }

    @Test
    void isolatesInvalidRecurrenceSettingsFromThePoolAutomationPolicy() {
        SchedulingProperties properties = new SchedulingProperties();
        properties.setRecurrenceCadence(Duration.ofSeconds(30));
        properties.setRecurrenceRecordLimit(0);

        assertTrue(properties.policy().valid());
        assertFalse(properties.recurrenceValid());
    }

    private static void assertInvalid(java.util.function.Consumer<SchedulingProperties> customize) {
        SchedulingProperties properties = new SchedulingProperties();
        customize.accept(properties);
        assertFalse(properties.policy().valid());
    }
}
