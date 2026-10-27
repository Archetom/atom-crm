package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentDetector;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * Opt-in secure-local composition.  It only reports READY after a current binding-specific
 * conformance manifest and a private streamed inspection engine are both present.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "atom.files", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SecureLocalAttachmentProperties.class)
public class SecureLocalAttachmentProfileConfiguration {
    private static final Set<String> MEDIA = Set.of("application/pdf", "image/png", "image/jpeg", "text/csv");
    private static final Set<AttachmentOperation> OPERATIONS = Set.of(AttachmentOperation.values());

    @Bean
    @ConditionalOnProperty(prefix = "atom.files", name = "conformance-report")
    SecureLocalAttachmentConformanceReportSource secureLocalAttachmentConformanceReportSource(
            SecureLocalAttachmentProperties properties) {
        return new FileSecureLocalAttachmentConformanceReportSource(properties.getConformanceReport());
    }
    @Bean
    @ConditionalOnProperty(prefix = "atom.files.clamd", name = "enabled", havingValue = "true")
    StreamingInspectionEngine streamingInspectionEngine(SecureLocalAttachmentProperties properties) {
        return new ClamdInstreamStreamingInspectionEngine(properties.getClamd());
    }
    @Bean
    SecureLocalAttachmentProfileState secureLocalAttachmentProfileState(SecureLocalAttachmentProperties properties,
                                                                          ObjectProvider<SecureLocalAttachmentConformanceReportSource> reports,
                                                                          ObjectProvider<StreamingInspectionEngine> engines) {
        return new SecureLocalAttachmentProfileState(properties, reports.getIfAvailable(), engines.getIfAvailable(),
                Clock.systemUTC());
    }
    @Bean
    AttachmentRuntimePolicy attachmentRuntimePolicy(SecureLocalAttachmentProfileState state) { return state.policy(); }
    @Bean
    AttachmentStorage attachmentStorage(SecureLocalAttachmentProfileState state) {
        if (state.storage() != null) return state.storage();
        return new AttachmentStorage() {
            private UnsupportedOperationException unavailable() {
                return new UnsupportedOperationException("secure-local attachment profile is not ready");
            }
            @Override public Allocation allocate(io.github.archetom.crm.application.port.out.attachment.StorageBindingRef binding, long expectedBytes, Instant deadline) { throw unavailable(); }
            @Override public StoreReceipt store(StoreCommand command, io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource source) { throw unavailable(); }
            @Override public ProbeResult probe(ProbeCommand command) { throw unavailable(); }
            @Override public io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle open(OpenCommand command) { throw unavailable(); }
            @Override public void requestDelete(DeleteCommand command) { throw unavailable(); }
            @Override public boolean confirmAbsent(DeleteCommand command) { throw unavailable(); }
            @Override public AbortState abortIncomplete(io.github.archetom.crm.application.port.out.attachment.StorageBindingRef binding, io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator locator, byte[] storageGeneration, long attempt, Instant deadline) { throw unavailable(); }
        };
    }
    @Bean
    AttachmentContentDetector attachmentContentDetector() { return new BoundedMagicAttachmentContentDetector(); }
    @Bean
    AttachmentContentScanner attachmentContentScanner(ObjectProvider<StreamingInspectionEngine> engines) {
        StreamingInspectionEngine engine = engines.getIfAvailable();
        return engine == null ? (handle, evidence) -> new AttachmentContentScanner.ScanResult(
                AttachmentContentScanner.ScanVerdict.FAILED, null, "ENGINE_NOT_READY")
                : new StreamedInspectionAdapter(engine);
    }
    @Bean
    AttachmentStorageReadinessResolver attachmentStorageReadinessResolver(SecureLocalAttachmentProfileState state,
                                                                            SecureLocalAttachmentProperties properties) {
        return (TenantId tenant, AttachmentOperation operation) -> {
            Instant now = Instant.now();
            var evidence = state.storageReadinessEvidence(properties, now)
                    .filter(value -> value.operations().contains(operation));
            boolean ready = evidence.isPresent();
            return new AttachmentStorageReadinessResolver.Snapshot(ready ? AttachmentReadiness.READY : AttachmentReadiness.NOT_READY,
                    ready ? state.binding() : null, evidence.map(SecureLocalAttachmentProfileState.StorageReadinessEvidence::operations).orElse(Set.of()),
                    evidence.map(SecureLocalAttachmentProfileState.StorageReadinessEvidence::expiresAt).orElse(now),
                    ready ? "CURRENT_MANIFEST" : "MANIFEST_OR_BINDING_NOT_READY");
        };
    }
    @Bean
    AttachmentInspectionReadinessResolver attachmentInspectionReadinessResolver(
            SecureLocalAttachmentProfileState state, SecureLocalAttachmentProperties properties,
            ObjectProvider<StreamingInspectionEngine> engines) {
        return tenant -> {
            Instant now = Instant.now();
            StreamingInspectionEngine engine = engines.getIfAvailable();
            var evidence = state.readinessEvidence(properties, engine, now);
            boolean ready = evidence.isPresent();
            return new AttachmentInspectionReadinessResolver.Snapshot(ready ? AttachmentReadiness.READY : AttachmentReadiness.NOT_READY,
                    ready ? MEDIA : Set.of(),
                    evidence.map(SecureLocalAttachmentProfileState.ReadinessEvidence::expiresAt).orElse(now),
                    evidence.map(SecureLocalAttachmentProfileState.ReadinessEvidence::definitionGeneration).orElse(null),
                    ready ? "CURRENT_MANIFEST" : "ENGINE_OR_MANIFEST_NOT_READY");
        };
    }
}
