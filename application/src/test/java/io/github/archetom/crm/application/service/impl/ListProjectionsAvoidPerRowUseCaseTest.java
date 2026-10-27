package io.github.archetom.crm.application.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A facade maps a use-case result to a response; it does not start another use case per row.
 *
 * <p>Response assembly used to call back into the FILE-value use case once for every row of a
 * paged list. Because that use case opens its own {@code REQUIRES_NEW} read transaction, a
 * two-hundred row page produced two hundred extra transactions, each repeating the membership
 * lookup, the target authorization, and the field catalog for one row. The batch entry point
 * resolves the whole page in one read; this keeps the per-row shape from coming back.</p>
 */
class ListProjectionsAvoidPerRowUseCaseTest {

    private static final Path FACADES =
            Path.of("../infra/facade/src/main/java/io/github/archetom/crm/infra/facade");

    /**
     * Enrichment inside a stream over a page is the shape that produced one transaction per row.
     * A single-record path calls the same helper directly and is unaffected.
     */
    private static final Pattern PER_ROW_ENRICHMENT = Pattern.compile(
            "\\.stream\\(\\)[^;]*?(?:response|fileFieldResponseEnricher)\\s*\\([^;]*?caller",
            Pattern.DOTALL);

    @Test
    void noFacadeEnrichesAListOneRowAtATime() {
        List<Path> facades = facades();
        assertFalse(facades.isEmpty(), () -> "expected the facade sources at " + FACADES.toAbsolutePath());

        List<String> perRow = new ArrayList<>();
        for (Path facade : facades) {
            String source = read(facade);
            Matcher matcher = PER_ROW_ENRICHMENT.matcher(source);
            while (matcher.find()) {
                int line = source.substring(0, matcher.start()).split("\n", -1).length;
                perRow.add(facade.getFileName() + ":" + line
                        + " enriches a projection row by row; resolve the page in one read instead");
            }
        }
        assertTrue(perRow.isEmpty(), () -> String.join("\n", perRow));
    }

    @Test
    void theBatchEntryPointIsTheOneActuallyUsed() {
        long batchUsers = facades().stream()
                .map(ListProjectionsAvoidPerRowUseCaseTest::read)
                .filter(source -> source.contains("responses(caller,"))
                .count();
        // Every facade that carries FILE values on a list must route through the batch helper.
        assertTrue(batchUsers >= 13,
                () -> "expected the FILE-bearing facades to use the batch helper, found " + batchUsers);
    }

    private static List<Path> facades() {
        if (!Files.isDirectory(FACADES)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(FACADES)) {
            return files.filter(path -> path.getFileName().toString().endsWith("FacadeImpl.java"))
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
