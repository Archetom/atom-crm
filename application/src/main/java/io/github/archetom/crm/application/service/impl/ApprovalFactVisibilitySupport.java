package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.domain.entity.ApprovalInstance;
import io.github.archetom.crm.domain.model.ApprovalInstanceSummary;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.ContractFieldCatalog;
import io.github.archetom.crm.domain.policy.InvoiceFieldCatalog;
import io.github.archetom.crm.domain.policy.ReceivableFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies the owning domain's current visibility to relationship facts in approval output. */
@Component
@RequiredArgsConstructor
class ApprovalFactVisibilitySupport {

    private static final Map<BusinessObjectType, Map<FieldCode, BusinessObjectType>> RELATIONS =
            Map.of(
                    BusinessObjectType.CONTRACT, Map.of(
                            new FieldCode(ContractFieldCatalog.CUSTOMER),
                            BusinessObjectType.CUSTOMER,
                            new FieldCode(ContractFieldCatalog.OPPORTUNITY),
                            BusinessObjectType.OPPORTUNITY,
                            new FieldCode(ContractFieldCatalog.CONTACT),
                            BusinessObjectType.CONTACT),
                    BusinessObjectType.INVOICE, Map.of(
                            new FieldCode(InvoiceFieldCatalog.CUSTOMER),
                            BusinessObjectType.CUSTOMER,
                            new FieldCode(InvoiceFieldCatalog.CONTRACT),
                            BusinessObjectType.CONTRACT),
                    BusinessObjectType.RECEIVABLE, Map.of(
                            new FieldCode(ReceivableFieldCatalog.CUSTOMER),
                            BusinessObjectType.CUSTOMER,
                            new FieldCode(ReceivableFieldCatalog.CONTRACT),
                            BusinessObjectType.CONTRACT,
                            new FieldCode(ReceivableFieldCatalog.PLAN),
                            BusinessObjectType.RECEIVABLE_PLAN));

    private final BusinessRecordAccessGuard recordAccessGuard;

    Set<FieldCode> hiddenForInstance(
            AuthenticatedCaller caller, TenantId tenantId, ApprovalInstance instance) {
        return hidden(caller, tenantId, List.of(snapshot(instance)))
                .getOrDefault(instance.getId().getValue(), Set.of());
    }

    Map<Long, Set<FieldCode>> hiddenForInstances(
            AuthenticatedCaller caller, TenantId tenantId,
            Collection<ApprovalInstance> instances) {
        return hidden(caller, tenantId, instances.stream().map(this::snapshot).toList());
    }

    Map<Long, Set<FieldCode>> hiddenForSummaries(
            AuthenticatedCaller caller, TenantId tenantId,
            Collection<ApprovalInstanceSummary> instances) {
        return hidden(caller, tenantId, instances.stream().map(this::snapshot).toList());
    }

    private Map<Long, Set<FieldCode>> hidden(
            AuthenticatedCaller caller, TenantId tenantId, List<FactSnapshot> snapshots) {
        EnumMap<BusinessObjectType, LinkedHashSet<Long>> requested =
                new EnumMap<>(BusinessObjectType.class);
        List<RelationshipFact> relationships = new ArrayList<>();
        LinkedHashMap<Long, LinkedHashSet<FieldCode>> hidden = new LinkedHashMap<>();
        for (FactSnapshot snapshot : snapshots) {
            Map<FieldCode, BusinessObjectType> rules = RELATIONS.get(snapshot.objectType());
            if (rules == null) {
                continue;
            }
            rules.forEach((fieldCode, targetType) -> {
                List<String> values = snapshot.facts().get(fieldCode);
                if (values == null) {
                    return;
                }
                Long targetId = relationId(values);
                if (targetId == null) {
                    hidden.computeIfAbsent(snapshot.instanceId(), ignored ->
                            new LinkedHashSet<>()).add(fieldCode);
                    return;
                }
                requested.computeIfAbsent(targetType, ignored ->
                        new LinkedHashSet<>()).add(targetId);
                relationships.add(new RelationshipFact(
                        snapshot.instanceId(), fieldCode, targetType, targetId));
            });
        }

        EnumMap<BusinessObjectType, Set<Long>> visible =
                new EnumMap<>(BusinessObjectType.class);
        requested.forEach((targetType, ids) -> {
            try {
                visible.put(targetType, recordAccessGuard.visibleSubjectIds(
                        caller, tenantId, targetType, ids));
            } catch (NonRetryableApplicationException exception) {
                if (exception.getErrorCode() != ApplicationErrorCode.ACCESS_DENIED) {
                    throw exception;
                }
                visible.put(targetType, Set.of());
            }
        });
        relationships.forEach(relationship -> {
            if (!visible.getOrDefault(relationship.targetType(), Set.of())
                    .contains(relationship.targetId())) {
                hidden.computeIfAbsent(relationship.instanceId(), ignored ->
                        new LinkedHashSet<>()).add(relationship.fieldCode());
            }
        });

        LinkedHashMap<Long, Set<FieldCode>> result = new LinkedHashMap<>();
        hidden.forEach((instanceId, codes) -> result.put(instanceId, Set.copyOf(codes)));
        return java.util.Collections.unmodifiableMap(result);
    }

    private FactSnapshot snapshot(ApprovalInstance instance) {
        return new FactSnapshot(instance.getId().getValue(), instance.getObjectType(),
                instance.getFacts());
    }

    private FactSnapshot snapshot(ApprovalInstanceSummary instance) {
        return new FactSnapshot(instance.id().getValue(), instance.objectType(), instance.facts());
    }

    private Long relationId(List<String> values) {
        if (values.size() != 1) {
            return null;
        }
        try {
            long value = Long.parseLong(values.getFirst());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record FactSnapshot(
            Long instanceId, BusinessObjectType objectType,
            Map<FieldCode, List<String>> facts) {
    }

    private record RelationshipFact(
            Long instanceId, FieldCode fieldCode,
            BusinessObjectType targetType, Long targetId) {
    }
}
