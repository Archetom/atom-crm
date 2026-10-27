package io.github.archetom.crm.application.port.out.attachment;

/** Independently gated runtime operations; missing evidence never falls back to ready. */
public enum AttachmentOperation {
    ALLOCATE_WRITE, STORE_SINGLE_STREAM, PROBE_EXACT, READ_FULL, READ_SINGLE_RANGE,
    DELETE_EXACT, CONFIRM_ABSENCE, ABORT_INCOMPLETE
}
