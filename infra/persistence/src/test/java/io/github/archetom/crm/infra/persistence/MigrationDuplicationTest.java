package io.github.archetom.crm.infra.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Near-identical migrations do not multiply.
 *
 * <p>Five pairs of migrations are large copies of each other: the customer, contact and lead
 * foundations against one another, the customer and contact pools, and the two job-title additions.
 * That is the cost of having modelled three near-identical aggregates, and it is not fixable after
 * the fact — an applied migration must never be edited, so these five pairs are permanent history.</p>
 *
 * <p>What is still open is whether a sixth appears. This holds the five known pairs as the ceiling:
 * a new migration that is a near-copy of an existing one fails here, and the answer is to extract
 * the shared shape rather than paste it again. None of the known pairs can be retired, so the
 * number only ever grows by a deliberate decision.</p>
 */
class MigrationDuplicationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** Below this, two files can look alike simply by both being short. */
    private static final int SUBSTANTIAL_LENGTH = 800;

    private static final double NEAR_COPY = 0.80;

    /**
     * The five pairs that already exist: the customer, contact and lead foundations against each
     * other, the customer and contact pools, and the two job-title additions. Measured similarity
     * for all five sits above 0.82, and the next closest unrelated pair is 0.66 — the ceiling is
     * not near a boundary. Never raise it to admit a new copy.
     */
    private static final int KNOWN_NEAR_COPY_PAIRS = 5;

    @Test
    void noNewMigrationIsANearCopyOfAnExistingOne() {
        List<Path> migrations = migrations();
        assertTrue(migrations.size() > 100,
                () -> "expected the migration history, found " + migrations.size());

        List<String> pairs = new ArrayList<>();
        List<String> bodies = migrations.stream().map(MigrationDuplicationTest::normalized).toList();
        List<Set<String>> fingerprints = bodies.stream()
                .map(body -> body.length() < SUBSTANTIAL_LENGTH ? Set.<String>of() : windows(body))
                .toList();
        for (int first = 0; first < migrations.size(); first++) {
            for (int second = first + 1; second < migrations.size(); second++) {
                if (similarity(fingerprints.get(first), fingerprints.get(second)) > NEAR_COPY) {
                    pairs.add(migrations.get(first).getFileName() + " ↔ "
                            + migrations.get(second).getFileName());
                }
            }
        }

        assertTrue(pairs.size() <= KNOWN_NEAR_COPY_PAIRS,
                () -> "near-copy migrations grew from " + KNOWN_NEAR_COPY_PAIRS + " to " + pairs.size()
                        + "; extract the shared shape instead of pasting it again:\n  "
                        + String.join("\n  ", pairs));
    }

    /**
     * Overlap of the two bodies' 8-character windows. Comparing every pair of a hundred migrations
     * rules out an edit-distance measure; this is linear per file and separates the real copies
     * cleanly from merely similar DDL.
     */
    private static double similarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        long shared = left.stream().filter(right::contains).count();
        return (double) shared / (left.size() + right.size() - shared);
    }

    private static Set<String> windows(String body) {
        Set<String> windows = new HashSet<>();
        for (int index = 0; index + WINDOW <= body.length(); index++) {
            windows.add(body.substring(index, index + WINDOW));
        }
        return windows;
    }

    private static final int WINDOW = 8;

    /** Comments and whitespace differ freely between copies and say nothing about duplication. */
    private static String normalized(Path migration) {
        return read(migration)
                .replaceAll("--[^\n]*", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static List<Path> migrations() {
        if (!Files.isDirectory(MIGRATIONS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sql")).sorted().toList();
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
