package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.entity.RoleAssignment;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.NavigationPreferencePO;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleAssignmentPO;
import io.github.archetom.crm.infra.persistence.mysql.po.RoleGrantPO;
import io.github.archetom.crm.infra.persistence.mysql.po.TenantRolePO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static io.github.archetom.crm.domain.policy.AuthorityCatalog.NAVIGATION_READ;
import static io.github.archetom.crm.domain.policy.AuthorityCatalog.USERS_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthorizationPOConverterTest {

    @Test
    void roleAndGrantConvertersPreserveEveryPersistedFieldWithoutRaisingEvents() {
        TenantRolePO rolePO = new TenantRolePO();
        rolePO.setId(8L);
        rolePO.setTenantId(9L);
        rolePO.setName("Support");
        rolePO.setDescription("Customer support role");
        rolePO.setStatus("ACTIVE");
        rolePO.setVersion(3L);
        LocalDateTime created = LocalDateTime.of(2026, 7, 15, 10, 0);
        rolePO.setCreatedTime(created);
        rolePO.setUpdatedTime(created.plusHours(1));
        RoleGrant grant = RoleGrant.withScope(USERS_READ, DataScope.DEPARTMENT_SUBTREE);

        TenantRole role = new TenantRolePOConverter().toDomain(rolePO, List.of(grant));
        TenantRolePO roundTrip = new TenantRolePOConverter().toPO(role);

        assertEquals(8L, roundTrip.getId());
        assertEquals(9L, roundTrip.getTenantId());
        assertEquals("Support", roundTrip.getName());
        assertEquals("Customer support role", roundTrip.getDescription());
        assertEquals("ACTIVE", roundTrip.getStatus());
        assertEquals(3L, roundTrip.getVersion());
        assertEquals(created, roundTrip.getCreatedTime());
        assertEquals(List.of(grant), role.getGrants());
        assertFalse(role.hasDomainEvents());

        RoleGrantPO grantPO = new RoleGrantPOConverter().toPO(
                new TenantId(9L), new RoleId(8L), grant);
        assertEquals(9L, grantPO.getTenantId());
        assertEquals(8L, grantPO.getRoleId());
        assertEquals(USERS_READ, grantPO.getAuthorityCode());
        assertEquals("DEPARTMENT_SUBTREE", grantPO.getDataScope());
    }

    @Test
    void assignmentAndPreferenceConvertersPreserveCompositeTenantIdentity() {
        RoleAssignment assignment = RoleAssignment.assign(
                new TenantId(9L), new MemberId(7L), new RoleId(8L));
        RoleAssignmentPO assignmentPO = new RoleAssignmentPOConverter().toPO(assignment);

        assertEquals(9L, assignmentPO.getTenantId());
        assertEquals(7L, assignmentPO.getActorId());
        assertEquals(8L, assignmentPO.getRoleId());

        NavigationPreference preference = NavigationPreference.create(
                new TenantId(9L), new MemberId(7L),
                List.of("home", "authorization.roles"), List.of("users"),
                Set.of("home", "authorization.roles", "users"));
        NavigationPreferencePOConverter converter = new NavigationPreferencePOConverter(new ObjectMapper());
        NavigationPreferencePO preferencePO = converter.toPO(preference);
        NavigationPreference restored = converter.toDomain(preferencePO);

        assertEquals(9L, preferencePO.getTenantId());
        assertEquals(7L, preferencePO.getActorId());
        assertEquals(List.of("home", "authorization.roles"), restored.getOrderedCodes());
        assertEquals(Set.of("users"), restored.getHiddenCodes());
        assertFalse(restored.hasDomainEvents());

        assertEquals(NAVIGATION_READ, RoleGrant.withoutScope(NAVIGATION_READ).authorityCode().getValue());
    }
}
