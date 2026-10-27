package io.github.archetom.crm.application.transaction;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfterCommitExecutorTest {

    private final AfterCommitExecutor executor = new AfterCommitExecutor();

    @AfterEach
    void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void defersActionUntilCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean executed = new AtomicBoolean();

        executor.execute("test action", () -> executed.set(true));

        assertFalse(executed.get());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());
        assertTrue(executed.get());
    }

    @Test
    void runsImmediatelyWithoutTransaction() {
        AtomicBoolean executed = new AtomicBoolean();

        executor.execute("test action", () -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    void isolatesFailuresBetweenPostCommitActions() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean secondActionExecuted = new AtomicBoolean();

        executor.execute("test action", () -> {
            throw new IllegalStateException("simulated side-effect failure");
        });
        executor.execute("test action", () -> secondActionExecuted.set(true));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCommit());

        assertTrue(secondActionExecuted.get());
    }

    /**
     * A post-commit failure cannot fail the business transaction, so the log is the only signal it
     * ever produces. An unlabelled one says a side effect failed but not which, and a lost
     * password-reset email needs a different response from a skipped cache invalidation.
     */
    @Test
    void aFailingActionIsNamedRatherThanSwallowedAnonymously() {
        List<String> logged = new ArrayList<>();
        Logger logger = (Logger) LoggerFactory.getLogger(AfterCommitExecutor.class);
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                logged.add(event.getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            executor.execute("password reset email delivery", () -> {
                throw new IllegalStateException("smtp is unavailable");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(1, logged.size(), () -> "expected one failure log, got " + logged);
        assertTrue(logged.getFirst().contains("password reset email delivery"),
                () -> "the failure must name the action, was: " + logged.getFirst());
    }

    @Test
    void anUnnamedActionIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(null, () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute("  ", () -> { }));
    }
}
