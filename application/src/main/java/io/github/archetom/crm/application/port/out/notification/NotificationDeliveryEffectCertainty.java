package io.github.archetom.crm.application.port.out.notification;

/** Durable provider-effect knowledge. AMBIGUOUS is sticky until an accepted result resolves the exact command. */
public enum NotificationDeliveryEffectCertainty {
    /** No provider effect has been attempted or the latest attempted call was explicitly not accepted. */
    NO_EFFECT,
    /** A provider effect may have happened and must never be terminalized as DEAD. */
    AMBIGUOUS,
    /** The provider explicitly accepted the exact command. */
    ACCEPTED
}
