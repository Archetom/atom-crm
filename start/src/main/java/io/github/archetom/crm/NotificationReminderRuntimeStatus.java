package io.github.archetom.crm;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local scheduler outcome used only to prevent a green health signal after a lane failure. */
@Component
public class NotificationReminderRuntimeStatus {
    private final AtomicBoolean failedSinceLastSuccess = new AtomicBoolean();

    public void recordSuccess() {
        failedSinceLastSuccess.set(false);
    }

    public void recordFailure() {
        failedSinceLastSuccess.set(true);
    }

    public boolean failedSinceLastSuccess() {
        return failedSinceLastSuccess.get();
    }
}
