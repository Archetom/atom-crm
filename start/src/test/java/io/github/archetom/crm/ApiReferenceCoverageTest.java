package io.github.archetom.crm;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the published route reference from falling further behind the controllers.
 *
 * <p>{@code docs/api-reference.md} is a route table, and it is what a caller reads to learn an
 * endpoint's authority and semantics. A route that never reaches it is reachable but undescribed,
 * and nothing today notices when a new one is added.</p>
 *
 * <p>A number of routes are already missing. Rather than assert zero and leave a permanently red
 * test, this holds the current count as a ceiling: the gap may shrink, never grow. Documenting the
 * remaining routes needs each one's authority and semantics, which is a writing task rather than a
 * mechanical one, so the ceiling is expected to be lowered deliberately over time.</p>
 */
class ApiReferenceCoverageTest {

    private static final Path CONTROLLERS =
            Path.of("../infra/rest/src/main/java/io/github/archetom/crm/infra/rest/controller");
    private static final Path REFERENCE = Path.of("../docs/api-reference.md");

    /** Lower this as routes are documented; never raise it to accommodate a new undocumented one. */
    private static final int UNDOCUMENTED_ROUTE_CEILING = 86;

    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Delete|Patch)Mapping\\((?:value\\s*=\\s*)?\"?([^\",)]*)\"?");
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]*}");

    @Test
    void theUndocumentedRouteGapDoesNotGrow() {
        Set<String> routes = declaredRoutes();
        assertTrue(routes.size() > 300,
                () -> "expected the controller routes, found " + routes.size());

        String reference = PATH_VARIABLE.matcher(read(REFERENCE)).replaceAll("{}");
        List<String> undocumented = routes.stream()
                .filter(route -> !reference.contains(route))
                .sorted()
                .toList();

        assertTrue(undocumented.size() <= UNDOCUMENTED_ROUTE_CEILING,
                () -> "routes missing from docs/api-reference.md grew from "
                        + UNDOCUMENTED_ROUTE_CEILING + " to " + undocumented.size()
                        + "; document the new route instead of raising the ceiling:\n"
                        + String.join("\n", undocumented));
    }

    private static Set<String> declaredRoutes() {
        Set<String> routes = new LinkedHashSet<>();
        for (Path controller : controllers()) {
            String source = read(controller);
            Matcher classMapping = CLASS_MAPPING.matcher(source);
            String base = classMapping.find() ? classMapping.group(1) : "";
            Matcher methodMapping = METHOD_MAPPING.matcher(source);
            while (methodMapping.find()) {
                String path = methodMapping.group(2);
                String full = path.isEmpty() ? base : path.startsWith("/") ? base + path : base + "/" + path;
                routes.add(PATH_VARIABLE.matcher(full).replaceAll("{}"));
            }
        }
        return routes;
    }

    private static List<Path> controllers() {
        if (!Files.isDirectory(CONTROLLERS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            return new ArrayList<>(files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .sorted()
                    .toList());
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
