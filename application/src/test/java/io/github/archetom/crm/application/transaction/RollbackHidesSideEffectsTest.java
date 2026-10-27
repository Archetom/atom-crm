package io.github.archetom.crm.application.transaction;

import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.shared.operation.OperationCode;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the boundary stated in AGENTS.md: "A rollback must not expose cache or event side
 * effects."
 *
 * <p>The existing template test supplies a mocked transaction manager, so no synchronization is
 * ever bound and a registered side effect cannot be observed either firing or not firing. This
 * drives a real {@link AbstractPlatformTransactionManager} instead — the same class Spring uses to
 * trigger after-commit and after-completion callbacks — so the invariant is actually exercised
 * without needing a database.</p>
 */
class RollbackHidesSideEffectsTest {

    private final AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final CommandServiceTemplate template =
            new CommandServiceTemplate("test-app", transactionManager);

    /** Stands in for the cache mutations and event publications that ride the post-commit hook. */
    private final List<String> sideEffects = new ArrayList<>();

    @AfterEach
    void clearBoundSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void aCommittedCommandExposesItsCacheAndEventSideEffects() {
        Result<String> result = template.execute(TestOperation.CREATE, new ServiceOperation<>() {
            @Override
            public String execute() {
                afterCommitExecutor.execute("cache mutation", () -> sideEffects.add("cache-mutation"));
                afterCommitExecutor.execute("event publication", () -> sideEffects.add("event-publication"));
                return "created";
            }
        });

        assertTrue(result.isSuccess());
        assertEquals(List.of("commit"), transactionManager.outcomes);
        assertEquals(List.of("cache-mutation", "event-publication"), sideEffects);
    }

    @Test
    void aRolledBackCommandExposesNoCacheOrEventSideEffect() {
        Result<String> result = template.execute(TestOperation.CREATE, new ServiceOperation<>() {
            @Override
            public String execute() {
                afterCommitExecutor.execute("cache mutation", () -> sideEffects.add("cache-mutation"));
                afterCommitExecutor.execute("event publication", () -> sideEffects.add("event-publication"));
                throw new IllegalStateException("the command failed after registering side effects");
            }
        });

        assertFalse(result.isSuccess());
        assertEquals(List.of("rollback"), transactionManager.outcomes);
        assertTrue(sideEffects.isEmpty(),
                () -> "a rollback exposed side effects: " + sideEffects);
    }

    @Test
    void sideEffectsRegisteredInOnSuccessAlsoStayHiddenWhenTheCommitFails() {
        transactionManager.failCommit = true;

        Result<String> result = template.execute(TestOperation.CREATE, new ServiceOperation<>() {
            @Override
            public String execute() {
                return "created";
            }

            @Override
            public void onSuccess(String data) {
                afterCommitExecutor.execute("cache mutation", () -> sideEffects.add("cache-mutation"));
            }
        });

        assertFalse(result.isSuccess());
        assertTrue(sideEffects.isEmpty(),
                () -> "a failed commit exposed side effects: " + sideEffects);
    }

    /**
     * Minimal manager that owns no resource but keeps Spring's real synchronization lifecycle, so
     * after-commit and after-completion callbacks fire exactly as they would against a datasource.
     */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final List<String> outcomes = new ArrayList<>();
        private boolean failCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // No resource to bind; synchronization is prepared by the superclass.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) {
                throw new IllegalStateException("simulated commit failure");
            }
            outcomes.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            outcomes.add("rollback");
        }
    }

    private enum TestOperation implements OperationCode {
        CREATE;

        @Override
        public String code() {
            return name();
        }
    }
}
