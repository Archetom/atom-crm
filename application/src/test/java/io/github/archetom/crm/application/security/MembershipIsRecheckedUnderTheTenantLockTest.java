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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Once the tenant root is locked, membership is re-read under a lock rather than from the snapshot.
 *
 * <p>Command transactions run at REPEATABLE_READ. A membership check performed before the tenant
 * lock — or performed after it with a plain read — can be answered from the snapshot taken earlier
 * in the transaction, which still shows a member who has since been deactivated. The operation then
 * proceeds on behalf of someone who no longer has access, and nothing about it looks wrong:
 * the check is there, it ran, and it returned true.</p>
 *
 * <p>{@code OrganizationAccessGuard} says exactly this — "callers holding the tenant root lock must
 * follow this with requireActiveMembershipForShare" — but the rule lived only in a comment, and two
 * operations did not follow it. This turns the comment into a check: any method that acquires the
 * tenant root lock and then decides on membership must use a locking read.</p>
 *
 * <p>The rule is deliberately narrow. The great majority of membership checks hold no tenant lock
 * and are correct with a plain read; requiring a locking read everywhere would be both wrong and a
 * gate nobody could act on.</p>
 */
class MembershipIsRecheckedUnderTheTenantLockTest {

    private static final Path APPLICATION = Path.of("src/main/java/io/github/archetom/crm/application");

    private static final List<String> TENANT_LOCKS =
            List.of("lockForTenantChanges(", "lockForStructureChanges(");

    /** Reads that see the current row rather than the transaction's snapshot. */
    private static final List<String> LOCKING_READS =
            List.of("requireActiveMembershipForShare(", "findByMemberForShare(",
                    "activeMembershipForShare(");

    /** Membership decisions that are answered from the snapshot. */
    private static final List<String> SNAPSHOT_READS =
            List.of("requireActiveMembership(", "findByMember(", "activeMembership(");

    private static final Pattern METHOD = Pattern.compile(
            "\\n    (?:public|private|protected)[\\w<>,.\\[\\]? ]+ (\\w+)\\([^)]*\\)[^{;]*\\{");

    @Test
    void aMethodHoldingTheTenantLockRereadsMembershipUnderALock() {
        List<Path> sources = sources();
        assertTrue(sources.size() > 50, () -> "expected the application sources, found " + sources.size());

        List<String> violations = new ArrayList<>();
        int guarded = 0;
        for (Path source : sources) {
            String text = read(source);
            if (TENANT_LOCKS.stream().noneMatch(text::contains)) {
                continue;
            }
            Matcher method = METHOD.matcher(text);
            while (method.find()) {
                String body = block(text, method.end());
                if (TENANT_LOCKS.stream().noneMatch(body::contains)) {
                    continue;
                }
                boolean locking = LOCKING_READS.stream().anyMatch(body::contains);
                boolean snapshot = SNAPSHOT_READS.stream().anyMatch(body::contains);
                if (locking) {
                    guarded++;
                } else if (snapshot) {
                    violations.add(source.getFileName() + "#" + method.group(1)
                            + " holds the tenant lock but decides membership from the snapshot;"
                            + " re-read with the ForShare variant after the lock");
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
        int checked = guarded;
        assertTrue(checked > 0,
                () -> "no lock-holding method was found to check, so this rule proved nothing");
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
