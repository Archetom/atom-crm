package io.github.archetom.crm.infra.persistence;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three record repositories keep the operations they have in common.
 *
 * <p>Customer, Contact and Lead are modelled almost identically, and their persistence adapters
 * followed: around 1,200 lines each, with 56 method names present in all three. That duplication is
 * not something a test can undo — the aggregates are genuinely parallel and the adapters were
 * written that way deliberately.</p>
 *
 * <p>What a test can do is notice when they stop being parallel. The danger with three copies is
 * not that they exist, it is that a change lands in one and not the others: a visibility predicate
 * tightened for Customer but not for Contact, a batch projection added for Lead alone. The
 * aggregates then quietly behave differently in ways no compiler or per-aggregate test would
 * report, because each one is self-consistent.</p>
 *
 * <p>So this pins the shared surface: an operation that all three offer today must keep being
 * offered by all three. Adding one to a single aggregate is fine — aggregates do differ, and the
 * exclusive methods are left alone. Removing one from a single aggregate, or renaming it in one
 * place, is the drift this catches.</p>
 */
class RecordRepositoryParityTest {

    private static final Path REPOSITORIES = Path.of(
            "src/main/java/io/github/archetom/crm/infra/persistence/repository");

    private static final List<String> ADAPTERS = List.of(
            "CustomerRepositoryImpl", "ContactRepositoryImpl", "LeadRepositoryImpl");

    /**
     * Operations all three shared when this was written. Lower it only by removing an operation
     * from all three together; never by dropping a name because one adapter stopped offering it.
     */
    private static final int SHARED_OPERATION_FLOOR = 56;

    private static final Pattern METHOD = Pattern.compile(
            "\\n    (?:public|private)(?: static)?(?: <[^>]+>)? [\\w<>,.\\[\\]? ]+ (\\w+)\\(");

    @Test
    void everyOperationSharedByAllThreeStaysSharedByAllThree() {
        List<Set<String>> surfaces = ADAPTERS.stream().map(RecordRepositoryParityTest::methods).toList();
        surfaces.forEach(surface -> assertTrue(surface.size() > 40,
                () -> "expected a record repository surface, found " + surface.size()));

        Set<String> shared = new LinkedHashSet<>(surfaces.getFirst());
        surfaces.forEach(shared::retainAll);

        assertTrue(shared.size() >= SHARED_OPERATION_FLOOR,
                () -> "the three record repositories shared " + SHARED_OPERATION_FLOOR
                        + " operations and now share only " + shared.size()
                        + "; an operation was changed in one aggregate without the others, which is"
                        + " how they start behaving differently:\n  " + missingByAdapter(surfaces));
    }

    /** Names the adapters that lost a formerly-shared operation, so the failure is actionable. */
    private static String missingByAdapter(List<Set<String>> surfaces) {
        Set<String> union = new LinkedHashSet<>();
        surfaces.forEach(union::addAll);
        List<String> report = new ArrayList<>();
        for (String operation : union) {
            List<String> missing = new ArrayList<>();
            for (int index = 0; index < ADAPTERS.size(); index++) {
                if (!surfaces.get(index).contains(operation)) {
                    missing.add(ADAPTERS.get(index));
                }
            }
            if (!missing.isEmpty() && missing.size() < ADAPTERS.size()) {
                report.add(operation + " is absent from " + missing);
            }
        }
        return String.join("\n  ", report);
    }

    private static Set<String> methods(String adapter) {
        Set<String> names = new LinkedHashSet<>();
        Matcher method = METHOD.matcher(read(REPOSITORIES.resolve(adapter + ".java")));
        while (method.find()) {
            names.add(method.group(1));
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
