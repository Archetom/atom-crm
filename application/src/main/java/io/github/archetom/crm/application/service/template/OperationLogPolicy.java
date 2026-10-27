package io.github.archetom.crm.application.service.template;

/** Selects whether an execution surface may expose implementation exception types in logs. */
public enum OperationLogPolicy {
    DEFAULT,
    SAFE_BACKGROUND
}
