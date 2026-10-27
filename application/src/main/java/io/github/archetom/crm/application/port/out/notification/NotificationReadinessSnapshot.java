package io.github.archetom.crm.application.port.out.notification;

import java.time.LocalDateTime;
import java.time.Duration;

/** Short-lived adapter capability evidence with no endpoint or provider response data. */
public record NotificationReadinessSnapshot(
        NotificationChannel channel,
        String adapterContract,
        String adapterBuild,
        String bindingRevision,
        NotificationDispatchGuarantee guarantee,
        String templateRevision,
        LocalDateTime expiresAt) {
    private static final Duration MAX_REMAINING_TTL = Duration.ofMinutes(5);

    public NotificationReadinessSnapshot {
        if (channel == null || blank(adapterContract) || blank(adapterBuild) || blank(bindingRevision)
                || guarantee == null || blank(templateRevision) || expiresAt == null) {
            throw new IllegalArgumentException("notification readiness snapshot is invalid");
        }
    }

    public boolean readyAt(NotificationChannel expectedChannel, String expectedTemplateRevision,
                           String expectedAdapterContract, String expectedAdapterBuild,
                           String expectedBindingRevision, LocalDateTime now) {
        return expectedChannel == channel && expectedTemplateRevision != null
                && expectedTemplateRevision.equals(templateRevision)
                && expectedAdapterContract != null && expectedAdapterContract.equals(adapterContract)
                && expectedAdapterBuild != null && expectedAdapterBuild.equals(adapterBuild)
                && expectedBindingRevision != null && expectedBindingRevision.equals(bindingRevision)
                && now != null && expiresAt.isAfter(now) && !expiresAt.isAfter(now.plus(MAX_REMAINING_TTL))
                && guarantee.kind() != NotificationDispatchGuaranteeKind.UNSUPPORTED;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > 128;
    }
}
