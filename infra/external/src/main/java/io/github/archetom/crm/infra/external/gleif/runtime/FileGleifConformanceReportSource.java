package io.github.archetom.crm.infra.external.gleif.runtime;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/** Reads a bounded owner-only runner report and reduces parse failures to absent evidence. */
final class FileGleifConformanceReportSource implements GleifConformanceReportSource {
    private final Path report;
    private final ObjectMapper json = new ObjectMapper();

    FileGleifConformanceReportSource(Path report) {
        if (report == null || !report.isAbsolute()) throw new IllegalArgumentException("GLEIF report path must be absolute");
        this.report = report.normalize();
    }

    @Override public Optional<Report> currentReport() {
        try {
            if (Files.isSymbolicLink(report) || !Files.isRegularFile(report, LinkOption.NOFOLLOW_LINKS)
                    || !ownerOnly(report) || Files.size(report) > 32 * 1024) return Optional.empty();
            JsonNode root = json.readTree(Files.readString(report, StandardCharsets.UTF_8));
            Set<String> scenarios = new LinkedHashSet<>();
            JsonNode values = root.path("passedScenarios");
            if (!values.isArray()) return Optional.empty();
            for (JsonNode value : values) {
                String scenario = value.asText(null);
                if (scenario == null || !scenario.matches("O2-\\d{2}")) return Optional.empty();
                scenarios.add(scenario);
            }
            return Optional.of(new Report(text(root, "contractVersion"), text(root, "adapterBuildDigest"),
                    text(root, "policyDigest"), text(root, "raCatalogDigest"), text(root, "quotaMode"),
                    Instant.parse(text(root, "expiresAt")), scenarios));
        } catch (Exception exception) { return Optional.empty(); }
    }

    private static String text(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank() || value.length() > 1024) throw new IllegalArgumentException("report field unavailable");
        return value;
    }
    private static boolean ownerOnly(Path file) throws Exception {
        PosixFileAttributes attributes = Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Set<PosixFilePermission> permissions = attributes.permissions();
        return !permissions.contains(PosixFilePermission.GROUP_READ) && !permissions.contains(PosixFilePermission.GROUP_WRITE)
                && !permissions.contains(PosixFilePermission.GROUP_EXECUTE) && !permissions.contains(PosixFilePermission.OTHERS_READ)
                && !permissions.contains(PosixFilePermission.OTHERS_WRITE) && !permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
    }
}
