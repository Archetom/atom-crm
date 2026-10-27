package io.github.archetom.crm.application.service.template;

import io.github.archetom.crm.shared.operation.OperationCode;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryServiceTemplateTest {

    @Test
    void executesReadPhaseInIndependentRepeatableReadTransaction() {
        List<String> lifecycle = new ArrayList<>();
        PlatformTransactionManager transactionManager = mock(
                PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    definition.getPropagationBehavior());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                    definition.getIsolationLevel());
            assertTrue(definition.isReadOnly());
            lifecycle.add("begin");
            return new SimpleTransactionStatus();
        });
        doAnswer(invocation -> {
            lifecycle.add("commit");
            return null;
        }).when(transactionManager).commit(any());
        QueryServiceTemplate template = new QueryServiceTemplate(
                "test-app", transactionManager);

        Result<String> result = template.execute(TestOperation.READ,
                new ServiceOperation<>() {
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
                        return "snapshot";
                    }

                    @Override
                    public void onSuccess(String ignored) {
                        lifecycle.add("onSuccess");
                    }
                });

        assertTrue(result.isSuccess());
        assertEquals("snapshot", result.getData());
        assertEquals(List.of("validate", "prepare", "begin", "execute",
                "onSuccess", "commit"), lifecycle);
    }

    private enum TestOperation implements OperationCode {
        READ;

        @Override
        public String code() {
            return "9001";
        }
    }
}
