package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.MarketingActivity;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityParticipantPO;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingActivityPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.assertEveryFieldSurvives;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.filled;
import static io.github.archetom.crm.infra.persistence.converter.PORoundTrip.overrides;

/** Every stored marketing-activity column returns to the field it came from. */
class MarketingActivityPOConverterTest {

    private final MarketingActivityPOConverter converter = new MarketingActivityPOConverter();

    @Test
    void everyColumnReturnsToItsOwnField() {
        MarketingActivityPO original = filled(new MarketingActivityPO(), overrides(
                "relatedObjectType", "CUSTOMER", "status", "ACTIVE"));

        // An activity with no participant at all is refused by the aggregate, and participants
        // live in their own table rather than in the row being compared.
        MarketingActivityParticipantPO participant = new MarketingActivityParticipantPO();
        participant.setTenantId(original.getTenantId());
        participant.setMarketingActivityId(original.getId());
        participant.setActorId(4242L);

        MarketingActivity activity = converter.toDomain(original, List.of(participant), List.of());
        MarketingActivityPO restored = converter.toPO(activity);

        assertEveryFieldSurvives(original, restored, Set.of());
    }
}
