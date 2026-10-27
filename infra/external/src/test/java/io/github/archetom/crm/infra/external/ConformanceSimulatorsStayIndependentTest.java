package io.github.archetom.crm.infra.external;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conformance fixtures describe the outside world without borrowing our description of it.
 *
 * <p>Three of the largest test assets in the repository — around 3,700 lines between them — exist to
 * stand in for things we do not control: a composed safe-file scenario, an official source's HTTP
 * security behaviour, and its durability behaviour. They are worth their size only while they are
 * an <em>independent</em> statement of what those systems do.</p>
 *
 * <p>The moment one of them takes a production class, it stops being independent. A simulator built
 * from the adapter's own constants, enums or parsing agrees with that adapter by construction: the
 * conformance test then passes because both sides made the same assumption, which is precisely the
 * case it was written to catch. Nothing fails, the suite stays green, and the check has quietly
 * become circular.</p>
 *
 * <p>The compiler cannot object — production and test sources are separate source sets, and a test
 * may use production freely. So the rule is stated here instead, and it is stated against the
 * source sets themselves. A package-name allow-list would have been a second place to keep in step
 * with the tree: the day a production class lands in a package the list calls test-only, borrowing
 * it becomes invisible. Whether a name belongs to test code is instead answered by looking for the
 * file, so the answer moves with the tree.</p>
 *
 * <p>For the same reason the scan reads the whole file rather than its import list. An import is
 * only the tidiest way to reach production code; a fully-qualified name written inline reaches it
 * just as well, and a class name in a string literal reaches it by reflection. Every mention of our
 * own package space counts — including one in a comment, which means the fixture is already
 * describing itself in terms of the code it is supposed to check independently.</p>
 */
class ConformanceSimulatorsStayIndependentTest {

    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");

    /** The assets whose value depends on being written independently of the adapter under test. */
    private static final List<String> SIMULATORS = List.of(
            "SafeFileComposedFixture.java",
            "OfficialSourceHttpSecuritySimulator.java",
            "OfficialSourceReadinessDurableSimulator.java");

    /** Our own package space, however it is written: import, inline name, or string literal. */
    private static final Pattern OWN_REFERENCE =
            Pattern.compile("\\bio\\.github\\.archetom\\.crm(?:\\.[A-Za-z_$][\\w$]*)+");

    @Test
    void noConformanceSimulatorTakesAProductionClass() {
        List<Path> found = simulators();
        assertEquals(SIMULATORS.size(), found.size(),
                () -> "expected " + SIMULATORS + ", found " + found);

        List<String> borrowed = new ArrayList<>();
        for (Path simulator : found) {
            Matcher referenced = OWN_REFERENCE.matcher(read(simulator));
            while (referenced.find()) {
                String reference = referenced.group();
                if (resolvesUnder(TEST_SOURCES, reference)) {
                    continue;
                }
                borrowed.add(simulator.getFileName() + " names " + reference
                        + (resolvesUnder(PRODUCTION_SOURCES, reference)
                        ? ", which is production code"
                        : ", which is not a test source in this module"));
            }
        }
        assertTrue(borrowed.isEmpty(),
                () -> "a conformance fixture took its description from the code it checks, which"
                        + " makes the check agree with itself:\n  " + String.join("\n  ", borrowed));
    }

    /**
     * The check above passes when nothing resolves outside the test sources — and it would also
     * pass if the resolver answered "test source" to everything, which is what a wrong source root
     * or a broken prefix walk would do. Both directions are therefore asserted against real files
     * picked out of the tree, so the separation is demonstrated rather than assumed.
     */
    @Test
    void theResolverSeparatesTheTwoSourceSets() {
        String productionType = anyTypeUnder(PRODUCTION_SOURCES);
        String testType = anyTypeUnder(TEST_SOURCES);

        assertTrue(resolvesUnder(PRODUCTION_SOURCES, productionType),
                () -> productionType + " is a production source and did not resolve as one");
        assertFalse(resolvesUnder(TEST_SOURCES, productionType),
                () -> productionType + " is production code but resolved as a test source");
        assertTrue(resolvesUnder(TEST_SOURCES, testType),
                () -> testType + " is a test source and did not resolve as one");
        assertFalse(resolvesUnder(PRODUCTION_SOURCES, testType),
                () -> testType + " is a test source but resolved as production code");
    }

    /**
     * True when the dotted name reaches a source file in this set. Prefixes are tried because a
     * reference may continue past the type into a nested type or a member, and the bare package is
     * accepted as a directory so that a file's own {@code package} line resolves to where it sits.
     */
    private static boolean resolvesUnder(Path sourceRoot, String reference) {
        StringBuilder path = new StringBuilder();
        for (String segment : reference.split("\\.")) {
            if (!path.isEmpty()) {
                path.append('/');
            }
            path.append(segment);
            if (Files.isRegularFile(sourceRoot.resolve(path + ".java"))) {
                return true;
            }
        }
        return Files.isDirectory(sourceRoot.resolve(reference.replace('.', '/')));
    }

    /** The fully-qualified name of some type in this source set, used to exercise the resolver. */
    private static String anyTypeUnder(Path sourceRoot) {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .map(path -> sourceRoot.relativize(path).toString())
                    .map(name -> name.substring(0, name.length() - ".java".length())
                            .replace('/', '.'))
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no sources under " + sourceRoot));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<Path> simulators() {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            files.filter(path -> SIMULATORS.contains(path.getFileName().toString()))
                    .sorted()
                    .forEach(found::add);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return found;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
