package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowFieldAccessCacheTest {

    @Test
    void resolvesEachBusinessObjectTypeOnlyOncePerUseCase() {
        WorkflowFieldAccessSupport support = mock(WorkflowFieldAccessSupport.class);
        TenantId tenantId = new TenantId(99L);
        MemberId memberId = new MemberId(7L);
        Map<FieldCode, EffectiveFieldAccess> customerAccesses = Map.of();
        Map<FieldCode, EffectiveFieldAccess> contactAccesses = Map.of(
                new FieldCode("name"), mock(EffectiveFieldAccess.class));
        when(support.accesses(tenantId, memberId, BusinessObjectType.CUSTOMER))
                .thenReturn(customerAccesses);
        when(support.accesses(tenantId, memberId, BusinessObjectType.CONTACT))
                .thenReturn(contactAccesses);
        WorkflowFieldAccessCache cache = new WorkflowFieldAccessCache(
                support, tenantId, memberId);

        assertSame(customerAccesses, cache.accesses(BusinessObjectType.CUSTOMER));
        assertSame(customerAccesses, cache.accesses(BusinessObjectType.CUSTOMER));
        assertSame(contactAccesses, cache.accesses(BusinessObjectType.CONTACT));
        assertSame(contactAccesses, cache.accesses(BusinessObjectType.CONTACT));

        verify(support, times(1)).accesses(
                tenantId, memberId, BusinessObjectType.CUSTOMER);
        verify(support, times(1)).accesses(
                tenantId, memberId, BusinessObjectType.CONTACT);
    }
}
