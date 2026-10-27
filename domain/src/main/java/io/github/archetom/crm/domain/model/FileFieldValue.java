package io.github.archetom.crm.domain.model;

import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.domain.valueobject.FieldCode;

import java.util.Arrays;

/** Immutable structured FILE value; it is not a scalar custom-field value. */
public record FileFieldValue(FieldCode fieldCode, AttachmentFileId fileId, int ordinal,
                             long contentVersion, byte[] evidenceDigest) {
    public FileFieldValue {
        if (fieldCode == null || fileId == null || ordinal < 0 || ordinal > 9
                || contentVersion < 1 || evidenceDigest == null || evidenceDigest.length != 32) {
            throw new IllegalArgumentException("file field value is invalid");
        }
        evidenceDigest = Arrays.copyOf(evidenceDigest, evidenceDigest.length);
    }
    @Override public byte[] evidenceDigest() { return Arrays.copyOf(evidenceDigest, evidenceDigest.length); }
}
