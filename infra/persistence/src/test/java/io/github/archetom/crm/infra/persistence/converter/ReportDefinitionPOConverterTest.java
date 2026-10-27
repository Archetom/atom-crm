package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ReportDefinition;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.infra.persistence.mysql.po.ReportDefinitionPO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;

/** Every stored report column returns to the field it came from. */
class ReportDefinitionPOConverterTest {

    private static final String REPORT_NAME = "Quarterly Revenue";

    /** A report needs at least one measure, and the column is re-serialised on the way out. */
    private static final String MEASURES_JSON = "[{\"type\":\"SUM\",\"fieldCode\":\"amount\"}]";

    private final ReportDefinitionPOConverter converter =
            new ReportDefinitionPOConverter(new ObjectMapper());

    @Test
    void everyColumnReturnsToItsOwnField() {
        ReportDefinitionPO original = filled(new ReportDefinitionPO(), overrides(
                "objectType", "CONTRACT", "granularity", "MONTH", "status", "ACTIVE",
                // The normalized name is re-derived from the name, so it must agree with it.
                "name", REPORT_NAME, "normalizedName", IdentifierKeyNormalizer.normalize(REPORT_NAME),
                "timeFieldCode", "order_date", "dimensionFieldCode", "owner_actor_id",
                "filtersJson", "[]", "measuresJson", MEASURES_JSON));

        ReportDefinition definition = converter.toDomain(original);
        ReportDefinitionPO restored = converter.toPO(definition);

        assertEveryFieldSurvives(original, restored, Set.of());
    }
}
