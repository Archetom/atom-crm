package io.github.archetom.crm.application.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Record-scoped visibility is derived in exactly one place.
 *
 * <p>Eight aggregates used to carry a byte-for-byte copy of the same derivation. They agreed at the
 * time, but nothing kept them agreeing: adding a check to one and missing another would widen or
 * narrow that aggregate's visibility without failing to compile and without failing a test. Each
 * resolver now contributes only its typed value, so a change to the rule reaches all of them.</p>
 */
class DataScopeResolutionIsSharedTest {

    private static final Path SECURITY =
            Path.of("src/main/java/io/github/archetom/crm/application/security");

    /** Steps that belong to the shared derivation and must not reappear in a per-aggregate copy. */
    private static final List<String> SHARED_STEPS = List.of(
            "requireActiveMembership", "dataScopeResolver.resolve",
            "membershipRepository.findByMember", "findDescendants", "DEPARTMENT_SUBTREE");

    @Test
    void noAggregateResolverReimplementsTheSharedDerivation() {
        List<Path> resolvers = recordScopedResolvers();
        assertTrue(resolvers.size() >= 8,
                () -> "expected the aggregate resolvers, found " + resolvers.size());

        List<String> reimplemented = new ArrayList<>();
        for (Path resolver : resolvers) {
            String source = read(resolver);
            for (String step : SHARED_STEPS) {
                if (source.contains(step)) {
                    reimplemented.add(resolver.getFileName() + " performs '" + step
                            + "' itself; delegate to DataScopeVisibilityResolver instead");
                }
            }
        }
        assertTrue(reimplemented.isEmpty(), () -> String.join("\n", reimplemented));
    }

    @Test
    void everyAggregateResolverDelegatesToTheSharedResolver() {
        List<String> notDelegating = recordScopedResolvers().stream()
                .filter(path -> !read(path).contains("DataScopeVisibilityResolver"))
                .map(path -> path.getFileName().toString())
                .toList();
        assertTrue(notDelegating.isEmpty(),
                () -> "resolvers that do not use the shared derivation: " + notDelegating);
    }

    @Test
    void theSharedResolverStillPerformsEveryStep() {
        String shared = read(SECURITY.resolve("DataScopeVisibilityResolver.java"));
        List<String> missing = SHARED_STEPS.stream().filter(step -> !shared.contains(step)).toList();
        assertTrue(missing.isEmpty(), () -> "the shared derivation dropped: " + missing);
    }

    /**
     * The record-scoped aggregates only. OA Request authorises by participation and has no data
     * scope, and the field-access resolver answers a different question entirely.
     */
    private static List<Path> recordScopedResolvers() {
        try (Stream<Path> files = Files.list(SECURITY)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith("AccessResolver.java"))
                    .filter(path -> !path.getFileName().toString().startsWith("OaRequest"))
                    .filter(path -> !path.getFileName().toString().startsWith("EffectiveField"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
