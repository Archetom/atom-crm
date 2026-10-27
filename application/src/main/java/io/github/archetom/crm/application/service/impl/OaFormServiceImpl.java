package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OaFormCreateRequest;
import io.github.archetom.crm.api.dto.request.OaFormFieldRequest;
import io.github.archetom.crm.api.dto.request.OaFormQueryRequest;
import io.github.archetom.crm.api.dto.request.OaFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.OaFormUpdateRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.OaFormService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.FieldOptionVO;
import io.github.archetom.crm.application.vo.OaFormFieldVO;
import io.github.archetom.crm.application.vo.OaFormApprovalDefinitionCandidateVO;
import io.github.archetom.crm.application.vo.OaFormApprovalSelectionVO;
import io.github.archetom.crm.application.vo.OaFormConfigurationFieldVO;
import io.github.archetom.crm.application.vo.OaFormConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.OaFormFieldCandidateVO;
import io.github.archetom.crm.application.vo.OaFormVO;
import io.github.archetom.crm.application.vo.ApprovalSelectionCandidateVO;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.OaFormDefinition;
import io.github.archetom.crm.domain.entity.OaFormField;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.ApprovalStepType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.OaFormStatus;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.policy.IdentifierKeyNormalizer;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.OaFormRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OaFormServiceImpl implements OaFormService {

    private static final int MAX_FORMS = 200;

    private final OaFormRepository formRepository;
    private final OaRequestRepository requestRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final ApprovalDefinitionRepository approvalRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OrganizationAccessGuard accessGuard;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<OaFormConfigurationOptionsVO> configurationOptions(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_CONFIGURATION_OPTIONS,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_WRITE);
                        memberId = new MemberId(caller.actorId());
                    }

                    @Override
                    public OaFormConfigurationOptionsVO execute() {
                        List<FieldDefinition> definitions = fieldRepository.findAll(
                                tenantId, BusinessObjectType.OA_REQUEST, false);
                        Map<FieldCode, EffectiveFieldAccess> accesses = fieldAccessResolver.resolve(
                                tenantId, memberId, BusinessObjectType.OA_REQUEST, definitions);
                        List<FieldDefinition> fields = definitions.stream()
                                .filter(FieldDefinition::isActive)
                                .filter(field -> !OaRequestFieldCatalog.serverDerivedCodes()
                                        .contains(field.getCode()))
                                .filter(field -> field.getFieldType() != CustomFieldType.RELATION)
                                .filter(field -> accesses.getOrDefault(field.getCode(),
                                        EffectiveFieldAccess.hidden()).accessLevel()
                                        .covers(FieldAccessLevel.WRITE))
                                .sorted(Comparator.comparing(field -> field.getCode().getValue()))
                                .toList();
                        Set<FieldCode> allowedCodes = fields.stream().map(FieldDefinition::getCode)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
                        List<OaFormApprovalDefinitionCandidateVO> approvals = approvalRepository
                                .findActiveByObjectType(tenantId, BusinessObjectType.OA_REQUEST)
                                .stream()
                                .map(approval -> approvalCandidate(approval, allowedCodes))
                                .filter(java.util.Objects::nonNull)
                                .sorted(Comparator.comparing(OaFormApprovalDefinitionCandidateVO::name)
                                        .thenComparing(OaFormApprovalDefinitionCandidateVO::id))
                                .toList();
                        return new OaFormConfigurationOptionsVO(approvals, fields.stream()
                                .map(field -> new OaFormConfigurationFieldVO(
                                        field.getCode().getValue(), field.getDisplayName(),
                                        field.getFieldType().name(), field.isRequired()))
                                .toList());
                    }
                });
    }

    @Override
    public Result<Pager<OaFormVO>> query(
            AuthenticatedCaller caller, OaFormQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormStatus status;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_READ);
                        memberId = new MemberId(caller.actorId());
                        require(request, "OA form query");
                        validatePage(request);
                        status = parseOptionalStatus(request.getStatus());
                        if (status == OaFormStatus.DELETED) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Deleted OA forms cannot be queried");
                        }
                    }

                    @Override
                    public Pager<OaFormVO> execute() {
                        PageResult<OaFormDefinition> page = formRepository.findPage(
                                tenantId, status, request.getPage(), request.getSize());
                        FormReadContext context = readContext(
                                tenantId, memberId, page.items());
                        Pager<OaFormVO> result = new Pager<>();
                        result.setPageNum(page.page());
                        result.setPageSize(page.size());
                        result.setTotalNum(page.total());
                        result.setObjectList(page.items().stream()
                                .map(form -> toVO(form, context)).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<OaFormVO> get(AuthenticatedCaller caller, Long formId) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_READ);
                        memberId = new MemberId(caller.actorId());
                        id = formId(formId);
                    }

                    @Override
                    public OaFormVO execute() {
                        OaFormDefinition form = requireForm(tenantId, id);
                        return toVO(form, readContext(
                                tenantId, memberId, List.of(form)));
                    }
                });
    }

    @Override
    public Result<List<OaFormVO>> available(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_AVAILABLE_LIST,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_READ);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                    }

                    @Override
                    public List<OaFormVO> execute() {
                        List<OaFormDefinition> forms =
                                formRepository.findAllActive(tenantId, MAX_FORMS);
                        FormReadContext context = readContext(tenantId, memberId, forms);
                        var membership = membershipRepository.findByMember(tenantId, memberId)
                                .filter(value -> value.isActive())
                                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED,
                                        "Caller is not an active tenant member"));
                        return forms.stream()
                                .filter(form -> isAvailable(form, membership.getDepartmentId(), context))
                                .map(form -> toVO(form, context)).toList();
                    }
                });
    }

    @Override
    public Result<OaFormVO> getAvailable(AuthenticatedCaller caller, Long formId) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_AVAILABLE_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_READ);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        id = formId(formId);
                    }

                    @Override
                    public OaFormVO execute() {
                        OaFormDefinition form = requireForm(tenantId, id);
                        FormReadContext context = readContext(
                                tenantId, memberId, List.of(form));
                        var membership = membershipRepository.findByMember(tenantId, memberId)
                                .filter(value -> value.isActive())
                                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED,
                                        "Caller is not an active tenant member"));
                        if (!isAvailable(form, membership.getDepartmentId(), context)) {
                            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                    "OA form was not found");
                        }
                        return toVO(form, context);
                    }
                });
    }

    @Override
    public Result<Pager<OaFormFieldCandidateVO>> fieldCandidates(
            AuthenticatedCaller caller, Long formId, String fieldCode,
            String keyword, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_FIELD_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId id;
                    private FieldCode code;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_READ);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        id = formId(formId);
                        code = fieldCode(fieldCode);
                        pageNumber = candidatePage(page);
                        pageSize = candidateSize(size);
                    }

                    @Override
                    public Pager<OaFormFieldCandidateVO> execute() {
                        AvailableFormContext available = availableContext(tenantId, memberId, id);
                        if (available.form().getFields().stream()
                                .noneMatch(field -> field.fieldCode().equals(code))) {
                            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                    "OA form field was not found");
                        }
                        FieldDefinition field = available.context().definitions().get(code);
                        if (field == null) {
                            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                    "OA form field was not found");
                        }
                        return switch (field.getFieldType()) {
                            case USER -> actorCandidates(tenantId, keyword, pageNumber, pageSize);
                            case DEPARTMENT -> departmentCandidates(
                                    tenantId, keyword, pageNumber, pageSize);
                            default -> throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "OA form field has no candidate catalog");
                        };
                    }
                });
    }

    @Override
    public Result<Pager<ApprovalSelectionCandidateVO>> approvalSelectionCandidates(
            AuthenticatedCaller caller, Long formId, String selectionCode,
            String keyword, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.OA_FORM_APPROVAL_SELECTION_CANDIDATE_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId id;
                    private String code;
                    private String searchKeyword;
                    private int pageNumber;
                    private int pageSize;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_READ);
                        accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_REQUESTS_SUBMIT);
                        memberId = new MemberId(caller.actorId());
                        id = formId(formId);
                        code = selectionCode == null ? null : selectionCode.trim();
                        searchKeyword = blankToNull(keyword);
                        pageNumber = candidatePage(page);
                        pageSize = candidateSize(size);
                        if (code == null || code.isEmpty()) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "OA approval selection code is invalid");
                        }
                    }

                    @Override
                    public Pager<ApprovalSelectionCandidateVO> execute() {
                        AvailableFormContext available = availableContext(tenantId, memberId, id);
                        ApprovalDefinition approval = available.context().approvals().get(
                                available.form().getApprovalDefinitionId());
                        boolean exists = approval.getSteps().stream().anyMatch(step ->
                                step.type() == ApprovalStepType.APPLICANT_SELECT
                                        && step.code().equals(code));
                        if (!exists) {
                            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                                    "OA approval selection was not found");
                        }
                        PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                                tenantId, searchKeyword, pageNumber, pageSize);
                        Pager<ApprovalSelectionCandidateVO> result = new Pager<>();
                        result.setPageNum(profiles.page());
                        result.setPageSize(profiles.size());
                        result.setTotalNum(profiles.total());
                        result.setObjectList(profiles.items().stream().filter(MemberProfile::isActive)
                                .map(profile -> new ApprovalSelectionCandidateVO(
                                        profile.getId().getValue(), profile.getDisplayName())).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<OaFormVO> create(
            AuthenticatedCaller caller, OaFormCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_FORM_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private ApprovalDefinitionId approvalId;
                    private List<OaFormField> fields;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        require(request, "OA form create request");
                        approvalId = approvalId(request.getApprovalDefinitionId());
                        fields = fields(request.getFields());
                    }

                    @Override
                    public OaFormVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
                        if (formRepository.countNonDeleted(tenantId) >= MAX_FORMS) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "OA form limit has been reached");
                        }
                        ValidatedConfiguration validated = validateConfiguration(
                                tenantId, approvalId, fields, false);
                        OaFormDefinition form = OaFormDefinition.create(
                                tenantId, memberId, request.getCode(), request.getName(),
                                request.getDescription(), approvalId, fields);
                        OaFormDefinition saved = formRepository.save(tenantId, form);
                        return toVO(saved, readContext(
                                tenantId, memberId, List.of(saved), validated));
                    }
                });
    }

    @Override
    public Result<OaFormVO> update(
            AuthenticatedCaller caller, Long formId, OaFormUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_FORM_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId id;
                    private ApprovalDefinitionId approvalId;
                    private List<OaFormField> fields;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = formId(formId);
                        require(request, "OA form update request");
                        approvalId = approvalId(request.getApprovalDefinitionId());
                        fields = fields(request.getFields());
                    }

                    @Override
                    public OaFormVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
                        OaFormDefinition form = requireFormForUpdate(tenantId, id);
                        requireVersion(form, request.getVersion());
                        if (!form.getNormalizedCode().equals(
                                IdentifierKeyNormalizer.normalize(request.getCode()))
                                && requestRepository.existsByForm(tenantId, id)) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "OA form code cannot change after a request is submitted");
                        }
                        ValidatedConfiguration validated = validateConfiguration(
                                tenantId, approvalId, fields, form.isActive());
                        form.update(request.getCode(), request.getName(),
                                request.getDescription(), approvalId, fields);
                        OaFormDefinition saved = formRepository.save(tenantId, form);
                        return toVO(saved, readContext(
                                tenantId, memberId, List.of(saved), validated));
                    }
                });
    }

    @Override
    public Result<OaFormVO> updateStatus(
            AuthenticatedCaller caller, Long formId,
            OaFormStatusUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OA_FORM_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId memberId;
                    private OaFormId id;
                    private OaFormStatus status;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_WRITE);
                        memberId = new MemberId(caller.actorId());
                        id = formId(formId);
                        require(request, "OA form status request");
                        status = parseStatus(request.getStatus());
                        if (status == OaFormStatus.DELETED) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "OA form status is invalid");
                        }
                    }

                    @Override
                    public OaFormVO execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
                        OaFormDefinition form = requireFormForUpdate(tenantId, id);
                        requireVersion(form, request.getVersion());
                        if (status == OaFormStatus.ACTIVE) {
                            ValidatedConfiguration validated = validateConfiguration(
                                    tenantId, form.getApprovalDefinitionId(),
                                    form.getFields(), true);
                            form.activate();
                            OaFormDefinition saved = formRepository.save(tenantId, form);
                            return toVO(saved, readContext(
                                    tenantId, memberId, List.of(saved), validated));
                        }
                        form.deactivate();
                        OaFormDefinition saved = formRepository.save(tenantId, form);
                        return toVO(saved, readContext(
                                tenantId, memberId, List.of(saved)));
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long formId, Long version) {
        return commandTemplate.execute(UseCaseOperation.OA_FORM_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private OaFormId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.OA_FORMS_WRITE);
                        id = formId(formId);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(tenantId, BusinessObjectType.OA_REQUEST);
                        OaFormDefinition form = requireFormForUpdate(tenantId, id);
                        requireVersion(form, version);
                        form.delete();
                        formRepository.save(tenantId, form);
                        return null;
                    }
                });
    }

    private ValidatedConfiguration validateConfiguration(
            TenantId tenantId, ApprovalDefinitionId approvalId,
            Collection<OaFormField> configuredFields, boolean requireActive) {
        Set<FieldCode> configuredCodes = configuredFields.stream()
                .map(OaFormField::fieldCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<FieldCode, FieldDefinition> definitions = fieldRepository.findByCodes(
                        tenantId, BusinessObjectType.OA_REQUEST, configuredCodes).stream()
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        for (OaFormField field : configuredFields) {
            FieldDefinition definition = definitions.get(field.fieldCode());
            if (definition == null || !definition.isActive()) {
                throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "OA form references an unavailable field");
            }
            if (OaRequestFieldCatalog.serverDerivedCodes().contains(field.fieldCode())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Server-derived OA fields cannot be configured as inputs");
            }
        }
        ApprovalDefinition approval = requireApproval(tenantId, approvalId);
        if (approval.getObjectType() != BusinessObjectType.OA_REQUEST
                || approval.getStatus() == ApprovalDefinitionStatus.DELETED) {
            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "OA approval definition was not found");
        }
        if (requireActive && !approval.isActive()) {
            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                    "OA form activation requires an active approval definition");
        }
        if (requireActive) {
            Set<FieldCode> allowedFacts = new LinkedHashSet<>(configuredCodes);
            allowedFacts.addAll(OaRequestFieldCatalog.serverDerivedCodes());
            Set<FieldCode> conditionFields = approval.getSteps().stream()
                    .flatMap(step -> step.conditions().stream())
                    .map(condition -> condition.fieldCode())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!allowedFacts.containsAll(conditionFields)) {
                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                        "Approval conditions reference fields outside the OA form");
            }
        }
        return new ValidatedConfiguration(approval, definitions);
    }

    private OaFormApprovalDefinitionCandidateVO approvalCandidate(
            ApprovalDefinition approval, Set<FieldCode> allowedCodes) {
        List<FieldCode> requiredCodes = approval.getSteps().stream()
                .flatMap(step -> step.conditions().stream())
                .map(condition -> condition.fieldCode())
                .filter(code -> !OaRequestFieldCatalog.serverDerivedCodes().contains(code))
                .distinct()
                .toList();
        if (!allowedCodes.containsAll(requiredCodes)) {
            return null;
        }
        return new OaFormApprovalDefinitionCandidateVO(approval.getId().getValue(),
                approval.getName(), requiredCodes.stream().map(FieldCode::getValue).toList());
    }

    private boolean isAvailable(
            OaFormDefinition form,
            io.github.archetom.crm.domain.valueobject.DepartmentId departmentId,
            FormReadContext context) {
        return OaFormAvailability.isAvailable(form, context.memberId(), departmentId,
                context.approvals().get(form.getApprovalDefinitionId()), context.definitions(),
                context.accesses());
    }

    private AvailableFormContext availableContext(
            TenantId tenantId, MemberId memberId, OaFormId id) {
        OaFormDefinition form = requireForm(tenantId, id);
        FormReadContext context = readContext(tenantId, memberId, List.of(form));
        var membership = membershipRepository.findByMember(tenantId, memberId)
                .filter(value -> value.isActive())
                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED,
                        "Caller is not an active tenant member"));
        if (!isAvailable(form, membership.getDepartmentId(), context)) {
            throw error(ApplicationErrorCode.RESOURCE_NOT_FOUND, "OA form was not found");
        }
        return new AvailableFormContext(form, context);
    }

    private Pager<OaFormFieldCandidateVO> actorCandidates(
            TenantId tenantId, String keyword, int page, int size) {
        PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                tenantId, blankToNull(keyword), page, size);
        Pager<OaFormFieldCandidateVO> result = new Pager<>();
        result.setPageNum(profiles.page());
        result.setPageSize(profiles.size());
        result.setTotalNum(profiles.total());
        result.setObjectList(profiles.items().stream().filter(MemberProfile::isActive)
                .map(profile -> new OaFormFieldCandidateVO(profile.getId().getValue(),
                        profile.getDisplayName(), "ACTOR_ID")).toList());
        return result;
    }

    private Pager<OaFormFieldCandidateVO> departmentCandidates(
            TenantId tenantId, String keyword, int page, int size) {
        String normalized = blankToNull(keyword);
        List<Department> departments = departmentRepository.findAll(tenantId).stream()
                .filter(Department::isActive)
                .filter(value -> normalized == null || value.getName().toLowerCase(Locale.ROOT)
                        .contains(normalized.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Department::getName)
                        .thenComparing(value -> value.getId().getValue()))
                .toList();
        int from = Math.min((page - 1) * size, departments.size());
        int to = Math.min(from + size, departments.size());
        Pager<OaFormFieldCandidateVO> result = new Pager<>();
        result.setPageNum((long) page);
        result.setPageSize((long) size);
        result.setTotalNum((long) departments.size());
        result.setObjectList(departments.subList(from, to).stream().map(value ->
                new OaFormFieldCandidateVO(value.getId().getValue(), value.getName(),
                        "DEPARTMENT_ID")).toList());
        return result;
    }

    private FormReadContext readContext(
            TenantId tenantId, MemberId memberId,
            Collection<OaFormDefinition> forms) {
        Set<FieldCode> fieldCodes = forms.stream()
                .flatMap(form -> form.getFields().stream())
                .map(OaFormField::fieldCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<ApprovalDefinitionId> approvalIds = forms.stream()
                .map(OaFormDefinition::getApprovalDefinitionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<FieldCode, FieldDefinition> definitions = fieldRepository.findByCodes(
                        tenantId, BusinessObjectType.OA_REQUEST, fieldCodes).stream()
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<ApprovalDefinitionId, ApprovalDefinition> approvals = approvalRepository.findByIds(
                        tenantId, approvalIds).stream()
                .collect(Collectors.toMap(ApprovalDefinition::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        return new FormReadContext(memberId, definitions, approvals,
                fieldAccessResolver.resolve(
                        tenantId, memberId, BusinessObjectType.OA_REQUEST,
                        definitions.values()));
    }

    private FormReadContext readContext(
            TenantId tenantId, MemberId memberId,
            Collection<OaFormDefinition> forms,
            ValidatedConfiguration validated) {
        FormReadContext context = readContext(tenantId, memberId, forms);
        Map<ApprovalDefinitionId, ApprovalDefinition> approvals =
                new LinkedHashMap<>(context.approvals());
        approvals.put(validated.approval().getId(), validated.approval());
        Map<FieldCode, FieldDefinition> definitions = new LinkedHashMap<>(context.definitions());
        definitions.putAll(validated.definitions());
        return new FormReadContext(memberId, definitions, approvals, context.accesses());
    }

    private OaFormVO toVO(OaFormDefinition form, FormReadContext context) {
        ApprovalDefinition approval = context.approvals().get(form.getApprovalDefinitionId());
        if (approval == null) {
            approval = requireApproval(form.getTenantId(), form.getApprovalDefinitionId());
        }
        return new OaFormVO(form.getId().getValue(), form.getCode(), form.getName(),
                form.getDescription(), approval.getId().getValue(), approval.getName(),
                approval.getStatus().name(), form.getCreatedByMemberId().getValue(),
                form.getStatus().name(), approval.getSteps().stream()
                        .filter(step -> step.type() == ApprovalStepType.APPLICANT_SELECT)
                        .map(step -> new OaFormApprovalSelectionVO(step.code(), step.name(),
                                step.mode().name()))
                        .toList(), fieldVOs(form.getFields(), context),
                form.getVersion(), form.getCreatedTime(), form.getUpdatedTime());
    }

    private List<OaFormFieldVO> fieldVOs(
            Collection<OaFormField> fields, FormReadContext context) {
        return fields.stream().map(field -> {
            FieldDefinition definition = context.definitions().get(field.fieldCode());
            EffectiveFieldAccess access = context.accesses().getOrDefault(
                    field.fieldCode(), EffectiveFieldAccess.hidden());
            if (definition == null || access.accessLevel() == FieldAccessLevel.HIDDEN) {
                return null;
            }
            return new OaFormFieldVO(field.fieldCode().getValue(), definition.getDisplayName(),
                    definition.getDescription(), definition.getFieldType().name(),
                    definition.isBuiltin(), field.required(), definition.isSearchable(),
                    definition.getStatus().name(), field.sortOrder(),
                    definition.getOptions().stream().map(option -> new FieldOptionVO(
                            option.value(), option.label(), option.order())).toList(),
                    access.accessLevel().name(), access.maskType().name());
        }).filter(java.util.Objects::nonNull).toList();
    }

    private ApprovalDefinition requireApproval(
            TenantId tenantId, ApprovalDefinitionId id) {
        return approvalRepository.findById(tenantId, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "OA approval definition was not found"));
    }

    private OaFormDefinition requireForm(TenantId tenantId, OaFormId id) {
        return formRepository.findById(tenantId, id)
                .filter(form -> !form.isDeleted())
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "OA form was not found"));
    }

    private OaFormDefinition requireFormForUpdate(TenantId tenantId, OaFormId id) {
        return formRepository.findByIdForUpdate(tenantId, id)
                .filter(form -> !form.isDeleted())
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "OA form was not found"));
    }

    private List<OaFormField> fields(Collection<OaFormFieldRequest> requests) {
        if (requests == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form fields are required");
        }
        try {
            return requests.stream().map(request -> {
                if (request == null || request.getRequired() == null
                        || request.getSortOrder() == null) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "OA form field is invalid");
                }
                return new OaFormField(new FieldCode(request.getFieldCode()),
                        request.getRequired(), request.getSortOrder());
            }).toList();
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form field is invalid");
        }
    }

    private void validatePage(QueryRequest request) {
        if (request.getPage() == null) {
            request.setPage(QueryRequest.DEFAULT_PAGE);
        }
        if (request.getSize() == null) {
            request.setSize(QueryRequest.DEFAULT_SIZE);
        }
        if (request.getPage() < 1 || request.getPage() > QueryRequest.MAX_PAGE
                || request.getSize() < 1 || request.getSize() > QueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form page is outside the supported range");
        }
    }

    private OaFormStatus parseOptionalStatus(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    private OaFormStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form status is required");
        }
        try {
            return OaFormStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form status is invalid");
        }
    }

    private OaFormId formId(Long value) {
        try {
            return new OaFormId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form ID is invalid");
        }
    }

    private FieldCode fieldCode(String value) {
        try {
            return new FieldCode(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA form field code is invalid");
        }
    }

    private int candidatePage(Integer value) {
        int page = value == null ? 1 : value;
        if (page < 1 || page > QueryRequest.MAX_PAGE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA candidate page is outside the supported range");
        }
        return page;
    }

    private int candidateSize(Integer value) {
        int size = value == null ? QueryRequest.DEFAULT_SIZE : value;
        if (size < 1 || size > 200) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA candidate page size is outside the supported range");
        }
        return size;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApprovalDefinitionId approvalId(Long value) {
        try {
            return new ApprovalDefinitionId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "OA approval definition ID is invalid");
        }
    }

    private void requireVersion(OaFormDefinition form, Long expected) {
        if (expected == null || !expected.equals(form.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "OA form version is stale");
        }
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is required");
        }
    }

    private NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private record ValidatedConfiguration(
            ApprovalDefinition approval,
            Map<FieldCode, FieldDefinition> definitions) {
    }

    private record FormReadContext(
            MemberId memberId,
            Map<FieldCode, FieldDefinition> definitions,
            Map<ApprovalDefinitionId, ApprovalDefinition> approvals,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
    }

    private record AvailableFormContext(OaFormDefinition form, FormReadContext context) {
    }
}
