package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.vo.ActivityRelationVO;
import io.github.archetom.crm.application.vo.ActivityVO;
import io.github.archetom.crm.domain.entity.Activity;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rechecks each timeline relation because its owning record may no longer be visible. */
@Component
@RequiredArgsConstructor
class ActivityOutputSupport {
    private final BusinessRecordAccessGuard recordAccessGuard;

    ActivityVO toVO(AuthenticatedCaller caller, TenantId tenantId, Activity value) {
        return toVOs(caller, tenantId, List.of(value)).getFirst();
    }

    List<ActivityVO> toVOs(AuthenticatedCaller caller, TenantId tenantId,
                           Collection<Activity> activities) {
        Map<io.github.archetom.crm.domain.model.BusinessObjectType, Set<Long>> visible = visibleRelations(
                caller, tenantId, activities);
        return activities.stream().map(value -> toVO(value, visible)).toList();
    }

    private ActivityVO toVO(Activity value,
                            Map<io.github.archetom.crm.domain.model.BusinessObjectType, Set<Long>> visible) {
        return new ActivityVO(value.getId().getValue(), value.getSubjectType().name(), value.getSubjectId(),
                value.getKind().name(), value.getCreatorMemberId().getValue(), value.getCategory(),
                value.getPurpose(), value.getContent(), value.getOccurredTime(), value.getNextContactTime(),
                value.getRelations().stream().filter(relation -> visible.containsKey(relation.objectType())
                        && visible.get(relation.objectType()).contains(relation.recordId()))
                        .map(relation -> new ActivityRelationVO(relation.objectType().name(),
                                relation.recordId())).toList(), value.getVersion(), value.getCreatedTime(),
                value.getUpdatedTime());
    }

    private Map<io.github.archetom.crm.domain.model.BusinessObjectType, Set<Long>> visibleRelations(
            AuthenticatedCaller caller, TenantId tenantId, Collection<Activity> activities) {
        Map<io.github.archetom.crm.domain.model.BusinessObjectType, Set<Long>> requested = new EnumMap<>(
                io.github.archetom.crm.domain.model.BusinessObjectType.class);
        activities.forEach(activity -> activity.getRelations().forEach(relation -> requested
                .computeIfAbsent(relation.objectType(), ignored -> new LinkedHashSet<>()).add(relation.recordId())));
        Map<io.github.archetom.crm.domain.model.BusinessObjectType, Set<Long>> visible = new EnumMap<>(
                io.github.archetom.crm.domain.model.BusinessObjectType.class);
        requested.forEach((type, ids) -> {
            Set<Long> accepted = new LinkedHashSet<>();
            List<Long> values = List.copyOf(ids);
            for (int from = 0; from < values.size(); from += 200) {
                try {
                    accepted.addAll(recordAccessGuard.visibleSubjectIds(caller, tenantId, type,
                            values.subList(from, Math.min(from + 200, values.size()))));
                } catch (NonRetryableApplicationException ignored) {
                    break;
                }
            }
            visible.put(type, Set.copyOf(accepted));
        });
        return visible;
    }
}
