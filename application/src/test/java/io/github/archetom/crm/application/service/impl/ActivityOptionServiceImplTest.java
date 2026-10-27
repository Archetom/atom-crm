package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ActivityOptionRequest;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.TestTransactionManager;
import io.github.archetom.crm.domain.repository.ActivityOptionRepository;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityOptionServiceImplTest {
    private static final TenantId TENANT = new TenantId(9L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(7L, 9L,
            Set.of("activities:read", "activities:write"));

    @Test
    void returnsGovernedDefaultsWhenTenantHasNoOverrides() {
        ActivityOptionRepository repository = mock(ActivityOptionRepository.class);
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        when(guard.requireActiveMembership(CALLER, "activities:read")).thenReturn(TENANT);
        when(repository.findAll(TENANT)).thenReturn(List.of());

        var result = service(repository, guard).query(CALLER);

        assertTrue(result.isSuccess());
        assertEquals(List.of("CALL", "MEETING", "EMAIL", "GENERAL_FOLLOW_UP"),
                result.getData().stream().map(value -> value.value()).toList());
    }

    @Test
    void normalizesReplacementAndRejectsCatalogWithoutAMethod() {
        ActivityOptionRepository repository = mock(ActivityOptionRepository.class);
        OrganizationAccessGuard guard = mock(OrganizationAccessGuard.class);
        when(guard.requireActiveMembership(CALLER, "activities:write")).thenReturn(TENANT);
        ActivityOptionServiceImpl service = service(repository, guard);

        var replaced = service.replace(CALLER, List.of(
                option(" method ", " call ", "Phone", 20),
                option("template", "intro", "Introduction", 10)));
        var rejected = service.replace(CALLER, List.of(
                option("template", "intro", "Introduction", 10)));

        assertTrue(replaced.isSuccess());
        assertEquals(List.of("CALL", "INTRO"),
                replaced.getData().stream().map(value -> value.value()).toList());
        assertFalse(rejected.isSuccess());
        verify(repository).replaceAll(eq(TENANT), any(List.class));
    }

    private ActivityOptionRequest option(String type, String value, String label, int order) {
        return new ActivityOptionRequest().setType(type).setValue(value).setLabel(label)
                .setDisplayOrder(order);
    }

    private ActivityOptionServiceImpl service(
            ActivityOptionRepository repository, OrganizationAccessGuard guard) {
        TestTransactionManager transactions = new TestTransactionManager();
        return new ActivityOptionServiceImpl(repository, guard,
                new CommandServiceTemplate("test-app", transactions),
                new QueryServiceTemplate("test-app", transactions));
    }
}
