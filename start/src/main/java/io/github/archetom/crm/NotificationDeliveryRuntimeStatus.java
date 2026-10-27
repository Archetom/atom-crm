package io.github.archetom.crm;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local evidence preventing a green delivery health result after a pump failure. */
@Component
public class NotificationDeliveryRuntimeStatus {
    private final AtomicBoolean failedSinceLastSuccess = new AtomicBoolean();
    public void recordSuccess() { failedSinceLastSuccess.set(false); }
    public void recordFailure() { failedSinceLastSuccess.set(true); }
    public boolean failedSinceLastSuccess() { return failedSinceLastSuccess.get(); }
}
