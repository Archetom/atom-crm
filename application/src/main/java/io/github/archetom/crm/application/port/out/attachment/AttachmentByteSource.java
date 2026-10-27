package io.github.archetom.crm.application.port.out.attachment;

import java.io.IOException;
import java.io.InputStream;

/** One bounded, non-serializable input stream supplied by a transport or storage adapter. */
@FunctionalInterface
public interface AttachmentByteSource {
    InputStream openStream() throws IOException;

    /** Transport-proven content length; unknown lengths are never accepted for B24a upload. */
    default long declaredLength() { return -1; }
}
