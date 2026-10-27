package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MarketingFormField;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validates a public form against the target record's current field and ownership policy.
 *
 * <p>The target type is persisted on each form. It is deliberately not inferred from the
 * submitted values or a marketing activity label.</p>
 */
@Component
@RequiredArgsConstructor
public class MarketingFormPolicy {

    private static final Set<String> PUBLIC_LEAD_BUILTIN_FIELDS = Set.of(
            LeadFieldCatalog.NAME,
            LeadFieldCatalog.PHONE_NUMBER,
            LeadFieldCatalog.TELEPHONE,
            LeadFieldCatalog.JOB_TITLE,
            LeadFieldCatalog.EMAIL,
            LeadFieldCatalog.WEBSITE,
            LeadFieldCatalog.DESCRIPTION,
            LeadFieldCatalog.NEXT_CONTACT_TIME);
    private static final Set<String> PUBLIC_CUSTOMER_BUILTIN_FIELDS = Set.of(
            CustomerFieldCatalog.NAME,
            CustomerFieldCatalog.PHONE_NUMBER,
            CustomerFieldCatalog.TELEPHONE,
            CustomerFieldCatalog.EMAIL,
            CustomerFieldCatalog.WEBSITE,
            CustomerFieldCatalog.DESCRIPTION);

    private final FieldDefinitionRepository fieldRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final LeadAccessResolver leadAccessResolver;
    private final CustomerAccessResolver customerAccessResolver;

    /** Returns only target fields a resolved form owner can safely expose to anonymous users. */
    public List<FieldDefinition> configurationFields(
            TenantId tenantId, MemberId ownerMemberId, MarketingFormTargetType targetType) {
        requireOwnerCaller(tenantId, ownerMemberId, targetType);
        List<FieldDefinition> activeFields = fields(tenantId, targetType);
        Map<FieldCode, EffectiveFieldAccess> accesses = fieldAccessResolver.resolve(
                tenantId, ownerMemberId, targetType.businessObjectType());
        return activeFields.stream()
                .filter(FieldDefinition::isActive)
                .filter(field -> isPubliclySupported(targetType, field))
                .filter(field -> accesses.getOrDefault(field.getCode(),
                        EffectiveFieldAccess.hidden()).accessLevel()
                        .covers(FieldAccessLevel.WRITE))
                .sorted(Comparator.comparing(field -> field.getCode().getValue()))
                .toList();
    }

    public ValidatedConfiguration validate(
            TenantId tenantId, MemberId ownerMemberId, MarketingFormTargetType targetType,
            Collection<MarketingFormField> configuredFields, boolean requireComplete) {
        AuthenticatedCaller ownerCaller = requireOwnerCaller(tenantId, ownerMemberId, targetType);
        List<FieldDefinition> activeFields = fields(tenantId, targetType);
        Map<FieldCode, FieldDefinition> definitions = activeFields.stream()
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<FieldCode, EffectiveFieldAccess> accesses = fieldAccessResolver.resolve(
                tenantId, ownerMemberId, targetType.businessObjectType());
        requireWrite(accesses, nameField(targetType));
        requireWrite(accesses, ownerField(targetType));

        Map<FieldCode, MarketingFormField> configured = new LinkedHashMap<>();
        if (configuredFields != null) {
            for (MarketingFormField field : configuredFields) {
                FieldDefinition definition = definitions.get(field.fieldCode());
                if (definition == null || !isPubliclySupported(targetType, definition)) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Marketing form field is unavailable for public submission");
                }
                requireWrite(accesses, field.fieldCode());
                configured.put(field.fieldCode(), field);
            }
        }

        if (requireComplete) {
            for (FieldDefinition definition : activeFields) {
                if (!definition.isRequired()
                        || ownerField(targetType).equals(definition.getCode())) {
                    continue;
                }
                if (!isPubliclySupported(targetType, definition)) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "A required record field cannot be exposed by a marketing form");
                }
                MarketingFormField formField = configured.get(definition.getCode());
                if (formField == null || !formField.required()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Marketing form must include every required record field");
                }
            }
        }
        return new ValidatedConfiguration(ownerCaller, Map.copyOf(definitions));
    }

    /** Compatibility path for the dedicated public FILE pipeline, which only supports leads. */
    public ValidatedConfiguration validate(
            TenantId tenantId, MemberId ownerMemberId,
            Collection<MarketingFormField> configuredFields, boolean requireComplete) {
        return validate(tenantId, ownerMemberId, MarketingFormTargetType.LEAD,
                configuredFields, requireComplete);
    }

    private List<FieldDefinition> fields(TenantId tenantId, MarketingFormTargetType targetType) {
        return fieldRepository.findAll(tenantId, targetType.businessObjectType(), false);
    }

    private AuthenticatedCaller requireOwnerCaller(
            TenantId tenantId, MemberId ownerMemberId, MarketingFormTargetType targetType) {
        AuthenticatedCaller ownerCaller = new AuthenticatedCaller(
                ownerMemberId.getValue(), tenantId.getValue(), Set.of(writeAuthority(targetType)));
        if (targetType == MarketingFormTargetType.LEAD) {
            LeadAccessResolver.LeadAccess access = leadAccessResolver.access(
                    ownerCaller, AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE);
            if (!tenantId.sameValueAs(access.tenantId())
                    || !ownerMemberId.sameValueAs(access.memberId())) {
                throw error(ApplicationErrorCode.ACCESS_DENIED,
                        "Marketing form owner cannot create target records");
            }
        } else {
            CustomerAccessResolver.CustomerAccess access = customerAccessResolver.access(
                    ownerCaller, AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
            if (!tenantId.sameValueAs(access.tenantId())
                    || !ownerMemberId.sameValueAs(access.memberId())) {
                throw error(ApplicationErrorCode.ACCESS_DENIED,
                        "Marketing form owner cannot create target records");
            }
        }
        return ownerCaller;
    }

    private boolean isPubliclySupported(
            MarketingFormTargetType targetType, FieldDefinition definition) {
        if (definition.isBuiltin()) {
            return builtinFields(targetType).contains(definition.getCode().getValue());
        }
        return switch (definition.getFieldType()) {
            case FILE -> targetType == MarketingFormTargetType.LEAD;
            case USER, DEPARTMENT, RELATION, ADDRESS -> false;
            case TEXT, TEXTAREA, SELECT, MULTI_SELECT, DATE, DATETIME,
                    INTEGER, DECIMAL, BOOLEAN, EMAIL, PHONE, URL -> true;
        };
    }

    private static Set<String> builtinFields(MarketingFormTargetType targetType) {
        return targetType == MarketingFormTargetType.LEAD
                ? PUBLIC_LEAD_BUILTIN_FIELDS : PUBLIC_CUSTOMER_BUILTIN_FIELDS;
    }

    private static FieldCode nameField(MarketingFormTargetType targetType) {
        return new FieldCode(targetType == MarketingFormTargetType.LEAD
                ? LeadFieldCatalog.NAME : CustomerFieldCatalog.NAME);
    }

    private static FieldCode ownerField(MarketingFormTargetType targetType) {
        return new FieldCode(targetType == MarketingFormTargetType.LEAD
                ? LeadFieldCatalog.OWNER_ACTOR : CustomerFieldCatalog.OWNER_ACTOR);
    }

    private static String writeAuthority(MarketingFormTargetType targetType) {
        return targetType == MarketingFormTargetType.LEAD
                ? AuthorityCatalog.LEADS_WRITE : AuthorityCatalog.CUSTOMERS_WRITE;
    }

    private void requireWrite(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode fieldCode) {
        EffectiveFieldAccess access = accesses.get(fieldCode);
        if (access == null || access.accessLevel() != FieldAccessLevel.WRITE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Marketing form owner cannot write a configured record field");
        }
    }

    private NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    public record ValidatedConfiguration(
            AuthenticatedCaller ownerCaller,
            Map<FieldCode, FieldDefinition> definitions) {
    }
}
