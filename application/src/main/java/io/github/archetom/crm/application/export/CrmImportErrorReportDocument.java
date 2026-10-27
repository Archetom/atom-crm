package io.github.archetom.crm.application.export;

import java.util.Objects;

/** Bounded derived CSV containing import issue coordinates only, never source cells. */
public final class CrmImportErrorReportDocument {
    private final byte[] content;

    public CrmImportErrorReportDocument(byte[] content) {
        this.content = Objects.requireNonNull(content, "content must not be null").clone();
    }

    public byte[] content() {
        return content.clone();
    }

    @Override
    public String toString() {
        return "CrmImportErrorReportDocument[redacted]";
    }
}
