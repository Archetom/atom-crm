package io.github.archetom.crm.application.port.out.attachment;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentRuntimePolicyTest {
    @Test
    void enabledPolicyRequiresAndExposesDurableUsageLimits() {
        AttachmentRuntimePolicy policy = policy(64, 48);

        assertEquals(64, policy.usageLimits().globalReserveBytes());
        assertEquals(48, policy.usageLimits().deletionControlReserve());
        assertEquals(2, policy.usageLimits().maxActiveDownloads());
    }

    @Test
    void deletionReserveCannotConsumeTheWholeTenantBudget() {
        assertThrows(IllegalArgumentException.class, () -> policy(64, 64));
    }

    private static AttachmentRuntimePolicy policy(long reserve, long deletionReserve) {
        Duration second = Duration.ofSeconds(1);
        return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2,
                reserve, deletionReserve, second, second, second, second, second,
                second, second, second, Duration.ofSeconds(2), second, 2, second, second, 1);
    }
}
