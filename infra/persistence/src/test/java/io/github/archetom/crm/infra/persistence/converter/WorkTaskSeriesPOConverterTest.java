package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.WorkTaskSeries;
import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskSeriesPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;

/** Every stored recurrence-series column returns to the field it came from. */
class WorkTaskSeriesPOConverterTest {

    private final WorkTaskSeriesPOConverter converter = new WorkTaskSeriesPOConverter();

    @Test
    void everyColumnReturnsToItsOwnField() {
        // A daily series: monthly-only columns must stay empty, so they are pinned rather than
        // filled, and the remaining columns still each carry a value unique to themselves.
        WorkTaskSeriesPO original = filled(new WorkTaskSeriesPO(), overrides(
                "priority", "HIGH", "recurrenceFrequency", "DAILY", "status", "ACTIVE",
                "zoneId", "UTC", "monthDay", null, "lastDayOfMonth", false,
                "lastSafeReason", "ASSIGNEE_ACCESS_REVOKED"));

        WorkTaskSeries series = converter.toDomain(original, List.of(), List.of());
        WorkTaskSeriesPO restored = converter.toPO(series);

        assertEveryFieldSurvives(original, restored, Set.of());
    }
}
