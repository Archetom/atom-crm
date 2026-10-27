package io.github.archetom.crm.api.facade;

import java.io.IOException;
import java.io.InputStream;

/** Bounded upload bytes supplied by the HTTP adapter without leaking a servlet type into the use case. */
public interface FileFieldUploadContent {
    InputStream openStream() throws IOException;

    long declaredLength();
}
