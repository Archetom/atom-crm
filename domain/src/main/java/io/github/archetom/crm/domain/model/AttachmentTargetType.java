package io.github.archetom.crm.domain.model;

/** Closed B24 attachment target catalog. Runtime registration remains batch-specific. */
public enum AttachmentTargetType {
    WORK_TASK,
    CUSTOMER,
    CONTACT,
    LEAD,
    OPPORTUNITY,
    CONTRACT,
    INVOICE,
    RECEIVABLE,
    RECEIVABLE_PLAN,
    PRODUCT,
    RETURN_VISIT,
    MARKETING_ACTIVITY,
    OA_ANNOUNCEMENT,
    OA_REQUEST,
    IMPORT_DRAFT
}
