package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.model.WorkTaskPriority;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.WorkTaskId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkTaskOutputSupportTest {

    @Test
    void taskRemainsVisibleWhileUnreadableBusinessRelationshipIsOmitted() {
        TenantId tenantId = new TenantId(9L);
        AuthenticatedCaller caller = new AuthenticatedCaller(
                7L, tenantId.getValue(), Set.of("work-tasks:read"));
        BusinessRecordAccessGuard guard = mock(BusinessRecordAccessGuard.class);
        WorkTaskApplicationSupport applicationSupport = new WorkTaskApplicationSupport();
        WorkTaskOutputSupport output = new WorkTaskOutputSupport(guard, applicationSupport);
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        WorkTask task = WorkTask.create(tenantId, new MemberId(7L),
                new MemberId(8L), "Task", null, WorkTaskPriority.NORMAL, null, now);
        task.updateBusinessRecord(new WorkTaskBusinessRecord(
                BusinessObjectType.CUSTOMER, 41L), now);
        task.onPersisted(new WorkTaskId(31L), 0L, now, now);
        when(guard.visibleSubjectIds(caller, tenantId,
                BusinessObjectType.CUSTOMER, Set.of(41L)))
                .thenThrow(new NonRetryableApplicationException(
                        ApplicationErrorCode.ACCESS_DENIED, "denied"));

        var value = output.toVO(caller, tenantId, task, now);

        assertNull(value.businessObjectType());
        assertNull(value.businessRecordId());
    }
}
