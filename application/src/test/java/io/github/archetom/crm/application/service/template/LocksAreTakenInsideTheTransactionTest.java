package io.github.archetom.crm.application.service.template;

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
 * A lock acquired before the transaction opens protects nothing.
 *
 * <p>{@code OperationTemplateSupport} runs {@code validate()} and {@code prepare()} and only then
 * opens the transaction. A {@code SELECT ... FOR UPDATE} and the tenant lock both hold only for the
 * duration of the current transaction, so taking either in those phases releases it immediately —
 * the work that follows runs unserialized while the code reads as though it is guarded. Nothing
 * fails and no test goes red; the guarantee is simply absent.</p>
 *
 * <p>The check follows one level of indirection inside the same class, because that is the shape
 * the mistake took: the CRM import confirm and withdraw operations called a private
 * {@code current(...)} helper from {@code validate()}, and the locking reads were inside it. A rule
 * that only read the phase body would have passed them.</p>
 *
 * <p>It also distinguishes an unconditional lock from one a caller selects with a flag. Several
 * helpers here take a {@code boolean lock} and choose between a locking and a plain read; calling
 * one of those with {@code false} acquires nothing and is fine. Reporting those would make this a
 * gate nobody can act on, which is worse than having no gate.</p>
 */
class LocksAreTakenInsideTheTransactionTest {

    private static final Path APPLICATION = Path.of("src/main/java/io/github/archetom/crm/application");

    /** Calls whose effect lasts exactly as long as the surrounding transaction. */
    private static final List<String> LOCKING_CALLS = List.of(
            "ForUpdate(", "lockForTenantChanges(", "lockForStructureChanges(", "objectLock.acquire(");

    private static final Pattern PHASE =
            Pattern.compile("public void (validate|prepare)\\(\\)\\s*\\{");
    private static final Pattern OWN_CALL = Pattern.compile("(?<![.\\w])(\\w+)\\s*\\(");

    @Test
    void noPreTransactionPhaseAcquiresALock() {
        List<Path> sources = sources();
        assertTrue(sources.size() > 50, () -> "expected the application sources, found " + sources.size());

        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            String text = read(source);
            Matcher phase = PHASE.matcher(text);
            while (phase.find()) {
                inspectPhase(source, text, phase.group(1), phase.end(), violations);
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static void inspectPhase(Path source, String text, String phaseName,
                                     int bodyStart, List<String> violations) {
        String body = block(text, bodyStart);
        String where = source.getFileName() + " " + phaseName + "()";

        unconditionalLocks(body).forEach(call -> violations.add(
                where + " acquires " + call + " before the transaction opens;"
                        + " move the read into execute()"));

        Set<String> visited = new LinkedHashSet<>();
        Matcher call = OWN_CALL.matcher(body);
        while (call.find()) {
            String name = call.group(1);
            if (!visited.add(name)) {
                continue;
            }
            Matcher declaration = Pattern.compile(
                    "(?:private|protected)\\s[\\w<>,.\\[\\]\\s]*\\b" + Pattern.quote(name)
                            + "\\s*\\([^)]*\\)\\s*\\{").matcher(text);
            if (!declaration.find()) {
                continue;
            }
            String helper = block(text, declaration.end());
            unconditionalLocks(helper).forEach(locking -> violations.add(
                    where + " calls " + name + "(), which always acquires " + locking
                            + " before the transaction opens; move the read into execute()"));
            if (!conditionalLocks(helper).isEmpty() && selectsLocking(body, name)) {
                violations.add(where + " calls " + name + "(..., true), which acquires a lock"
                        + " before the transaction opens; move the read into execute()");
            }
        }
    }

    /** Locking calls that run whatever the arguments say, because no ternary selects them. */
    private static List<String> unconditionalLocks(String body) {
        return LOCKING_CALLS.stream()
                .filter(call -> occurrences(body, call).stream().anyMatch(index ->
                        !statementAround(body, index).contains("?")))
                .toList();
    }

    private static List<String> conditionalLocks(String body) {
        return LOCKING_CALLS.stream()
                .filter(call -> occurrences(body, call).stream().anyMatch(index ->
                        statementAround(body, index).contains("?")))
                .toList();
    }

    /** Whether the phase passes a literal {@code true} to the flag-selected helper. */
    private static boolean selectsLocking(String body, String name) {
        Matcher invocation = Pattern.compile(
                "(?<![.\\w])" + Pattern.quote(name) + "\\s*\\(([^;]*?)\\)").matcher(
                        body.replaceAll("\\s+", " "));
        while (invocation.find()) {
            if (Pattern.compile("(?<![.\\w])true(?![\\w])").matcher(invocation.group(1)).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> occurrences(String body, String needle) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = body.indexOf(needle); index >= 0; index = body.indexOf(needle, index + 1)) {
            indexes.add(index);
        }
        return indexes;
    }

    private static String statementAround(String body, int index) {
        int start = Math.max(Math.max(body.lastIndexOf(';', index), body.lastIndexOf('{', index)),
                body.lastIndexOf('}', index)) + 1;
        int end = body.indexOf(';', index);
        return body.substring(start, end < 0 ? body.length() : end);
    }

    private static String block(String text, int afterOpeningBrace) {
        int depth = 1;
        int index = afterOpeningBrace;
        while (index < text.length() && depth > 0) {
            char character = text.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
            }
            index++;
        }
        return text.substring(afterOpeningBrace, Math.max(afterOpeningBrace, index - 1));
    }

    private static List<Path> sources() {
        try (Stream<Path> files = Files.walk(APPLICATION)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
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
