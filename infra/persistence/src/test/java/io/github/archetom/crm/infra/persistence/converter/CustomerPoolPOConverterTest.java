package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolRule;
import io.github.archetom.crm.domain.model.CustomerPoolRuleType;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolRulePO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomerPoolPOConverterTest {

    @Test
    void poolRoundTripRestoresVersionedConfigurationWithoutEvents() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
        CustomerPool pool = CustomerPool.create(new TenantId(9L), "Shared pool",
                Set.of(new MemberId(11L)), Set.of(new MemberId(12L)),
                Set.of(new DepartmentId(21L)), 3, 2, true,
                List.of(new CustomerPoolRule(CustomerPoolRuleType.NOT_WON, 60)));
        pool.onPersisted(new CustomerPoolId(31L), 4L, now, now.plusMinutes(1));
        CustomerPoolPOConverter converter = new CustomerPoolPOConverter();
        var po = converter.toPO(pool);
        CustomerPoolRulePO rule = new CustomerPoolRulePO();
        rule.setRuleType("NOT_WON");
        rule.setInactiveDays(60);

        CustomerPool restored = converter.toDomain(po, List.of(11L), List.of(12L),
                List.of(21L), List.of(rule));

        assertEquals(new CustomerPoolId(31L), restored.getId());
        assertEquals(4L, restored.getVersion());
        assertEquals(pool.getRules(), restored.getRules());
        assertFalse(restored.hasDomainEvents());
    }
}
