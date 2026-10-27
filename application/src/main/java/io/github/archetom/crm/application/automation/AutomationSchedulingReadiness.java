package io.github.archetom.crm.application.automation;

/** Fixed, safe reasons why the durable automation control plane may not claim work. */
public enum AutomationSchedulingReadiness {
    READY,
    REVISION_DRIFT,
    NOT_RECONCILED
}
