package io.github.archetom.crm.application.security;

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
 * A caller without the required authority must be rejected on that ground alone, whatever the
 * request body looks like. When request-shape validation runs first, the same unauthorized caller
 * receives a parameter error for a malformed body and an access error for a well-formed one — a
 * difference that confirms the endpoint exists and that the body was understood, and that makes
 * sibling endpoints disagree on the error contract for identical input.
 *
 * <p>Every authority resolution in these services derives its scope from the verified caller
 * alone, so it can always run first.</p>
 */
class AuthorityPrecedesRequestValidationTest {

    private static final Path SERVICES =
            Path.of("src/main/java/io/github/archetom/crm/application/service/impl");

    private static final Pattern VALIDATE_BODY =
            Pattern.compile("public void validate\\(\\)\\s*\\{(.*?)\\n\\s*\\}", Pattern.DOTALL);

    private static final Pattern REQUEST_CHECK =
            Pattern.compile("\\b(?:require|requireRequest)\\(request\\b");

    private static final Pattern AUTHORITY_CHECK =
            Pattern.compile("(?:accessResolver|\\w*AccessResolver)\\.access\\(");

    @Test
    void authorityIsResolvedBeforeTheRequestShapeIsValidated() {
        List<String> inversions = new ArrayList<>();
        List<String> scanned = new ArrayList<>();

        for (Path service : commandServices()) {
            String source = read(service);
            Matcher validate = VALIDATE_BODY.matcher(source);
            while (validate.find()) {
                String body = validate.group(1);
                Matcher request = REQUEST_CHECK.matcher(body);
                Matcher authority = AUTHORITY_CHECK.matcher(body);
                if (!request.find() || !authority.find()) {
                    continue;
                }
                String location = service.getFileName() + ":"
                        + (source.substring(0, validate.start()).split("\n", -1).length);
                scanned.add(location);
                if (request.start() < authority.start()) {
                    inversions.add(location + " validates the request before resolving authority");
                }
            }
        }

        assertFalse(scanned.isEmpty(), "no command-service validate() blocks were scanned");
        assertTrue(inversions.isEmpty(),
                () -> "Unauthorized callers can distinguish a malformed body from a denied"
                        + " authority at:\n" + String.join("\n", inversions));
    }

    private static List<Path> commandServices() {
        try (Stream<Path> files = Files.list(SERVICES)) {
            return files.filter(path -> path.getFileName().toString().endsWith("CommandServiceImpl.java"))
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
