package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolRule;
import io.github.archetom.crm.domain.model.ContactPoolRuleType;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolRulePO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContactPoolPOConverterTest {

    @Test
    void poolRoundTripRestoresVersionedConfigurationWithoutEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
        ContactPool pool = ContactPool.create(new TenantId(9L), "Shared pool",
                Set.of(new MemberId(11L)), Set.of(new MemberId(12L)),
                Set.of(new DepartmentId(21L)), 3, 2, true,
                List.of(new ContactPoolRule(ContactPoolRuleType.NOT_WON, 60)));
        pool.onPersisted(new ContactPoolId(31L), 4L, now, now.plusMinutes(1));
        ContactPoolPOConverter converter = new ContactPoolPOConverter();
        var po = converter.toPO(pool);
        ContactPoolRulePO rule = new ContactPoolRulePO();
        rule.setRuleType("NOT_WON");
        rule.setInactiveDays(60);

        ContactPool restored = converter.toDomain(po, List.of(11L), List.of(12L),
                List.of(21L), List.of(rule));

        assertEquals(new ContactPoolId(31L), restored.getId());
        assertEquals(4L, restored.getVersion());
        assertEquals(pool.getRules(), restored.getRules());
        assertFalse(restored.hasDomainEvents());
    }
}
