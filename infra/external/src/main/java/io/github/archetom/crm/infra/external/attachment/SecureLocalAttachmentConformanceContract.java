package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.StorageBindingRef;

import java.util.Set;
import java.nio.file.Path;
import java.io.IOException;

/** Public, non-secret contract data used by an independent deployment conformance runner. */
public final class SecureLocalAttachmentConformanceContract {
    private SecureLocalAttachmentConformanceContract() { }
    public static String profile() { return SecureLocalAttachmentConformanceManifest.PROFILE; }
    public static String contractVersion() { return SecureLocalAttachmentConformanceManifest.CONTRACT; }
    public static Set<String> requiredScenarios() { return SecureLocalAttachmentConformanceManifest.requiredScenarios(); }
    public static String currentAdapterBuildDigest() { return SecureLocalAttachmentConformanceManifest.runtimeAdapterDigest(); }
    public static String bindingDigest(StorageBindingRef binding) {
        return SecureLocalAttachmentConformanceManifest.expectedDigest(binding);
    }
    public static String storageRootDigest(Path root) throws IOException {
        return SecureLocalAttachmentStorage.rootAttestationDigest(root);
    }
    public static String policyDigest(SecureLocalAttachmentProperties properties) {
        return SecureLocalAttachmentConformanceManifest.policyDigest(properties);
    }
}
