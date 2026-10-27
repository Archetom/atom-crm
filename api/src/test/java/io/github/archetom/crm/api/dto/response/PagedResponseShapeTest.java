package io.github.archetom.crm.api.dto.response;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every paged endpoint reports its page the same way.
 *
 * <p>A caller that pages one collection should not have to discover that another collection names
 * the same three numbers differently. One response used to publish
 * {@code page}/{@code size}/{@code total}/{@code items} — the internal value object's names, which
 * had leaked through the mapper into the public contract — while the other 37 used
 * {@code pageNum}/{@code pageSize}/{@code totalNum}/{@code objectList}.</p>
 *
 * <p>Nothing prevented that, and nothing would prevent the next one: the compiler is satisfied by
 * any field names at all, and each response is only ever read by the test for its own endpoint.
 * This asserts the shared shape across every paged response at once.</p>
 */
class PagedResponseShapeTest {

    private static final Path RESPONSES =
            Path.of("src/main/java/io/github/archetom/crm/api/dto/response");

    private static final List<String> REQUIRED_FIELDS =
            List.of("pageNum", "pageSize", "totalNum", "objectList");

    /** Names that mean the same thing but are not the agreed ones. */
    private static final List<String> REJECTED_FIELDS =
            List.of("items", "records", "content", "total", "current", "totalPage");

    private static final Pattern COMPONENT =
            Pattern.compile("(?:long|Long|int|Integer|List<[^>]+>)\\s+([a-zA-Z]\\w*)\\s*[,)]");

    @Test
    void everyPagedResponseUsesTheSharedPageFields() {
        List<Path> paged = pagedResponses();
        assertTrue(paged.size() >= 30,
                () -> "expected the paged responses, found " + paged.size());

        List<String> violations = new ArrayList<>();
        for (Path response : paged) {
            List<String> components = components(read(response));
            REQUIRED_FIELDS.stream()
                    .filter(field -> !components.contains(field))
                    .forEach(field -> violations.add(
                            response.getFileName() + " is missing '" + field + "'; paged responses"
                                    + " carry " + REQUIRED_FIELDS));
            REJECTED_FIELDS.stream()
                    .filter(components::contains)
                    .forEach(field -> violations.add(
                            response.getFileName() + " publishes '" + field + "'; use the shared"
                                    + " name instead so one caller shape fits every page"));
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    /**
     * A paged response is one that carries a list and at least one of the page numbers, so a
     * response using entirely different names is still caught rather than quietly excluded.
     */
    private static List<Path> pagedResponses() {
        try (Stream<Path> files = Files.list(RESPONSES)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith("PageResponse.java"))
                    .filter(path -> components(read(path)).stream().anyMatch(field ->
                            REQUIRED_FIELDS.contains(field) || REJECTED_FIELDS.contains(field)))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<String> components(String source) {
        List<String> names = new ArrayList<>();
        Matcher component = COMPONENT.matcher(source);
        while (component.find()) {
            names.add(component.group(1));
        }
        return names;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
