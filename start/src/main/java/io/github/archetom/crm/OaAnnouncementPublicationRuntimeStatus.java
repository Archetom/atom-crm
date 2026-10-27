package io.github.archetom.crm;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local evidence preventing a stale green OA-publication lane health result. */
@Component
public class OaAnnouncementPublicationRuntimeStatus {
    private final AtomicBoolean failedSinceLastSuccess = new AtomicBoolean();
    public void recordSuccess() { failedSinceLastSuccess.set(false); }
    public void recordFailure() { failedSinceLastSuccess.set(true); }
    public boolean failedSinceLastSuccess() { return failedSinceLastSuccess.get(); }
}
