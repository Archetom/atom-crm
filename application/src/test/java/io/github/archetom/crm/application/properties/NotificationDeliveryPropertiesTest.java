package io.github.archetom.crm.application.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDeliveryPropertiesTest {

    @Test
    void deliveryRetentionCannotOutliveTheNotificationRetentionCeiling() {
        NotificationDeliveryProperties properties = enabledProperties();
        properties.setTerminalRetention(Duration.ofDays(30));

        assertFalse(properties.dispatchEnabled(Duration.ofDays(1)));
        assertEquals(Duration.ofDays(1), properties.safeTerminalRetention(Duration.ofDays(1)));

        properties.setTerminalRetention(Duration.ofDays(1));
        assertTrue(properties.dispatchEnabled(Duration.ofDays(1)));
    }

    private static NotificationDeliveryProperties enabledProperties() {
        NotificationDeliveryProperties value = new NotificationDeliveryProperties();
        value.setEnabled(true);
        value.setAdapterContract("TEST");
        value.setAdapterBuild("BUILD");
        value.setBindingRevision("BINDING");
        value.setActiveEndpointBindingKeyVersion("V1");
        value.setEndpointBindingKeys(Map.of("V1", "test-only-endpoint-binding-key"));
        return value;
    }
}
