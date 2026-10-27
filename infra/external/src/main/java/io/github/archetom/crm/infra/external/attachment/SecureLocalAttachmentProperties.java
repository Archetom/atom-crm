package io.github.archetom.crm.infra.external.attachment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/** Explicit opt-in configuration; incomplete values deliberately keep the profile not ready. */
@Data
@ConfigurationProperties("atom.files")
public class SecureLocalAttachmentProperties {
    private boolean enabled;
    private String storageProfile;
    private Path secureLocalRoot;
    private String bindingBase64;
    /** Read-only evidence emitted by the separate deployment conformance runner. */
    private Path conformanceReport;
    private Clamd clamd = new Clamd();
    private Inspection inspection = new Inspection();
    private Policy policy = new Policy();

    @Data
    public static class Inspection {
        private String detectorProfile;
        private String scannerProfile;
        private String detectorCatalogRevision;
        private String allowlistRevision;
        private Duration definitionMaxAge;
    }

    /**
     * The first real scanner profile deliberately permits only a local Unix socket.  clamd's
     * native TCP protocol has neither TLS nor authentication and must not become a production
     * scanner endpoint through this configuration surface.
     */
    @Data
    public static class Clamd {
        private boolean enabled;
        private Path unixSocket;
        /** Reserved for the isolated canary runner; production composition rejects it. */
        private Integer loopbackTcpPort;
        private Duration connectTimeout;
        private Duration responseTimeout;
        private Duration definitionEvidenceTtl;
        private Integer chunkBytes;
    }

    @Data
    public static class Policy {
        private Long maxFileBytes;
        private Long maxReadyBytes;
        private Long maxQuarantineBytes;
        private Long maxPendingIntents;
        private Integer maxActiveUploads;
        private Integer maxActiveInspections;
        private Integer maxActiveDownloads;
        private Long globalReserveBytes;
        private Long deletionControlReserve;
        /** Minimum filesystem bytes left after a new secure-local object is admitted. */
        private Long localFreeFloorBytes;
        private Duration intentTtl;
        private Duration uploadOpenTimeout;
        private Duration uploadIdleTimeout;
        private Duration uploadTotalTimeout;
        private Duration inspectionTotalTimeout;
        private Duration downloadOpenTimeout;
        private Duration downloadIdleTimeout;
        private Duration downloadTotalTimeout;
        private Duration jobLease;
        private Duration jobHeartbeat;
        private Integer maxAttempts;
        private Duration quarantineRetention;
        private Duration tombstoneRetention;
        private Integer claimSize;
    }
}
