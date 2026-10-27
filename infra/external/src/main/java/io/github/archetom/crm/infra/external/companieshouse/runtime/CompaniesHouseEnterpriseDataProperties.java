package io.github.archetom.crm.infra.external.companieshouse.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/** Deployment-owned Companies House binding. The API key and approvals never cross this boundary. */
@Data
@ConfigurationProperties("atom.enterprise-data.companies-house")
public class CompaniesHouseEnterpriseDataProperties {
    private boolean enabled;
    private URI apiBaseUri;
    private String apiKey;
    private Duration requestTimeout;
    private Integer maxCandidates;
    private Duration maximumObservationTtl;
    private String policyVersion;
    private String policyDigest;
    private String dataRightsApprovalRef;
    private String privacyReviewRef;
    private String copyrightReviewRef;
    private Set<Long> tenantIds = new LinkedHashSet<>();
}
