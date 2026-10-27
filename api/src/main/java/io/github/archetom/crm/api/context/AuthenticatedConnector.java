package io.github.archetom.crm.api.context;

/**
 * Non-human identity established by the connector authentication boundary.
 *
 * <p>The only identity value is {@code connectorRef}; the opaque evidence is an in-process
 * capability issued by the core authenticator and is never a tenant, actor, role, authority,
 * provider account, or connection generation. Application ingress verifies that evidence and
 * then resolves the current registration before accessing tenant-owned data.</p>
 */
public final class AuthenticatedConnector {
    private final String connectorRef;
    private final Object authenticationEvidence;

    /**
     * Infrastructure transports may carry authentication evidence, but only evidence issued by
     * the configured core authenticator is accepted by the application guard.
     */
    public AuthenticatedConnector(String connectorRef, Object authenticationEvidence) {
        if (connectorRef == null || connectorRef.isBlank()) {
            throw new IllegalArgumentException("connectorRef must not be empty");
        }
        if (authenticationEvidence == null) {
            throw new IllegalArgumentException("connector authentication evidence is required");
        }
        this.connectorRef = connectorRef;
        this.authenticationEvidence = authenticationEvidence;
    }

    public String connectorRef() {
        return connectorRef;
    }

    /** Internal capability; it must never be serialized, logged, or accepted from request data. */
    public Object authenticationEvidence() {
        return authenticationEvidence;
    }

    @Override
    public String toString() {
        return "AuthenticatedConnector[REDACTED]";
    }
}
