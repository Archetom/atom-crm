package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.converter.InvoiceProfilePOConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceProfileRepositoryImplTest {

    private final InvoiceProfileRepositoryImpl repository =
            new InvoiceProfileRepositoryImpl(null, new InvoiceProfilePOConverter());

    @Test
    void missingTenantFailsBeforeAnIdLookupCanReturnEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findById(null, null));
    }

    @Test
    void missingTenantFailsBeforeACustomerLookupCanReturnEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByCustomer(null, null, 1));
    }

    @Test
    void missingTenantFailsBeforeAnActiveCountCanReturnZero() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.countActiveByCustomer(null, null));
    }
}
