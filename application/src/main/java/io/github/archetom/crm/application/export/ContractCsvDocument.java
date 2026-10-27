package io.github.archetom.crm.application.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Completed, one-shot UTF-8 contract CSV payload; it only exists for the response stream. */
public final class ContractCsvDocument implements AutoCloseable {

    private byte[] content;
    private final AutoCloseable release;
    private boolean consumed;
    private boolean released;

    public ContractCsvDocument(byte[] content, AutoCloseable release) {
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.release = Objects.requireNonNull(release, "release must not be null");
    }

    public synchronized long size() {
        return content == null ? 0 : content.length;
    }

    /** Opens the complete result once; closing the stream releases the export admission slot. */
    public InputStream openStream() {
        byte[] payload;
        synchronized (this) {
            if (consumed || content == null) {
                throw new IllegalStateException("contract CSV document is no longer available");
            }
            consumed = true;
            payload = content;
            content = null;
        }
        return new ReleasingInputStream(payload);
    }

    private final class ReleasingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private ReleasingInputStream(byte[] payload) {
            super(payload);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } finally {
                ContractCsvDocument.this.close();
            }
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            content = null;
            if (released) {
                return;
            }
            released = true;
        }
        try {
            release.close();
        } catch (Exception exception) {
            throw new IllegalStateException("contract CSV admission release failed", exception);
        }
    }

    @Override
    public String toString() {
        return "ContractCsvDocument[redacted]";
    }
}
