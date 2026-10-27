package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.infra.persistence.mysql.po.ActivityPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;

/** Every stored activity column returns to the field it came from. */
class ActivityPOConverterTest {

    private final ActivityPOConverter converter = new ActivityPOConverter();

    @Test
    void everyColumnReturnsToItsOwnField() {
        ActivityPO original = filled(new ActivityPO(), overrides(
                "subjectType", "CUSTOMER", "kind", "FOLLOW_UP", "status", "ACTIVE"));

        Activity activity = converter.toDomain(original, List.of());
        ActivityPO restored = converter.toPO(activity);

        assertEveryFieldSurvives(original, restored, Set.of());
    }
}
