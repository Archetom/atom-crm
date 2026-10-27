package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque local identity of one tenant-scoped attachment aggregate. */
@Value
public class AttachmentFileId implements ValueObject<AttachmentFileId> {

    Long value;

    public AttachmentFileId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("attachment file ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(AttachmentFileId other) {
        return other != null && value.equals(other.value);
    }
}
