package io.github.archetom.crm.domain.model;

import java.util.Arrays;

/** Immutable evidence of one stored content version; provider metadata is intentionally absent. */
public record AttachmentContentEvidence(long actualBytes, byte[] sha256, long contentVersion) {

    public AttachmentContentEvidence {
        if (actualBytes <= 0 || sha256 == null || sha256.length != 32 || contentVersion <= 0) {
            throw new IllegalArgumentException("attachment content evidence is invalid");
        }
        sha256 = Arrays.copyOf(sha256, sha256.length);
    }

    @Override
    public byte[] sha256() {
        return Arrays.copyOf(sha256, sha256.length);
    }
}
