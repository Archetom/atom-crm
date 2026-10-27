package io.github.archetom.crm.infra.external.attachment;

import io.github.archetom.crm.application.port.out.attachment.AttachmentContentScanner;

import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a runner-produced, owner-only JSON report.  Runtime never writes this file and accepts no
 * properties-backed report fallback, keeping deployment evidence outside the application process.
 */
public final class FileSecureLocalAttachmentConformanceReportSource implements SecureLocalAttachmentConformanceReportSource {
    private final Path report;

    public FileSecureLocalAttachmentConformanceReportSource(Path report) {
        if (report == null || !report.isAbsolute()) throw new IllegalArgumentException("conformance report path must be absolute");
        this.report = report.normalize();
    }

    @Override
    public Optional<Report> currentReport() {
        try {
            if (Files.isSymbolicLink(report) || !Files.isRegularFile(report, LinkOption.NOFOLLOW_LINKS)
                    || !ownerOnly(report)) return Optional.empty();
            String json = Files.readString(report, StandardCharsets.UTF_8);
            if (json.length() > 32 * 1024) return Optional.empty();
            return Optional.of(parse(json));
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    private static Report parse(String json) {
        String contract = string(json, "contractVersion");
        String adapter = string(json, "adapterBuildDigest");
        String engine = string(json, "engineBuildDigest");
        String binding = string(json, "bindingDigest");
        String root = string(json, "storageRootDigest");
        String policy = string(json, "policyDigest");
        String detector = string(json, "detectorProfile");
        String detectorCatalog = string(json, "detectorCatalogRevision");
        String scanner = string(json, "scannerProfile");
        String allowlist = string(json, "allowlistRevision");
        String generation = string(json, "definitionGeneration");
        AttachmentContentScanner.DefinitionBasis basis = AttachmentContentScanner.DefinitionBasis.valueOf(string(json, "definitionBasis"));
        Instant validUntil = Instant.parse(string(json, "definitionsValidUntil"));
        Instant expiresAt = Instant.parse(string(json, "expiresAt"));
        return new Report(contract, adapter, engine, binding, root, policy, detector, detectorCatalog, scanner, allowlist,
                generation, basis, validUntil, expiresAt, scenarios(json));
    }

    private static String string(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\":\\\"([^\\\"]{1,1024})\\\"").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("conformance report field is missing");
        return matcher.group(1);
    }
    private static Set<String> scenarios(String json) {
        Matcher matcher = Pattern.compile("\\\"passedScenarios\\\":\\[(.*?)\\]").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("conformance scenarios are missing");
        Set<String> values = new LinkedHashSet<>();
        Matcher item = Pattern.compile("\\\"([SIX]-\\d{2})\\\"").matcher(matcher.group(1));
        while (item.find()) values.add(item.group(1));
        return Set.copyOf(values);
    }
    private static boolean ownerOnly(Path file) throws IOException {
        PosixFileAttributes attributes = Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Set<PosixFilePermission> permissions = attributes.permissions();
        return !permissions.contains(PosixFilePermission.GROUP_READ) && !permissions.contains(PosixFilePermission.GROUP_WRITE)
                && !permissions.contains(PosixFilePermission.GROUP_EXECUTE) && !permissions.contains(PosixFilePermission.OTHERS_READ)
                && !permissions.contains(PosixFilePermission.OTHERS_WRITE) && !permissions.contains(PosixFilePermission.OTHERS_EXECUTE);
    }
}
