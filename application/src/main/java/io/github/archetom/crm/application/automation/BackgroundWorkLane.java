package io.github.archetom.crm.application.automation;

/** Fixed local resource lanes; never a tenant or business identifier. */
public enum BackgroundWorkLane {
    AUTOMATION,
    ATTACHMENT,
    NOTIFICATION,
    NOTIFICATION_DELIVERY,
    OA_ANNOUNCEMENT_PUBLICATION,
    ENTERPRISE_DATA_ADOPTION_RETENTION
}
