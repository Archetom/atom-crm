package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentDetector;
import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;
import io.github.archetom.crm.application.port.out.attachment.AttachmentInspectionReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentOperation;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadHandle;
import io.github.archetom.crm.application.port.out.attachment.AttachmentReadiness;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorage;
import io.github.archetom.crm.application.port.out.attachment.AttachmentStorageReadinessResolver;
import io.github.archetom.crm.application.port.out.attachment.AttachmentByteSource;
import io.github.archetom.crm.application.port.out.attachment.PlannedObjectLocator;
import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Set;

/** Default production-safe composition. Existing CRM remains available while every file operation is not ready. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "atom.files", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAttachmentConfiguration {
    @Bean @ConditionalOnMissingBean
    AttachmentRuntimePolicy attachmentRuntimePolicy() { return AttachmentRuntimePolicy.disabled(); }

    @Bean @ConditionalOnMissingBean
    AttachmentStorageReadinessResolver attachmentStorageReadinessResolver() {
        return (TenantId tenantId, AttachmentOperation operation) -> new AttachmentStorageReadinessResolver.Snapshot(
                AttachmentReadiness.NOT_READY, null, Set.of(), Instant.EPOCH, "DISABLED");
    }

    @Bean @ConditionalOnMissingBean
    AttachmentInspectionReadinessResolver attachmentInspectionReadinessResolver() {
        return tenantId -> new AttachmentInspectionReadinessResolver.Snapshot(
                AttachmentReadiness.NOT_READY, Set.of(), Instant.EPOCH, "DISABLED");
    }

    @Bean @ConditionalOnMissingBean
    AttachmentStorage attachmentStorage() {
        return new AttachmentStorage() {
            private UnsupportedOperationException disabled() {
                return new UnsupportedOperationException("attachment storage is disabled");
            }
            @Override public Allocation allocate(StorageBindingRef binding, long expectedBytes, Instant deadline) { throw disabled(); }
            @Override public StoreReceipt store(StoreCommand command, AttachmentByteSource source) { throw disabled(); }
            @Override public ProbeResult probe(ProbeCommand command) { throw disabled(); }
            @Override public AttachmentReadHandle open(OpenCommand command) { throw disabled(); }
            @Override public void requestDelete(DeleteCommand command) { throw disabled(); }
            @Override public boolean confirmAbsent(DeleteCommand command) { throw disabled(); }
            @Override public AbortState abortIncomplete(StorageBindingRef binding, PlannedObjectLocator locator,
                                                        byte[] storageGeneration, long attempt, Instant deadline) { throw disabled(); }
        };
    }

    @Bean @ConditionalOnMissingBean
    AttachmentContentDetector attachmentContentDetector() {
        return (handle, evidence) -> new AttachmentContentDetector.Detection(
                AttachmentContentDetector.DetectionState.INDETERMINATE, null, null, "DISABLED");
    }
    @Bean @ConditionalOnMissingBean
    AttachmentContentScanner attachmentContentScanner() {
        return (handle, evidence) -> new AttachmentContentScanner.ScanResult(
                AttachmentContentScanner.ScanVerdict.FAILED, null, "DISABLED");
    }
}
