package io.github.archetom.crm.application.service.template;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Minimal transaction boundary for application service unit tests.  {@link #active()} reports
 * whether a transaction is currently open, so a test can prove that a port was consulted inside
 * or outside the boundary instead of asserting on a flag this manager never sets.
 */
public final class TestTransactionManager implements PlatformTransactionManager {

    private boolean active;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        active = true;
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        active = false;
    }

    @Override
    public void rollback(TransactionStatus status) {
        active = false;
    }

    public boolean active() {
        return active;
    }
}
