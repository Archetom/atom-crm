package io.github.archetom.crm.infra.external.gleif.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Set;
import java.util.jar.JarFile;

/** Current-build evidence check for deployment readiness; partial/local canaries can never pass it. */
final class GleifConformanceManifest {
    private static final String CONTRACT = "B23_GLEIF_CONFORMANCE_V1";
    private static final Set<String> REQUIRED = Set.of("O2-01", "O2-02", "O2-03", "O2-04", "O2-05", "O2-06",
            "O2-07", "O2-08", "O2-09", "O2-10", "O2-11", "O2-12");
    private GleifConformanceManifest() { }

    static boolean current(GleifConformanceReportSource source, Instant now) {
        if (source == null || now == null) return false;
        return source.currentReport().filter(report -> CONTRACT.equals(report.contractVersion())
                && same(runtimeAdapterDigest(), report.adapterBuildDigest())
                && same(GleifSourcePolicyCatalog.DIGEST, report.policyDigest())
                && same(GleifRegistrationAuthorityCatalog.DIGEST, report.raCatalogDigest())
                && "DURABLE_SHARED".equals(report.quotaMode()) && report.expiresAt() != null
                && report.expiresAt().isAfter(now) && report.passedScenarios().containsAll(REQUIRED)).isPresent();
    }

    static String runtimeAdapterDigest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path source = Path.of(GleifEnterpriseDataSource.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(source)) {
                try (var stream = Files.walk(source)) {
                    for (Path file : stream.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(path -> source.relativize(path).toString())).toList()) {
                        digest.update((source.relativize(file).toString() + "\n").getBytes(StandardCharsets.UTF_8));
                        digest.update(Files.readAllBytes(file));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(source.toFile())) {
                    for (var entry : jar.stream().filter(entry -> !entry.isDirectory())
                            .sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList()) {
                        digest.update((entry.getName() + "\n").getBytes(StandardCharsets.UTF_8));
                        try (var input = jar.getInputStream(entry)) { digest.update(input.readAllBytes()); }
                    }
                }
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (Exception exception) { throw new IllegalStateException("GLEIF adapter digest is unavailable", exception); }
    }
    private static boolean same(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
