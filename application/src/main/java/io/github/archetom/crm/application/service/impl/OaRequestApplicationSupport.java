package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.OaRequestSubmitRequest;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaFormField;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.exception.ApprovalDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.policy.ApprovalFactPolicy;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class OaRequestApplicationSupport {

    private static final Set<FieldCode> SERVER_FACTS = Set.of(
            code(OaRequestFieldCatalog.FORM_CODE),
            code(OaRequestFieldCatalog.APPLICANT_ACTOR),
            code(OaRequestFieldCatalog.APPLICANT_DEPARTMENT),
            code(OaRequestFieldCatalog.SUBMITTED_TIME));

    private final FieldDefinitionRepository fieldRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;

    Map<FieldCode, FieldDefinition> fields(TenantId tenantId, boolean includeInactive) {
        return fieldRepository.findAll(
                        tenantId, BusinessObjectType.OA_REQUEST, includeInactive).stream()
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    Map<FieldCode, List<String>> facts(
            TenantId tenantId,
            OaFormDefinition form,
            MemberId applicant,
            DepartmentId department,
            LocalDateTime submittedTime,
            Map<String, List<String>> requested,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        LinkedHashMap<FieldCode, List<String>> result = clientFacts(
                tenantId, form.getFields(), requested, fields, accesses, true);
        result.put(code(OaRequestFieldCatalog.FORM_CODE),
                List.of(form.getNormalizedCode()));
        result.put(code(OaRequestFieldCatalog.APPLICANT_ACTOR),
                List.of(applicant.getValue().toString()));
        result.put(code(OaRequestFieldCatalog.APPLICANT_DEPARTMENT),
                department == null ? List.of() : List.of(department.getValue().toString()));
        result.put(code(OaRequestFieldCatalog.SUBMITTED_TIME),
                List.of(submittedTime.toString()));
        if (result.size() > OaRequest.MAX_FACTS) {
            throw invalid("OA request facts exceed the supported limit");
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    /** The OA form, rather than the whole OA catalog, is the authority for FILE keys and requiredness. */
    Map<FieldCode, FieldDefinition> fileFields(OaFormDefinition form,
                                                Map<FieldCode, FieldDefinition> fields) {
        LinkedHashMap<FieldCode, FieldDefinition> result = new LinkedHashMap<>();
        for (OaFormField configured : form.getFields()) {
            FieldDefinition definition = fields.get(configured.fieldCode());
            if (definition == null || !definition.isActive()) {
                throw invalid("OA request field is unavailable");
            }
            if (definition.getFieldType() == CustomFieldType.FILE) {
                result.put(configured.fieldCode(), definition);
            }
        }
        return result;
    }

    void requireConfiguredFileValues(OaFormDefinition form, Map<String, List<Long>> values,
                                     Map<FieldCode, FieldDefinition> fileFields) {
        Map<String, List<Long>> source = values == null ? Map.of() : values;
        for (OaFormField configured : form.getFields()) {
            if (!fileFields.containsKey(configured.fieldCode())) continue;
            List<Long> files = source.get(configured.fieldCode().getValue());
            if (configured.required() && (files == null || files.isEmpty())) {
                throw invalid("A required OA file field is missing");
            }
        }
    }

    String retryDigest(
            TenantId tenantId,
            OaRequest existing,
            OaRequestSubmitRequest request,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, List<FileFieldValue>> fileValues) {
        if (!existing.getFormId().getValue().equals(request.getFormId())
                || !existing.getFormVersion().equals(request.getFormVersion())) {
            throw conflict();
        }
        LinkedHashMap<FieldCode, List<String>> facts = retryFacts(
                existing, request.getValues(), fields);
        SERVER_FACTS.forEach(code -> facts.put(code,
                existing.getFacts().getOrDefault(code, List.of())));
        try {
            return digestWithFileValues(existing.getFormId().getValue(), existing.getFormVersion(),
                    facts, request.getSelectedApprovers(), request.getWatcherActorIds(),
                    fileValues);
        } catch (RuntimeException exception) {
            throw conflict();
        }
    }

    private LinkedHashMap<FieldCode, List<String>> retryFacts(
            OaRequest existing,
            Map<String, List<String>> requested,
            Map<FieldCode, FieldDefinition> fields) {
        Map<String, List<String>> source = requested == null ? Map.of() : requested;
        LinkedHashSet<FieldCode> expected = existing.getFacts().keySet().stream()
                .filter(code -> !SERVER_FACTS.contains(code))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashMap<FieldCode, List<String>> raw = new LinkedHashMap<>();
        try {
            source.forEach((rawCode, values) -> {
                FieldCode code = new FieldCode(rawCode);
                if (!expected.contains(code) || raw.putIfAbsent(code, values) != null) {
                    throw conflict();
                }
            });
            LinkedHashMap<FieldCode, List<String>> result = new LinkedHashMap<>();
            for (FieldCode code : expected) {
                List<String> supplied = raw.get(code) == null ? List.of() : raw.get(code);
                List<String> stored = existing.getFacts().getOrDefault(code, List.of());
                if (supplied.equals(stored)) {
                    result.put(code, stored);
                    continue;
                }
                FieldDefinition field = fields.get(code);
                result.put(code, ApprovalFactPolicy.normalizeImmutable(field, supplied));
            }
            return result;
        } catch (NonRetryableApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw conflict();
        }
    }

    String digest(
            Long formId,
            Long formVersion,
            Map<FieldCode, List<String>> facts,
            Map<String, List<Long>> selectedApprovers,
            Collection<Long> watchers,
            Map<String, List<Long>> fileFields) {
        return digestWithFileValues(formId, formVersion, facts, selectedApprovers, watchers,
                rawFileValues(fileFields));
    }

    String digestWithFileValues(
            Long formId,
            Long formVersion,
            Map<FieldCode, List<String>> facts,
            Map<String, List<Long>> selectedApprovers,
            Collection<Long> watchers,
            Map<FieldCode, List<FileFieldValue>> fileFields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, Long.toString(formId));
            update(digest, Long.toString(formVersion));
            facts.entrySet().stream().sorted(Map.Entry.comparingByKey(
                            Comparator.comparing(FieldCode::getValue)))
                    .forEach(entry -> {
                        update(digest, entry.getKey().getValue());
                        update(digest, Integer.toString(entry.getValue().size()));
                        entry.getValue().forEach(value -> update(digest, value));
                    });
            Map<String, List<Long>> selected = selectedApprovers == null
                    ? Map.of() : selectedApprovers;
            selected.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        update(digest, entry.getKey().trim());
                        List<Long> actorIds = entry.getValue() == null
                                ? List.of() : entry.getValue();
                        update(digest, Integer.toString(actorIds.size()));
                        actorIds.forEach(value -> update(digest, String.valueOf(value)));
                    });
            List<Long> watcherIds = watchers == null ? List.of() : List.copyOf(watchers);
            update(digest, Integer.toString(watcherIds.size()));
            watcherIds.forEach(value -> update(digest, String.valueOf(value)));
            Map<FieldCode, List<FileFieldValue>> files = fileFields == null ? Map.of() : fileFields;
            files.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparing(FieldCode::getValue))).forEach(entry -> {
                update(digest, entry.getKey().getValue());
                List<FileFieldValue> values = entry.getValue() == null ? List.of() : entry.getValue();
                update(digest, Integer.toString(values.size()));
                values.stream().sorted(Comparator.comparingInt(FileFieldValue::ordinal)).forEach(value -> {
                    update(digest, Long.toString(value.fileId().getValue()));
                    update(digest, Long.toString(value.contentVersion()));
                    update(digest, java.util.HexFormat.of().formatHex(value.evidenceDigest()));
                });
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NonRetryableApplicationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OA request digest cannot be calculated", exception);
        }
    }

    String digest(Long formId, Long formVersion, Map<FieldCode, List<String>> facts,
                  Map<String, List<Long>> selectedApprovers, Collection<Long> watchers) {
        return digestWithFileValues(formId, formVersion, facts, selectedApprovers, watchers, Map.of());
    }

    private static Map<FieldCode, List<FileFieldValue>> rawFileValues(
            Map<String, List<Long>> fileFields) {
        Map<String, List<Long>> source = fileFields == null ? Map.of() : fileFields;
        LinkedHashMap<FieldCode, List<FileFieldValue>> result = new LinkedHashMap<>();
        source.forEach((rawCode, ids) -> {
            FieldCode code = new FieldCode(rawCode);
            List<Long> values = ids == null ? List.of() : ids;
            List<FileFieldValue> normalized = new ArrayList<>();
            for (int ordinal = 0; ordinal < values.size(); ordinal++) {
                Long id = values.get(ordinal);
                normalized.add(new FileFieldValue(code,
                        new io.github.archetom.crm.domain.valueobject.AttachmentFileId(id), ordinal,
                        1, new byte[32]));
            }
            result.put(code, normalized);
        });
        return result;
    }

    private LinkedHashMap<FieldCode, List<String>> clientFacts(
            TenantId tenantId,
            Collection<OaFormField> configuredFields,
            Map<String, List<String>> requested,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses,
            boolean requireCurrentAccess) {
        Map<String, List<String>> source = requested == null ? Map.of() : requested;
        LinkedHashMap<FieldCode, OaFormField> configured = configuredFields.stream()
                .collect(Collectors.toMap(OaFormField::fieldCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        LinkedHashMap<FieldCode, List<String>> raw = new LinkedHashMap<>();
        source.forEach((rawCode, values) -> {
            FieldCode fieldCode;
            try {
                fieldCode = new FieldCode(rawCode);
            } catch (RuntimeException exception) {
                throw invalid("OA request field code is invalid");
            }
            if (!configured.containsKey(fieldCode) || raw.putIfAbsent(fieldCode, values) != null) {
                throw invalid("OA request contains an unavailable field");
            }
        });
        LinkedHashMap<FieldCode, List<String>> result = new LinkedHashMap<>();
        for (OaFormField configuredField : configured.values()) {
            FieldDefinition definition = fields.get(configuredField.fieldCode());
            if (definition == null || requireCurrentAccess && !definition.isActive()) {
                throw invalid("OA request field is unavailable");
            }
            if (requireCurrentAccess) {
                EffectiveFieldAccess access = accesses.get(configuredField.fieldCode());
                if (access == null
                        || !access.accessLevel().covers(FieldAccessLevel.WRITE)) {
                    throw denied("OA request field is not writable");
                }
            }
            if (definition.getFieldType() == CustomFieldType.FILE) {
                // FILE values are held in B24c's typed relation tables.  Keeping them out of
                // scalar facts also prevents routing conditions or request JSON from treating a
                // file ID as a string value.
                if (raw.containsKey(configuredField.fieldCode())) {
                    throw invalid("OA FILE fields must use fileFields");
                }
                continue;
            }
            List<String> values;
            try {
                values = requireCurrentAccess
                        ? ApprovalFactPolicy.normalizeStored(
                                definition, raw.get(configuredField.fieldCode()))
                        : ApprovalFactPolicy.normalizeImmutable(
                                definition, raw.get(configuredField.fieldCode()));
            } catch (ApprovalDomainException exception) {
                throw invalid("OA request field value is invalid");
            }
            if (requireCurrentAccess
                    && (configuredField.required() || definition.isRequired())
                    && values.isEmpty()) {
                throw invalid("A required OA request field is missing");
            }
            result.put(configuredField.fieldCode(), values);
        }
        if (requireCurrentAccess) {
            validateReferences(tenantId, fields, result);
        }
        return result;
    }

    private void validateReferences(
            TenantId tenantId,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, List<String>> values) {
        LinkedHashSet<MemberId> members = new LinkedHashSet<>();
        LinkedHashSet<DepartmentId> departments = new LinkedHashSet<>();
        values.forEach((code, fieldValues) -> {
            FieldDefinition field = fields.get(code);
            if (field.getFieldType() == CustomFieldType.USER) {
                fieldValues.stream().map(Long::valueOf).map(MemberId::new)
                        .forEach(members::add);
            } else if (field.getFieldType() == CustomFieldType.DEPARTMENT) {
                fieldValues.stream().map(Long::valueOf).map(DepartmentId::new)
                        .forEach(departments::add);
            } else if (field.getFieldType() == CustomFieldType.RELATION
                    && !fieldValues.isEmpty()) {
                throw new NonRetryableApplicationException(
                        ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "OA relation fields require a governed owning module");
            }
        });
        if (!members.isEmpty()) {
            Set<MemberId> active = membershipRepository.findByMembers(tenantId, members)
                    .stream().filter(value -> value.isActive())
                    .map(value -> value.getMemberId()).collect(Collectors.toSet());
            if (!active.containsAll(members)) {
                throw invalid("OA request member field is invalid");
            }
        }
        if (!departments.isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, new ArrayList<>(departments)).stream()
                    .filter(Department::isActive).map(Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(departments)) {
                throw invalid("OA request department field is invalid");
            }
        }
    }

    NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.PARAMETER_INVALID, message);
    }

    NonRetryableApplicationException denied(String message) {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.ACCESS_DENIED, message);
    }

    NonRetryableApplicationException conflict() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.VERSION_CONFLICT,
                "OA request idempotency key is already in use");
    }

    private static void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(4).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
