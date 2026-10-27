package io.github.archetom.crm.infra.external.gleif.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Deployment-owned GLEIF binding. Values are deliberately incomplete by default: enabling the
 * bean alone never creates a source capability.
 */
@Data
@ConfigurationProperties("atom.enterprise-data.gleif")
public class GleifEnterpriseDataProperties {
    private boolean enabled;
    private URI apiBaseUri;
    private Duration requestTimeout;
    private Integer maxCandidates;
    private Duration maximumObservationTtl;
    private String policyVersion;
    private String policyDigest;
    private String raCatalogVersion;
    private String raCatalogDigest;
    /** Opaque deployment approval reference; never exposed through the application API. */
    private String dataRightsApprovalRef;
    /** Owner-only, independent runner evidence; a property value alone cannot make GLEIF ready. */
    private Path conformanceReport;
    /** Explicit tenant binding; an empty list is unavailable rather than a global enablement. */
    private Set<Long> tenantIds = new LinkedHashSet<>();
    /** Jurisdiction allowlist for the source policy, not a caller-controlled search filter. */
    private Set<String> legalJurisdictions = new LinkedHashSet<>();
    private SharedQuota sharedQuota = new SharedQuota();

    @Data
    public static class SharedQuota {
        private Integer maximumRequests;
        private Duration window;
    }
}
