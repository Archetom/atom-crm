package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.ContractAccessResolver;
import io.github.archetom.crm.application.security.DashboardVisibilityNarrower;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.ReceivableAccessResolver;
import io.github.archetom.crm.domain.model.DashboardDataScope;
import io.github.archetom.crm.domain.model.FixedReportMetric;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.ContractRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ReceivableRepository;
import io.github.archetom.crm.domain.repository.ReceivablePlanRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixedReportQuerySupportTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final TenantId OTHER_TENANT = new TenantId(10L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final AuthenticatedCaller CALLER = new AuthenticatedCaller(
            MEMBER.getValue(), TENANT.getValue(), Set.of());

    @Test
    void dashboardTenantMismatchRemainsAnInternalFailureWithoutChangingB17Denial() {
        OpportunityAccessResolver opportunities = mock(OpportunityAccessResolver.class);
        DashboardVisibilityNarrower narrower = mock(DashboardVisibilityNarrower.class);
        OpportunityAccessResolver.OpportunityAccess mismatched =
                new OpportunityAccessResolver.OpportunityAccess(
                        OTHER_TENANT, MEMBER, mock(OpportunityVisibility.class));
        when(opportunities.access(
                CALLER, AuthorityCatalog.OPPORTUNITIES_READ,
                OpportunityAccessMode.READ))
                .thenReturn(mismatched);
        FixedReportQuerySupport support = new FixedReportQuerySupport(
                opportunities, mock(ContractAccessResolver.class),
                mock(ReceivableAccessResolver.class), narrower,
                mock(EffectiveFieldAccessResolver.class),
                mock(OpportunityRepository.class), mock(ContractRepository.class),
                mock(ReceivableRepository.class), mock(ReceivablePlanRepository.class));

        assertThrows(IllegalStateException.class, () -> support.resolveDashboardSource(
                CALLER, TENANT, FixedReportMetric.WON_OPPORTUNITY,
                DashboardDataScope.SELF));
        NonRetryableApplicationException reportFailure = assertThrows(
                NonRetryableApplicationException.class,
                () -> support.resolveSource(
                        CALLER, TENANT, FixedReportMetric.WON_OPPORTUNITY));

        assertEquals(ApplicationErrorCode.ACCESS_DENIED, reportFailure.getErrorCode());
        verify(narrower, never()).narrow(
                mismatched, DashboardDataScope.SELF);
    }
}
