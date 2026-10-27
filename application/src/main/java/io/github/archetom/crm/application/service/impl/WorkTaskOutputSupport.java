package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.vo.WorkTaskVO;
import io.github.archetom.crm.domain.entity.WorkTask;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.WorkTaskBusinessRecord;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Rechecks CRM read visibility before emitting optional task relationship IDs. */
@Component
@RequiredArgsConstructor
class WorkTaskOutputSupport {

    private final BusinessRecordAccessGuard recordAccessGuard;
    private final WorkTaskApplicationSupport support;

    WorkTaskVO toVO(
            AuthenticatedCaller caller, TenantId tenantId,
            WorkTask task, LocalDateTime now) {
        return support.toVO(task, now,
                visible(caller, tenantId, List.of(task)).contains(task.getId().getValue()));
    }

    List<WorkTaskVO> toVOs(
            AuthenticatedCaller caller, TenantId tenantId,
            List<WorkTask> tasks, LocalDateTime now) {
        Set<Long> visible = visible(caller, tenantId, tasks);
        return tasks.stream().map(task -> support.toVO(task, now,
                visible.contains(task.getId().getValue()))).toList();
    }

    private Set<Long> visible(
            AuthenticatedCaller caller, TenantId tenantId, List<WorkTask> tasks) {
        EnumMap<BusinessObjectType, LinkedHashSet<Long>> requested =
                new EnumMap<>(BusinessObjectType.class);
        for (WorkTask task : tasks) {
            WorkTaskBusinessRecord relation = task.getBusinessRecord();
            if (relation != null) {
                requested.computeIfAbsent(relation.objectType(), ignored ->
                        new LinkedHashSet<>()).add(relation.recordId());
            }
        }
        EnumMap<BusinessObjectType, Set<Long>> visibleIds =
                new EnumMap<>(BusinessObjectType.class);
        requested.forEach((type, ids) -> {
            try {
                visibleIds.put(type, recordAccessGuard.visibleSubjectIds(
                        caller, tenantId, type, ids));
            } catch (NonRetryableApplicationException exception) {
                if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                    throw exception;
                }
                visibleIds.put(type, Set.of());
            }
        });
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (WorkTask task : tasks) {
            WorkTaskBusinessRecord relation = task.getBusinessRecord();
            if (relation == null || visibleIds.getOrDefault(
                    relation.objectType(), Set.of()).contains(relation.recordId())) {
                result.add(task.getId().getValue());
            }
        }
        return Set.copyOf(result);
    }
}
