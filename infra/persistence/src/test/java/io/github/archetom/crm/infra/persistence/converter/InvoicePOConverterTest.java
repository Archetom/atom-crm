package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.infra.persistence.mysql.po.InvoicePO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The widest positional reconstruction in the persistence layer.
 *
 * <p>{@code Invoice.Snapshot} takes 28 components, and ten of them are consecutive {@code String}s
 * covering a tax number, a bank account, a registered address and phone, a contact name and phone,
 * and a shipping address. Swapping any two compiles cleanly and puts one invoice's banking details
 * under another heading — the kind of defect that reaches a printed invoice before anyone notices.</p>
 */
class InvoicePOConverterTest {

    private final InvoicePOConverter converter = new InvoicePOConverter(new ObjectMapper());

    @Test
    void everyColumnReturnsToItsOwnFieldAcrossTheWidestReconstruction() {
        // ISSUED rather than PENDING: a pending invoice may not carry an issue result, so the
        // issued state is the one in which every column holds a value to compare.
        InvoicePO original = filled(new InvoicePO(),
                overrides("titleType", "ORGANIZATION", "status", "ISSUED"));

        Invoice invoice = converter.toDomain(original, List.of(), Map.of());
        InvoicePO restored = converter.toPO(invoice);

        assertEveryFieldSurvives(original, restored, Set.of());
    }

    /**
     * Guards the two columns the round trip cannot prove on its own: both are parsed into an enum
     * and written back through {@code name()}, so a mix-up between them would survive a comparison
     * that only checked they were equal to themselves.
     */
    @Test
    void theEnumBackedColumnsAreParsedIntoTheirOwnTypes() {
        InvoicePO original = filled(new InvoicePO(),
                overrides("titleType", "INDIVIDUAL", "status", "ISSUED"));

        Invoice invoice = converter.toDomain(original, List.of(), Map.of());

        assertEquals("INDIVIDUAL", invoice.getTitleType().name());
        assertEquals("ISSUED", invoice.getStatus().name());
    }
}
