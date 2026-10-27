package io.github.archetom.crm;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deprecated route is a migration shim with an end date, not a second supported surface.
 *
 * <p>Six stage-workflow record routes are duplicated under the workflow collection they used to
 * live in. They are deliberate and each names its replacement, so they are not a defect — but
 * nothing stopped a seventh from appearing, and nothing recorded when any of them go away. A shim
 * with no end date is how a temporary duplicate becomes permanent.</p>
 *
 * <p>This holds the inventory: every deprecated route must be listed in
 * {@code docs/api-reference.md} with its replacement, and no route may be deprecated without being
 * listed. Retiring one is then a deliberate edit in both places rather than silent drift.</p>
 */
class DeprecatedRouteRetirementTest {

    private static final Path CONTROLLERS =
            Path.of("../infra/rest/src/main/java/io/github/archetom/crm/infra/rest/controller");
    private static final Path REFERENCE = Path.of("../docs/api-reference.md");

    /** Lower this as routes are retired; never raise it to admit a new deprecated duplicate. */
    private static final int DEPRECATED_ROUTE_CEILING = 6;

    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
    private static final Pattern METHOD_MAPPING =
            Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping\\(\"?([^\"\\n)]*)\"?\\)");

    @Test
    void everyDeprecatedRouteIsListedWithItsReplacement() {
        List<String> deprecated = deprecatedRoutes();
        String reference = read(REFERENCE);

        List<String> undocumented = deprecated.stream()
                .filter(route -> !reference.contains(route))
                .toList();
        assertTrue(undocumented.isEmpty(),
                () -> "deprecated routes missing from the reference's retirement table: "
                        + undocumented);
    }

    /** The reference must name a retirement version, or "deprecated" means "forever". */
    @Test
    void theReferenceStatesWhenTheShimIsRemoved() {
        String reference = read(REFERENCE);
        assertTrue(reference.contains("## Deprecated routes"),
                "docs/api-reference.md must carry the deprecated-route inventory");
        assertTrue(Pattern.compile("removed\\s+in\\s+\\d+\\.\\d+\\.\\d+").matcher(reference).find(),
                "the deprecated-route section must name the release that removes the shim");
    }

    /**
     * Pins the deprecated surface exactly.  An equality also guards the extractor itself: a rule
     * that silently found nothing would otherwise pass every assertion above.
     */
    @Test
    void theDeprecatedRoutesAreFound() {
        List<String> deprecated = deprecatedRoutes();
        assertEquals(DEPRECATED_ROUTE_CEILING, deprecated.size(),
                () -> "expected the known deprecated routes, found " + deprecated.size()
                        + "; add the replacement route instead of a duplicate, and if one was"
                        + " retired lower the ceiling and drop its row from docs/api-reference.md:\n"
                        + String.join("\n", deprecated));
    }

    private static List<String> deprecatedRoutes() {
        List<String> routes = new ArrayList<>();
        for (Path controller : controllers()) {
            String source = read(controller);
            Matcher classMapping = CLASS_MAPPING.matcher(source);
            String base = classMapping.find() ? classMapping.group(1) : "";
            Matcher methodMapping = METHOD_MAPPING.matcher(source);
            while (methodMapping.find()) {
                if (!isDeprecated(source, methodMapping.start())) {
                    continue;
                }
                String path = methodMapping.group(2);
                routes.add(methodMapping.group(1).toUpperCase(java.util.Locale.ROOT) + " "
                        + (path.isEmpty() ? base : base + path));
            }
        }
        return routes;
    }

    /** The annotation block runs from the mapping to the handler signature that follows it. */
    private static boolean isDeprecated(String source, int mappingStart) {
        int handler = source.indexOf("public ", mappingStart);
        String block = source.substring(mappingStart, handler < 0 ? source.length() : handler);
        return block.contains("@Deprecated") || block.contains("deprecated = true");
    }

    private static List<Path> controllers() {
        if (!Files.isDirectory(CONTROLLERS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            return files.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .sorted().toList();
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
