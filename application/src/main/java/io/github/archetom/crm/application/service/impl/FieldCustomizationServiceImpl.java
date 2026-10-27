package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldLayoutUpdateRequest;
import io.github.archetom.crm.api.dto.request.FieldOptionRequest;
import io.github.archetom.crm.api.dto.request.FieldUpdateRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldAccessRequest;
import io.github.archetom.crm.api.dto.request.RoleFieldPolicyReplaceRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.FieldCustomizationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.BusinessObjectVO;
import io.github.archetom.crm.application.vo.FieldDefinitionVO;
import io.github.archetom.crm.application.vo.FieldLayoutVO;
import io.github.archetom.crm.application.vo.FieldOptionVO;
import io.github.archetom.crm.application.vo.FilterOperatorOptionVO;
import io.github.archetom.crm.application.vo.RoleFieldAccessVO;
import io.github.archetom.crm.application.vo.RoleFieldPolicyVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FieldLayoutPreference;
import io.github.archetom.crm.domain.entity.FieldOption;
import io.github.archetom.crm.domain.entity.RoleFieldAccess;
import io.github.archetom.crm.domain.entity.RoleFieldPolicy;
import io.github.archetom.crm.domain.entity.TenantRole;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.RoleAssignmentPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.FieldLayoutPreferenceRepository;
import io.github.archetom.crm.domain.repository.RoleFieldPolicyRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.ReportDefinitionRepository;
import io.github.archetom.crm.domain.repository.TenantRoleRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.FieldDefinitionId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.RoleId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FieldCustomizationServiceImpl implements FieldCustomizationService {

    private final FieldDefinitionRepository fieldRepository;
    private final FileFieldRepository fileFieldRepository;
    private final FieldLayoutPreferenceRepository layoutRepository;
    private final RoleFieldPolicyRepository policyRepository;
    private final SavedViewRepository savedViewRepository;
    private final ApprovalDefinitionRepository approvalDefinitionRepository;
    private final StageWorkflowDefinitionRepository stageWorkflowRepository;
    private final MarketingFormRepository marketingFormRepository;
    private final OaFormRepository oaFormRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final TenantRoleRepository roleRepository;
    private final CustomizationObjectLock objectLock;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<List<BusinessObjectVO>> listObjects(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.FIELD_OBJECT_LIST, new ServiceOperation<>() {
            @Override
            public void validate() {
                accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_READ);
            }

            @Override
            public List<BusinessObjectVO> execute() {
                return java.util.Arrays.stream(BusinessObjectType.values())
                        .map(value -> new BusinessObjectVO(value.name(), displayName(value)))
                        .toList();
            }
        });
    }

    @Override
    public Result<List<FieldDefinitionVO>> listFields(
            AuthenticatedCaller caller, String objectType, boolean includeInactive) {
        return queryTemplate.execute(UseCaseOperation.FIELD_DEFINITION_LIST, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_READ);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                if (includeInactive && !caller.hasAuthority(AuthorityCatalog.FIELDS_WRITE)) {
                    throw error(ApplicationErrorCode.ACCESS_DENIED,
                            "Inactive fields require field management authority");
                }
            }

            @Override
            public List<FieldDefinitionVO> execute() {
                return fieldRepository.findAll(tenantId, type, includeInactive).stream()
                        .map(FieldCustomizationServiceImpl.this::toVO).toList();
            }
        });
    }

    @Override
    public Result<FieldDefinitionVO> createField(
            AuthenticatedCaller caller, String objectType, FieldCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.FIELD_DEFINITION_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private CustomFieldType fieldType;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_WRITE);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                require(request, "Field create request");
                fieldType = parse(CustomFieldType.class, request.getFieldType(), "Field type");
            }

            @Override
            public FieldDefinitionVO execute() {
                lockObject(tenantId, type);
                FieldDefinition field = FieldDefinition.createCustom(
                        tenantId, type, new FieldCode("cf_" + UUID.randomUUID().toString().replace("-", "")),
                        request.getDisplayName(), request.getDescription(), fieldType,
                        Boolean.TRUE.equals(request.getRequired()), Boolean.TRUE.equals(request.getUnique()),
                        !Boolean.FALSE.equals(request.getSearchable()), value(request.getDisplayOrder()),
                        request.getSectionName(), options(request.getOptions()));
                return toVO(fieldRepository.save(tenantId, field));
            }
        });
    }

    @Override
    public Result<FieldDefinitionVO> updateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, FieldUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.FIELD_DEFINITION_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private FieldDefinitionId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_WRITE);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                id = new FieldDefinitionId(fieldId);
                require(request, "Field update request");
            }

            @Override
            public FieldDefinitionVO execute() {
                lockObject(tenantId, type);
                FieldDefinition field = requireField(tenantId, type, id);
                requireVersion(field.getVersion(), request.getVersion(), "Field version is stale");
                boolean searchable = !Boolean.FALSE.equals(request.getSearchable());
                if (field.isSearchable() && !searchable
                        && (savedViewRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || approvalDefinitionRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || stageWorkflowRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || reportDefinitionRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || (type == BusinessObjectType.OA_REQUEST
                        && oaFormRepository.existsActiveReferencingField(
                        tenantId, field.getCode())))) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Field is referenced by searchable configuration");
                }
                field.update(request.getDisplayName(), request.getDescription(),
                        Boolean.TRUE.equals(request.getRequired()),
                        request.getUnique() == null ? field.isUnique() : request.getUnique(),
                        searchable, value(request.getDisplayOrder()),
                        request.getSectionName(), options(request.getOptions()));
                return toVO(fieldRepository.save(tenantId, field));
            }
        });
    }

    @Override
    public Result<Void> deactivateField(
            AuthenticatedCaller caller, String objectType, Long fieldId, Long version) {
        return commandTemplate.execute(UseCaseOperation.FIELD_DEFINITION_DEACTIVATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private FieldDefinitionId id;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_WRITE);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                id = new FieldDefinitionId(fieldId);
            }

            @Override
            public Void execute() {
                lockObject(tenantId, type);
                FieldDefinition field = requireField(tenantId, type, id);
                requireVersion(field.getVersion(), version, "Field version is stale");
                if (layoutRepository.existsReferencingField(tenantId, type, field.getCode())
                        || savedViewRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || approvalDefinitionRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || stageWorkflowRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || reportDefinitionRepository.existsReferencingField(
                        tenantId, type, field.getCode())
                        || (type == BusinessObjectType.LEAD
                        && marketingFormRepository.existsActiveReferencingField(
                        tenantId, field.getCode()))
                        || (type == BusinessObjectType.OA_REQUEST
                        && oaFormRepository.existsActiveReferencingField(
                        tenantId, field.getCode()))
                        || (field.getFieldType() == CustomFieldType.FILE
                        && fileFieldRepository.hasCurrentDependency(tenantId, field))) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Field is referenced by active configuration");
                }
                field.deactivate();
                fieldRepository.save(tenantId, field);
                return null;
            }
        });
    }

    @Override
    public Result<FieldLayoutVO> getLayout(AuthenticatedCaller caller, String objectType) {
        return queryTemplate.execute(UseCaseOperation.FIELD_LAYOUT_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_READ);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                memberId = new MemberId(caller.actorId());
            }

            @Override
            public FieldLayoutVO execute() {
                List<FieldDefinition> activeFields = fieldRepository.findAll(tenantId, type, false);
                Set<FieldCode> activeCodes = activeFields.stream()
                        .map(FieldDefinition::getCode).collect(Collectors.toSet());
                return layoutRepository.findByMember(tenantId, memberId, type)
                        .map(preference -> toVO(preference, activeCodes))
                        .orElseGet(() -> defaultLayout(type, activeFields));
            }
        });
    }

    @Override
    public Result<FieldLayoutVO> updateLayout(
            AuthenticatedCaller caller, String objectType, FieldLayoutUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.FIELD_LAYOUT_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private BusinessObjectType type;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_READ);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                memberId = new MemberId(caller.actorId());
                require(request, "Field layout request");
            }

            @Override
            public FieldLayoutVO execute() {
                lockObject(tenantId, type);
                Set<FieldCode> activeCodes = fieldRepository.findAll(tenantId, type, false).stream()
                        .map(FieldDefinition::getCode).collect(Collectors.toSet());
                FieldLayoutPreference preference = layoutRepository.findByMember(tenantId, memberId, type)
                        .map(existing -> changeLayout(existing, request, activeCodes))
                        .orElseGet(() -> createLayout(request, activeCodes));
                return toVO(layoutRepository.save(tenantId, preference), activeCodes);
            }

            private FieldLayoutPreference createLayout(
                    FieldLayoutUpdateRequest value, Set<FieldCode> activeCodes) {
                if (value.getVersion() != null) {
                    throw versionConflict("Field layout version is stale");
                }
                return FieldLayoutPreference.create(tenantId, memberId, type,
                        codes(value.getOrderedCodes()), codes(value.getHiddenCodes()),
                        codes(value.getPinnedCodes()), widths(value.getColumnWidths()), activeCodes);
            }

            private FieldLayoutPreference changeLayout(
                    FieldLayoutPreference existing, FieldLayoutUpdateRequest value,
                    Set<FieldCode> activeCodes) {
                requireVersion(existing.getVersion(), value.getVersion(), "Field layout version is stale");
                existing.change(codes(value.getOrderedCodes()), codes(value.getHiddenCodes()),
                        codes(value.getPinnedCodes()), widths(value.getColumnWidths()), activeCodes);
                return existing;
            }
        });
    }

    @Override
    public Result<RoleFieldPolicyVO> getRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType) {
        return queryTemplate.execute(UseCaseOperation.ROLE_FIELD_POLICY_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_WRITE);
                requireAuthority(caller, AuthorityCatalog.AUTHORIZATION_READ);
                id = new RoleId(roleId);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
            }

            @Override
            public RoleFieldPolicyVO execute() {
                TenantRole role = requireRole(tenantId, id);
                List<FieldDefinition> fields = fieldRepository.findAll(tenantId, type, false);
                RoleFieldPolicy policy = policyRepository.find(tenantId, id, type).orElse(null);
                return policyVO(role, type, fields, policy);
            }
        });
    }

    @Override
    public Result<RoleFieldPolicyVO> replaceRolePolicy(
            AuthenticatedCaller caller, Long roleId, String objectType,
            RoleFieldPolicyReplaceRequest request) {
        return commandTemplate.execute(UseCaseOperation.ROLE_FIELD_POLICY_REPLACE, new ServiceOperation<>() {
            private TenantId tenantId;
            private RoleId id;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FIELDS_WRITE);
                requireAuthority(caller, AuthorityCatalog.AUTHORIZATION_WRITE);
                requireAuthority(caller, AuthorityCatalog.AUTHORIZATION_ASSIGN);
                id = new RoleId(roleId);
                type = parse(BusinessObjectType.class, objectType, "Business object type");
                require(request, "Role field policy request");
            }

            @Override
            public RoleFieldPolicyVO execute() {
                TenantRole role = requireRole(tenantId, id);
                if (!role.isActive() || role.isSystemRole()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Only active custom roles can have field policies");
                }
                RoleAssignmentPolicy.requireDelegable(role, authorizationEvaluator.evaluate(
                        tenantId, new MemberId(caller.actorId())).effectiveGrants());
                List<FieldDefinition> fields = fieldRepository.findAll(tenantId, type, false);
                Map<FieldCode, FieldDefinition> byCode = fields.stream().collect(Collectors.toMap(
                        FieldDefinition::getCode, Function.identity()));
                List<RoleFieldAccess> accesses = accesses(request.getAccesses(), byCode);
                RoleFieldPolicy policy = policyRepository.find(tenantId, id, type)
                        .map(existing -> replace(existing, request, accesses))
                        .orElseGet(() -> create(request, accesses));
                return policyVO(role, type, fields, policyRepository.save(tenantId, policy));
            }

            private RoleFieldPolicy create(RoleFieldPolicyReplaceRequest value,
                                           List<RoleFieldAccess> accesses) {
                if (value.getVersion() != null) {
                    throw versionConflict("Role field policy version is stale");
                }
                return RoleFieldPolicy.create(tenantId, id, type, accesses);
            }

            private RoleFieldPolicy replace(RoleFieldPolicy existing,
                                            RoleFieldPolicyReplaceRequest value,
                                            List<RoleFieldAccess> accesses) {
                requireVersion(existing.getVersion(), value.getVersion(),
                        "Role field policy version is stale");
                existing.replace(accesses);
                return existing;
            }
        });
    }

    private FieldDefinition requireField(
            TenantId tenantId, BusinessObjectType type, FieldDefinitionId id) {
        return fieldRepository.findById(tenantId, type, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Field definition was not found"));
    }

    private TenantRole requireRole(TenantId tenantId, RoleId id) {
        return roleRepository.findById(tenantId, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Tenant role was not found"));
    }

    private void lockObject(TenantId tenantId, BusinessObjectType objectType) {
        objectLock.acquire(tenantId, objectType);
    }

    private FieldLayoutVO defaultLayout(
            BusinessObjectType type, List<FieldDefinition> activeFields) {
        List<String> ordered = activeFields.stream()
                .map(field -> field.getCode().getValue()).toList();
        return new FieldLayoutVO(type.name(), ordered, Set.of(), Set.of(), Map.of(), null);
    }

    private RoleFieldPolicyVO policyVO(TenantRole role, BusinessObjectType type,
                                       List<FieldDefinition> fields, RoleFieldPolicy policy) {
        Map<FieldCode, RoleFieldAccess> configured = policy == null ? Map.of()
                : policy.getAccesses().stream().collect(Collectors.toMap(
                RoleFieldAccess::fieldCode, Function.identity()));
        boolean owner = role.getSystemKey() == io.github.archetom.crm.domain.model.SystemRoleKey.TENANT_OWNER
                && role.isActive();
        List<RoleFieldAccessVO> accesses = fields.stream().map(field -> {
            RoleFieldAccess access = configured.get(field.getCode());
            String level = owner ? FieldAccessLevel.WRITE.name()
                    : access == null ? FieldAccessLevel.HIDDEN.name() : access.accessLevel().name();
            String mask = owner ? FieldMaskType.NONE.name()
                    : access == null ? FieldMaskType.FULL.name() : access.maskType().name();
            return new RoleFieldAccessVO(field.getId().getValue(), field.getCode().getValue(),
                    field.getDisplayName(), level, mask);
        }).toList();
        return new RoleFieldPolicyVO(role.getId().getValue(), type.name(), accesses,
                policy == null ? null : policy.getVersion());
    }

    private List<RoleFieldAccess> accesses(List<RoleFieldAccessRequest> requests,
                                           Map<FieldCode, FieldDefinition> fields) {
        List<RoleFieldAccess> result = new java.util.ArrayList<>();
        Set<FieldCode> seen = new LinkedHashSet<>();
        for (RoleFieldAccessRequest request : requests == null ? List.<RoleFieldAccessRequest>of() : requests) {
            if (request == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role field access is invalid");
            }
            FieldCode code = new FieldCode(request.getFieldCode());
            if (!seen.add(code)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID, "Role field accesses must be unique");
            }
            FieldDefinition field = fields.get(code);
            if (field == null) {
                throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Role field access references an unknown field");
            }
            FieldAccessLevel level = parse(FieldAccessLevel.class, request.getAccessLevel(),
                    "Field access level");
            FieldMaskType mask = parse(FieldMaskType.class, request.getMaskType(), "Field mask type");
            if (level != FieldAccessLevel.HIDDEN) {
                result.add(new RoleFieldAccess(field.getId(), code, level, mask));
            }
        }
        return List.copyOf(result);
    }

    private List<FieldOption> options(List<FieldOptionRequest> requests) {
        return (requests == null ? List.<FieldOptionRequest>of() : requests).stream()
                .map(request -> {
                    if (request == null) {
                        throw error(ApplicationErrorCode.PARAMETER_INVALID, "Field option is invalid");
                    }
                    return new FieldOption(request.getValue(), request.getLabel(), value(request.getOrder()));
                }).toList();
    }

    private List<FieldCode> codes(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(FieldCode::new).toList();
    }

    private Map<FieldCode, Integer> widths(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<FieldCode, Integer> result = new LinkedHashMap<>();
        values.forEach((code, width) -> result.put(new FieldCode(code), width));
        return result;
    }

    private FieldDefinitionVO toVO(FieldDefinition field) {
        return new FieldDefinitionVO(field.getId().getValue(), field.getObjectType().name(),
                field.getCode().getValue(), field.getDisplayName(), field.getDescription(),
                field.getFieldType().name(), field.isBuiltin(), field.isRequired(), field.isUnique(),
                field.isSearchable(), field.getStatus().name(), field.getDisplayOrder(),
                field.getSectionName(), field.getOptions().stream()
                .map(option -> new FieldOptionVO(option.value(), option.label(), option.order())).toList(),
                SavedViewPolicy.supportedOperators(field.getFieldType()).stream().sorted()
                        .map(operator -> new FilterOperatorOptionVO(operator.name(),
                                operator.minimumValueCount(), operator.maximumValueCount())).toList(),
                field.getVersion(), field.getCreatedTime(), field.getUpdatedTime());
    }

    private FieldLayoutVO toVO(
            FieldLayoutPreference preference, Set<FieldCode> activeCodes) {
        return new FieldLayoutVO(preference.getObjectType().name(),
                preference.getOrderedCodes().stream().filter(activeCodes::contains)
                        .map(FieldCode::getValue).toList(),
                preference.getHiddenCodes().stream().filter(activeCodes::contains)
                        .map(FieldCode::getValue).collect(Collectors.toSet()),
                preference.getPinnedCodes().stream().filter(activeCodes::contains)
                        .map(FieldCode::getValue).collect(Collectors.toSet()),
                preference.getColumnWidths().entrySet().stream()
                        .filter(entry -> activeCodes.contains(entry.getKey())).collect(Collectors.toMap(
                        entry -> entry.getKey().getValue(), Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new)), preference.getVersion());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String displayName(BusinessObjectType value) {
        String[] words = value.name().toLowerCase(Locale.ROOT).split("_");
        return java.util.Arrays.stream(words)
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    private void requireAuthority(AuthenticatedCaller caller, String authority) {
        if (!caller.hasAuthority(authority)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED, "Caller lacks a required authority");
        }
    }

    private void requireVersion(Long actual, Long requested, String message) {
        if (requested == null || !requested.equals(actual)) {
            throw versionConflict(message);
        }
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " must not be empty");
        }
    }

    private NonRetryableApplicationException versionConflict(String message) {
        return error(ApplicationErrorCode.VERSION_CONFLICT, message);
    }

    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
