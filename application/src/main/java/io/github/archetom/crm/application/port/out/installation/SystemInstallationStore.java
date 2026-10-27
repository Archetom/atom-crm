package io.github.archetom.crm.application.port.out.installation;

/**
 * Atomic singleton guard for the one-time local tenant installation boundary.
 *
 * <p>This is intentionally a narrow pre-tenant port. It does not expose tenant enumeration,
 * actor lookup, or arbitrary installation-state mutation.</p>
 */
public interface SystemInstallationStore {

    boolean installationRequired();

    /** Claims the initial installation only while no local tenant has been initialized. */
    boolean claimInitialInstallation();
}
