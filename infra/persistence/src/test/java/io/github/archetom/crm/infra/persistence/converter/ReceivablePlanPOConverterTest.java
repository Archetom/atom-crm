package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ReceivablePlan;
import io.github.archetom.crm.infra.persistence.mysql.po.ReceivablePlanPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;

/** Every stored receivable-plan column returns to the field it came from. */
class ReceivablePlanPOConverterTest {

    private final ReceivablePlanPOConverter converter =
            new ReceivablePlanPOConverter(new ReceivableFieldValuePOConverter(new ObjectMapper()));

    @Test
    void everyColumnReturnsToItsOwnField() {
        ReceivablePlanPO original = filled(new ReceivablePlanPO(), overrides("status", "ACTIVE"));

        ReceivablePlan plan = converter.toDomain(original, List.of(), Map.of());
        ReceivablePlanPO restored = converter.toPO(plan);

        assertEveryFieldSurvives(original, restored, Set.of());
    }
}
