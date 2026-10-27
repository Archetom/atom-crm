package io.github.archetom.crm.infra.persistence.support;

import java.util.Locale;

/**
 * Recognises which database constraint a failed write violated.
 *
 * <p>The driver reports the constraint only inside the exception message, so a repository that
 * wants to turn one specific violation into a domain rejection has to look there. Keeping that
 * search in one place means the traversal — case handling, self-referencing causes — is written
 * once rather than copied into every repository that needs it.</p>
 *
 * <p>This is message matching and therefore inherently fragile: a driver upgrade or a server
 * locale that changes the wording makes the match stop succeeding, and the caller then rethrows
 * the original exception instead of its domain rejection. That is a safe direction to fail in,
 * but it is the reason no invariant should depend on this returning true.</p>
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** Whether any cause in the chain names the given constraint. */
    public static boolean causeContains(Throwable exception, String constraintName) {
        String expected = constraintName.toLowerCase(Locale.ROOT);
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }
}
