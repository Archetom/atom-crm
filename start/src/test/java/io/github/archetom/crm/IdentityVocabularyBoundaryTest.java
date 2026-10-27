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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two words for a person stay on their own sides of the persistence boundary.
 *
 * <p>A person is {@code member} in the domain and {@code actor} in storage. Two vocabularies for one
 * concept is already a cost, but the containable part is that each side uses exactly one of them:
 * every translation then happens in a converter, where it is visible. If {@code actorId} starts
 * appearing on domain entities, or {@code memberId} on persistent objects, the translation stops
 * being a boundary and becomes a per-field decision spread across the codebase.</p>
 *
 * <p>What this cannot check is the translation itself: both sides are {@code Long}, so a converter
 * that writes the creator's identity into the assignee's column compiles and runs. That risk is
 * covered where it lives, by the converter round-trip tests, which give every field a value unique
 * to itself and so surface a swap by name.</p>
 */
class IdentityVocabularyBoundaryTest {

    private static final Path DOMAIN = Path.of("../domain/src/main/java");
    private static final Path PERSISTENT_OBJECTS = Path.of(
            "../infra/persistence/src/main/java/io/github/archetom/crm/infra/persistence/mysql/po");

    /** An identity field, not a word that merely starts with the same root. */
    private static final Pattern DOMAIN_STORAGE_WORD =
            Pattern.compile("private\\s+[\\w<>]+\\s+(actor(?:Id|MemberId|ActorId)\\w*)\\s*[;=]");
    private static final Pattern STORAGE_DOMAIN_WORD =
            Pattern.compile("private\\s+[\\w<>]+\\s+(member(?:Id|ActorId)\\w*)\\s*[;=]");

    @Test
    void theDomainNamesAPersonMemberRatherThanActor() {
        List<String> leaks = matchesIn(DOMAIN, DOMAIN_STORAGE_WORD);
        assertTrue(leaks.isEmpty(),
                () -> "the storage word for a person reached the domain model:\n  "
                        + String.join("\n  ", leaks)
                        + "\ndomain identities are MemberId; translate in the converter instead");
    }

    @Test
    void persistentObjectsNameAPersonActorRatherThanMember() {
        List<String> leaks = matchesIn(PERSISTENT_OBJECTS, STORAGE_DOMAIN_WORD);
        assertTrue(leaks.isEmpty(),
                () -> "the domain word for a person reached a persistent object:\n  "
                        + String.join("\n  ", leaks)
                        + "\ncolumns are actor_id; translate in the converter instead");
    }

    /** Guards the extractor: a rule that scanned nothing would pass both assertions above. */
    @Test
    void bothSourceTreesAreActuallyScanned() {
        assertTrue(sources(DOMAIN).size() > 100,
                () -> "expected the domain sources, found " + sources(DOMAIN).size());
        assertTrue(sources(PERSISTENT_OBJECTS).size() > 50,
                () -> "expected the persistent objects, found " + sources(PERSISTENT_OBJECTS).size());
    }

    private static List<String> matchesIn(Path root, Pattern pattern) {
        List<String> found = new ArrayList<>();
        for (Path source : sources(root)) {
            Matcher matcher = pattern.matcher(read(source));
            while (matcher.find()) {
                found.add(source.getFileName() + " declares '" + matcher.group(1) + "'");
            }
        }
        return found;
    }

    private static List<Path> sources(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
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
