package io.github.archetom.crm.application.service.template;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes state-changing use cases and preserves transaction rollback semantics
 * when failures are converted to API results.
 */
@Component
public class CommandServiceTemplate extends OperationTemplateSupport {

    private final TransactionTemplate transactionTemplate;

    public CommandServiceTemplate(
            @Value("${spring.application.name}") String appName,
            PlatformTransactionManager transactionManager
    ) {
        super(appName);
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    protected <T> T invoke(ServiceOperation<T> operation) {
        return transactionTemplate.execute(status -> super.invoke(operation));
    }
}
