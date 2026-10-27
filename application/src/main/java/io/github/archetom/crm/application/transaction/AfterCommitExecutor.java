package io.github.archetom.crm.application.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects only after the surrounding transaction has committed.
 *
 * <p>A failure here cannot fail the business transaction — the commit has already happened — so it
 * is recorded rather than propagated. That makes the log the only signal these effects ever produce,
 * and a log that says only "a post-commit action failed" cannot be acted on: a lost password-reset
 * email and a skipped cache invalidation need different responses. Every registration therefore
 * names what it does, and the name is what appears when it fails.</p>
 */
@Slf4j
@Component
public class AfterCommitExecutor {

    /**
     * @param description what the effect is, in a few words, as it should appear in the failure
     *                    log — for example {@code "password reset email delivery"}
     */
    public void execute(String description, Runnable action) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            runSafely(description, action);
                        }
                    });
            return;
        }

        runSafely(description, action);
    }

    private void runSafely(String description, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            // The database commit has already succeeded. Record the failure for
            // retry/alerting instead of reporting the business transaction as failed.
            log.error("Post-commit action failed: action={}, exceptionType={}",
                    description, exception.getClass().getName());
        }
    }
}
