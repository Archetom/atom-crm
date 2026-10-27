package io.github.archetom.crm.application.service.attachment.worker;

/** Scheduler-facing durable worker; each invocation claims at most the policy's bounded batch. */
public interface AttachmentWorker {
    void runOnce();
}
