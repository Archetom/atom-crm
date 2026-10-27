package io.github.archetom.crm.application.port.out.attachment;

import java.io.IOException;
import java.io.InputStream;

/** One request-local storage handle. It must be closed or cancelled exactly once. */
public interface AttachmentReadHandle extends AutoCloseable {
    InputStream inputStream() throws IOException;
    void cancel();
    @Override void close() throws IOException;
}
