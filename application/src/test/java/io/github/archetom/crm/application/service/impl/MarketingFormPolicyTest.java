package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketingFormPolicyTest {

    @Test
    void rejectsAnOwnerWhoseLeadWriteScopeCannotBeResolved() {
        LeadAccessResolver leadAccessResolver = mock(LeadAccessResolver.class);
        when(leadAccessResolver.access(any(), eq(AuthorityCatalog.LEADS_WRITE),
                eq(LeadAccessMode.WRITE))).thenThrow(new NonRetryableApplicationException(
                        ApplicationErrorCode.ACCESS_DENIED,
                        "Department lead data scope is unavailable"));
        MarketingFormPolicy policy = new MarketingFormPolicy(
                mock(FieldDefinitionRepository.class),
                mock(EffectiveFieldAccessResolver.class), leadAccessResolver,
                mock(CustomerAccessResolver.class));

        NonRetryableApplicationException rejected = assertThrows(
                NonRetryableApplicationException.class,
                () -> policy.validate(new TenantId(9L), new MemberId(11L),
                        List.of(), false));

        assertEquals(ApplicationErrorCode.ACCESS_DENIED, rejected.getErrorCode());
        verify(leadAccessResolver).access(any(), eq(AuthorityCatalog.LEADS_WRITE),
                eq(LeadAccessMode.WRITE));
    }
}
