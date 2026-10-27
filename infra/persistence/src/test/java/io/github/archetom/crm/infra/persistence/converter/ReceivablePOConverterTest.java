package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Receivable;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;

/** Every stored receivable column returns to the field it came from. */
class ReceivablePOConverterTest {

    private final ReceivablePOConverter converter =
            new ReceivablePOConverter(new ReceivableFieldValuePOConverter(new ObjectMapper()));

    @Test
    void everyColumnReturnsToItsOwnField() {
        ReceivablePO original = filled(new ReceivablePO(), overrides("status", "ACTIVE"));

        Receivable receivable = converter.toDomain(original, List.of(), Map.of());
        ReceivablePO restored = converter.toPO(receivable);

        assertEveryFieldSurvives(original, restored, Set.of());
    }
}
