package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.domain.model.AttachmentContentEvidence;

import java.io.InputStream;
import java.time.Instant;

/** Private engine boundary; implementations must be definition-fenced and never accept a path or URL. */
public interface StreamingInspectionEngine {
    /** Immutable generation evidence; it must describe the engine that executes {@link #scan}. */
    Evidence evidence();

    AttachmentContentScanner.ScanResult scan(InputStream input, AttachmentContentEvidence evidence) throws Exception;

    record Evidence(String engineBuildDigest, String definitionGeneration,
                    AttachmentContentScanner.DefinitionBasis definitionBasis,
                    Instant loadedAt, Instant definitionsValidUntil) { }
}
