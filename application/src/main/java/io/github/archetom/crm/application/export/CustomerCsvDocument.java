package io.github.archetom.crm.application.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Completed, one-shot UTF-8 CSV payload. It is deliberately not serializable, cacheable, or
 * reusable; closing it releases the request's local export admission slot.
 */
public final class CustomerCsvDocument implements AutoCloseable {

    private byte[] content;
    private final AutoCloseable release;
    private boolean consumed;
    private boolean released;

    public CustomerCsvDocument(byte[] content, AutoCloseable release) {
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.release = Objects.requireNonNull(release, "release must not be null");
    }

    public synchronized long size() {
        return content == null ? 0 : content.length;
    }

    /**
     * Opens the completed payload exactly once. Closing the stream always releases the local
     * admission lease, including when the response writer aborts because a client disconnects.
     */
    public InputStream openStream() {
        byte[] payload;
        synchronized (this) {
            if (consumed || content == null) {
                throw new IllegalStateException("customer CSV document is no longer available");
            }
            consumed = true;
            payload = content;
            content = null;
        }
        return new ReleasingInputStream(payload);
    }

    public void writeTo(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output must not be null");
        try (InputStream input = openStream()) {
            input.transferTo(output);
        }
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
                CustomerCsvDocument.this.close();
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
            throw new IllegalStateException("customer CSV admission release failed", exception);
        }
    }

    @Override
    public String toString() {
        return "CustomerCsvDocument[redacted]";
    }
}
