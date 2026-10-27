package io.github.archetom.crm.domain.valueobject;

import lombok.Value;

/** Opaque identity of a one-shot FILE-field staging draft. */
@Value
public class FileFieldDraftId implements ValueObject<FileFieldDraftId> {
    Long value;

    public FileFieldDraftId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("file field draft ID must be positive");
        }
        this.value = value;
    }

    @Override
    public boolean sameValueAs(FileFieldDraftId other) {
        return other != null && value.equals(other.value);
    }
}
