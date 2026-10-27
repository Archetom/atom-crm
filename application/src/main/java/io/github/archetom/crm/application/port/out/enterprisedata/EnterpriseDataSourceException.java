package io.github.archetom.crm.application.port.out.enterprisedata;

import java.util.Objects;

/** Provider-neutral adapter failure without request, response, account, or provider text. */
public final class EnterpriseDataSourceException extends RuntimeException {

    private final EnterpriseDataSourceError error;

    public EnterpriseDataSourceException(EnterpriseDataSourceError error) {
        super("enterprise data source operation failed");
        this.error = Objects.requireNonNull(error, "source error must not be null");
    }

    public EnterpriseDataSourceError error() {
        return error;
    }
}
