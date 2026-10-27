package io.github.archetom.crm.application.service.template;

import io.github.archetom.crm.shared.operation.OperationCode;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandServiceTemplateTest {

    @Test
    void preparesBeforeOpeningIndependentCommandTransaction() {
        List<String> lifecycle = new ArrayList<>();
        PlatformTransactionManager transactionManager = transactionManager(lifecycle);
        CommandServiceTemplate template = new CommandServiceTemplate("test-app", transactionManager);

        Result<String> result = template.execute(TestOperation.CREATE, new ServiceOperation<>() {
            @Override
            public void validate() {
                lifecycle.add("validate");
            }

            @Override
            public void prepare() {
                lifecycle.add("prepare");
            }

            @Override
            public String execute() {
                lifecycle.add("execute");
                return "created";
            }

            @Override
            public void onSuccess(String ignored) {
                lifecycle.add("onSuccess");
            }
        });

        assertTrue(result.isSuccess());
        assertEquals("created", result.getData());
        assertEquals(List.of("validate", "prepare", "begin", "execute", "onSuccess", "commit"), lifecycle);
    }

    @Test
    void rollsBackBeforeConvertingCommandFailureToResult() {
        List<String> lifecycle = new ArrayList<>();
        PlatformTransactionManager transactionManager = transactionManager(lifecycle);
        CommandServiceTemplate template = new CommandServiceTemplate("test-app", transactionManager);

        Result<String> result = template.execute(TestOperation.CREATE, new ServiceOperation<>() {
            @Override
            public String execute() {
                throw new IllegalStateException("write failed");
            }
        });

        assertFalse(result.isSuccess());
        assertEquals(List.of("begin", "rollback"), lifecycle);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void mapsLockAcquisitionTimeoutToStableConcurrentOperationError() {
        List<String> lifecycle = new ArrayList<>();
        CommandServiceTemplate template = new CommandServiceTemplate(
                "test-app", transactionManager(lifecycle));

        Result<String> result = template.execute(TestOperation.CREATE, new ServiceOperation<>() {
            @Override
            public String execute() {
                throw new CannotAcquireLockException("lock wait timed out");
            }
        });

        assertFalse(result.isSuccess());
        assertEquals("201", result.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());
        assertEquals(List.of("begin", "rollback"), lifecycle);
    }

    private PlatformTransactionManager transactionManager(List<String> lifecycle) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            lifecycle.add("begin");
            return new SimpleTransactionStatus();
        });
        doAnswer(invocation -> {
            lifecycle.add("commit");
            return null;
        }).when(transactionManager).commit(any());
        doAnswer(invocation -> {
            lifecycle.add("rollback");
            return null;
        }).when(transactionManager).rollback(any());
        return transactionManager;
    }

    private enum TestOperation implements OperationCode {
        CREATE;

        @Override
        public String code() {
            return "9000";
        }
    }
}
