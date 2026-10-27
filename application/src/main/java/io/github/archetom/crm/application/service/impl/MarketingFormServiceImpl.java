package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormCreateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormFieldRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionQueryRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormSubmissionTrendRequest;
import io.github.archetom.crm.api.dto.request.MarketingFormUpdateRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.MarketingFormPublicId;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.LeadCreationCoordinator;
import io.github.archetom.crm.application.service.CustomerCreationCoordinator;
import io.github.archetom.crm.application.service.MarketingFormService;
import io.github.archetom.crm.application.service.MarketingFormCreationCoordinator;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.MarketingFormFieldOptionVO;
import io.github.archetom.crm.application.vo.MarketingFormFieldVO;
import io.github.archetom.crm.application.vo.MarketingFormConfigurationFieldVO;
import io.github.archetom.crm.application.vo.MarketingFormConfigurationOptionsVO;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionLinkVO;
import io.github.archetom.crm.application.vo.MarketingFormSubmissionTrendVO;
import io.github.archetom.crm.application.vo.MarketingFormVO;
import io.github.archetom.crm.application.vo.PublicMarketingFormVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.MarketingForm;
import io.github.archetom.crm.domain.entity.MarketingFormField;
import io.github.archetom.crm.domain.exception.DomainException;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.model.MarketingFormSubmission;
import io.github.archetom.crm.domain.model.MarketingFormSubmissionDateRange;
import io.github.archetom.crm.domain.model.MarketingFormSubmissionTrendPoint;
import io.github.archetom.crm.domain.model.MarketingFormStatus;
import io.github.archetom.crm.domain.model.ReportGranularity;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.MarketingFormRepository;
import io.github.archetom.crm.domain.repository.MarketingFormSubmissionRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarketingFormServiceImpl implements MarketingFormService, MarketingFormCreationCoordinator {

    private static final int MAX_SUBMISSION_TREND_BUCKETS = 120;
    private static final int MAX_SUBMISSION_TREND_YEARS = 10;
    private static final int MAX_SUBMISSION_LINK_DAYS = 366;

    private final MarketingFormRepository formRepository;
    private final MarketingFormSubmissionRepository submissionRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final MarketingFormPolicy formPolicy;
    private final OrganizationAccessGuard accessGuard;
    private final CustomizationObjectLock objectLock;
    private final LeadCreationCoordinator leadCreationCoordinator;
    private final CustomerCreationCoordinator customerCreationCoordinator;
    private final LeadAccessResolver leadAccessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final io.github.archetom.crm.application.service.MarketingFileDraftService marketingFileDraftService;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<MarketingFormConfigurationOptionsVO> configurationOptions(
            AuthenticatedCaller caller, Long formId, String targetObjectType) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_FORM_CONFIGURATION_OPTIONS,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;
                    private MemberId owner;
                    private MarketingFormTargetType targetType;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_WRITE);
                        id = formId == null ? null : id(formId);
                        if (id == null) {
                            owner = new MemberId(caller.actorId());
                            targetType = targetType(targetObjectType);
                        } else {
                            MarketingForm form = requireForm(tenantId, id);
                            owner = form.getOwnerMemberId();
                            targetType = form.getTargetType();
                            if (targetObjectType != null && !targetObjectType.isBlank()
                                    && targetType != targetType(targetObjectType)) {
                                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                        "Marketing form target object type is immutable");
                            }
                        }
                    }

                    @Override
                    public MarketingFormConfigurationOptionsVO execute() {
                        return new MarketingFormConfigurationOptionsVO(
                                formPolicy.configurationFields(tenantId, owner, targetType).stream()
                                        .map(MarketingFormServiceImpl::configurationField)
                                        .toList());
                    }
                });
    }

    @Override
    public Result<Pager<MarketingFormVO>> query(
            AuthenticatedCaller caller, MarketingFormQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_FORM_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormStatus status;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_READ);
                        require(request, "Marketing form query");
                        validatePage(request);
                        status = parseOptionalStatus(request.getStatus());
                    }

                    @Override
                    public Pager<MarketingFormVO> execute() {
                        PageResult<MarketingForm> page = formRepository.findPage(
                                tenantId, status, request.getPage(), request.getSize());
                        Pager<MarketingFormVO> result = new Pager<>();
                        result.setPageNum(page.page());
                        result.setPageSize(page.size());
                        result.setTotalNum(page.total());
                        result.setObjectList(page.items().stream()
                                .map(form -> toVO(form, definitions(
                                        tenantId, form.getTargetType(), true))).toList());
                        return result;
                    }
                });
    }

    @Override
    public Result<MarketingFormVO> get(AuthenticatedCaller caller, Long formId) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_FORM_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_READ);
                        id = id(formId);
                    }

                    @Override
                    public MarketingFormVO execute() {
                        MarketingForm form = requireForm(tenantId, id);
                        return toVO(form, definitions(tenantId, form.getTargetType(), true));
                    }
                });
    }

    @Override
    public Result<MarketingFormVO> create(
            AuthenticatedCaller caller, MarketingFormCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FORM_CREATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MemberId creator;
                    private MemberId owner;
                    private MarketingFormTargetType targetType;
                    private List<MarketingFormField> fields;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_WRITE);
                        require(request, "Marketing form create request");
                        creator = new MemberId(caller.actorId());
                        owner = new MemberId(request.getOwnerActorId() == null
                                ? caller.actorId() : request.getOwnerActorId());
                        targetType = targetType(request.getTargetObjectType());
                        fields = fields(request.getFields());
                    }

                    @Override
                    public MarketingFormVO execute() {
                        objectLock.acquire(tenantId, targetType.businessObjectType());
                        var validated = formPolicy.validate(
                                tenantId, owner, targetType, fields, false);
                        MarketingFormPublicId.Generated publicId =
                                MarketingFormPublicId.generate(tenantId);
                        MarketingForm form = MarketingForm.create(
                                tenantId, creator, owner, targetType, publicId.publicKey(),
                                request.getName(), request.getTitle(), request.getDescription(),
                                request.getSubmitButtonText(), request.getSuccessMessage(), fields);
                        return toVO(formRepository.save(tenantId, form), validated.definitions());
                    }
                });
    }

    @Override
    public MarketingFormVO createInCurrentTransaction(
            AuthenticatedCaller caller, MarketingFormCreateRequest request) {
        TenantId tenantId = accessGuard.requireActiveMembership(
                caller, AuthorityCatalog.MARKETING_FORMS_WRITE);
        require(request, "Marketing form create request");
        MemberId creator = new MemberId(caller.actorId());
        MemberId owner = new MemberId(request.getOwnerActorId() == null
                ? caller.actorId() : request.getOwnerActorId());
        MarketingFormTargetType targetType = targetType(request.getTargetObjectType());
        List<MarketingFormField> fields = fields(request.getFields());
        objectLock.acquire(tenantId, targetType.businessObjectType());
        var validated = formPolicy.validate(tenantId, owner, targetType, fields, false);
        MarketingFormPublicId.Generated publicId = MarketingFormPublicId.generate(tenantId);
        MarketingForm form = MarketingForm.create(
                tenantId, creator, owner, targetType, publicId.publicKey(),
                request.getName(), request.getTitle(), request.getDescription(),
                request.getSubmitButtonText(), request.getSuccessMessage(), fields);
        return toVO(formRepository.save(tenantId, form), validated.definitions());
    }

    @Override
    public Result<MarketingFormVO> update(
            AuthenticatedCaller caller, Long formId, MarketingFormUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FORM_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;
                    private MemberId owner;
                    private List<MarketingFormField> fields;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_WRITE);
                        id = id(formId);
                        require(request, "Marketing form update request");
                        owner = request.getOwnerActorId() == null
                                ? null : new MemberId(request.getOwnerActorId());
                        fields = fields(request.getFields());
                    }

                    @Override
                    public MarketingFormVO execute() {
                        MarketingForm form = requireFormForUpdate(tenantId, id);
                        objectLock.acquire(tenantId, form.getTargetType().businessObjectType());
                        requireVersion(form, request.getVersion());
                        if (owner == null) {
                            owner = form.getOwnerMemberId();
                        }
                        var validated = formPolicy.validate(
                                tenantId, owner, form.getTargetType(), fields, form.isActive());
                        form.update(owner, request.getName(), request.getTitle(),
                                request.getDescription(), request.getSubmitButtonText(),
                                request.getSuccessMessage(), fields);
                        return toVO(formRepository.save(tenantId, form), validated.definitions());
                    }
                });
    }

    @Override
    public Result<MarketingFormVO> updateStatus(
            AuthenticatedCaller caller, Long formId,
            MarketingFormStatusUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FORM_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;
                    private MarketingFormStatus status;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_WRITE);
                        id = id(formId);
                        require(request, "Marketing form status request");
                        status = parseStatus(request.getStatus());
                        if (status == MarketingFormStatus.DELETED) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Marketing form status is invalid");
                        }
                    }

                    @Override
                    public MarketingFormVO execute() {
                        MarketingForm form = requireFormForUpdate(tenantId, id);
                        objectLock.acquire(tenantId, form.getTargetType().businessObjectType());
                        requireVersion(form, request.getVersion());
                        if (status == MarketingFormStatus.ACTIVE) {
                            var validated = formPolicy.validate(
                                    tenantId, form.getOwnerMemberId(), form.getTargetType(),
                                    form.getFields(), true);
                            form.activate();
                            return toVO(formRepository.save(tenantId, form),
                                    validated.definitions());
                        } else {
                            form.deactivate();
                            return toVO(formRepository.save(tenantId, form),
                                    definitions(tenantId, form.getTargetType(), true));
                        }
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long formId, Long version) {
        return commandTemplate.execute(UseCaseOperation.MARKETING_FORM_DELETE,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_WRITE);
                        id = id(formId);
                    }

                    @Override
                    public Void execute() {
                        MarketingForm form = requireFormForUpdate(tenantId, id);
                        objectLock.acquire(tenantId, form.getTargetType().businessObjectType());
                        requireVersion(form, version);
                        form.delete();
                        formRepository.save(tenantId, form);
                        return null;
                    }
                });
    }

    @Override
    public Result<PublicMarketingFormVO> getPublic(String publicId) {
        return queryTemplate.execute(UseCaseOperation.MARKETING_FORM_PUBLIC_GET,
                new ServiceOperation<>() {
                    private MarketingFormPublicId.Parts parts;

                    @Override
                    public void validate() {
                        parts = parsePublicId(publicId);
                    }

                    @Override
                    public PublicMarketingFormVO execute() {
                        MarketingForm form = requirePublicForm(parts);
                        var validated = formPolicy.validate(parts.tenantId(),
                                form.getOwnerMemberId(), form.getTargetType(),
                                form.getFields(), true);
                        return new PublicMarketingFormVO(publicId, form.getTitle(),
                                form.getDescription(), form.getSubmitButtonText(), form.getSuccessMessage(),
                                fieldVOs(form.getFields(), validated.definitions()));
                    }
                });
    }

    @Override
    public Result<MarketingFormSubmissionVO> submit(
            String publicId, String uploadCapability, MarketingFormSubmissionRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.MARKETING_FORM_SUBMIT, new ServiceOperation<>() {
                    private MarketingFormPublicId.Parts parts;
                    private String idempotencyKey;

                    @Override
                    public void validate() {
                        parts = parsePublicId(publicId);
                        require(request, "Marketing form submission");
                        if (request.getValues() == null
                                || request.getValues().size() > MarketingForm.MAX_FIELDS) {
                            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Marketing form values are invalid");
                        }
                        idempotencyKey = idempotencyKey(request.getIdempotencyKey());
                    }

                    @Override
                    public MarketingFormSubmissionVO execute() {
                        MarketingForm form = requirePublicFormForUpdate(parts);
                        objectLock.acquire(parts.tenantId(), form.getTargetType().businessObjectType());
                        var validated = formPolicy.validate(parts.tenantId(),
                                form.getOwnerMemberId(), form.getTargetType(),
                                form.getFields(), true);
                        String submissionDigest = submissionDigest(form, request);
                        MarketingFormSubmission existing = findExistingSubmission(
                                parts.tenantId(), form.getId(), idempotencyKey);
                        if (existing != null) {
                            if (existing.formVersion() != form.getVersion()
                                    || !existing.submissionDigest().equals(submissionDigest)) {
                                throw error(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                                        "Marketing form submission idempotency key conflicts");
                            }
                            return submitted(existing);
                        }
                        long targetRecordId;
                        if (form.getTargetType() == MarketingFormTargetType.LEAD) {
                            var preparedFiles = marketingFileDraftService.prepareSubmission(
                                    parts.tenantId(), form, uploadCapability, request.getFileDraftId(),
                                    request.getFileFields(), validated.definitions());
                            LeadCreateRequest leadRequest = leadRequest(
                                    form, request.getValues(), validated.definitions());
                            LeadVO lead = createLead(
                                    validated.ownerCaller(), leadRequest, preparedFiles.values());
                            marketingFileDraftService.consumePrepared(preparedFiles, lead.id());
                            targetRecordId = lead.id();
                        } else {
                            rejectCustomerFileSubmission(request);
                            CustomerCreateRequest customerRequest = customerRequest(
                                    form, request.getValues(), validated.definitions());
                            CustomerVO customer = createCustomer(
                                    validated.ownerCaller(), customerRequest);
                            targetRecordId = customer.id();
                        }
                        MarketingFormSubmission submission = appendSubmission(parts.tenantId(), form,
                                targetRecordId, idempotencyKey, submissionDigest);
                        return submitted(submission);
                    }
                });
    }

    @Override
    public Result<MarketingFormSubmissionTrendVO> submissionTrend(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionTrendRequest request) {
        return queryTemplate.execute(
                UseCaseOperation.MARKETING_FORM_SUBMISSION_TREND,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;
                    private MarketingFormSubmissionDateRange range;
                    private ReportGranularity granularity;
                    private List<LocalDate> periods;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_READ);
                        id = id(formId);
                        require(request, "Marketing form submission trend request");
                        range = new MarketingFormSubmissionDateRange(
                                request.getFrom(), request.getTo());
                        granularity = ReportGranularity.parse(request.getGranularity());
                        if (range.exceedsYears(MAX_SUBMISSION_TREND_YEARS)) {
                            throw new IllegalArgumentException(
                                    "marketing form submission trend range is too large");
                        }
                        periods = range.periodStarts(
                                granularity, MAX_SUBMISSION_TREND_BUCKETS);
                    }

                    @Override
                    public MarketingFormSubmissionTrendVO execute() {
                        requireHistoricalForm(tenantId, id);
                        List<MarketingFormSubmissionTrendPoint> values =
                                submissionRepository.findTrend(
                                        tenantId, id, range, granularity);
                        List<MarketingFormSubmissionTrendVO
                                .MarketingFormSubmissionTrendPointVO> points =
                                MarketingFormSubmissionTrendProjection.complete(
                                        periods, values).stream()
                                .map(value -> new MarketingFormSubmissionTrendVO
                                        .MarketingFormSubmissionTrendPointVO(
                                        value.periodStart(), value.acceptedCount()))
                                .toList();
                        return new MarketingFormSubmissionTrendVO(
                                id.getValue(), range.from(), range.to(),
                                granularity.name(), points);
                    }
                });
    }

    @Override
    public Result<Pager<MarketingFormSubmissionLinkVO>> querySubmissions(
            AuthenticatedCaller caller, Long formId,
            MarketingFormSubmissionQueryRequest request) {
        return queryTemplate.execute(
                UseCaseOperation.MARKETING_FORM_SUBMISSION_QUERY,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private MarketingFormId id;
                    private MarketingFormSubmissionDateRange range;
                    private MarketingFormTargetType targetType;
                    private LeadAccessResolver.LeadAccess leadAccess;
                    private CustomerAccessResolver.CustomerAccess customerAccess;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.MARKETING_FORMS_READ);
                        id = id(formId);
                        require(request, "Marketing form submission query request");
                        validateSubmissionPage(request);
                        range = submissionRange(request);
                        targetType = requireHistoricalForm(tenantId, id).getTargetType();
                        if (targetType == MarketingFormTargetType.LEAD) {
                            leadAccess = leadAccessResolver.access(
                                    caller, AuthorityCatalog.LEADS_READ, LeadAccessMode.READ);
                            if (!tenantId.sameValueAs(leadAccess.tenantId())) {
                                throw accessDenied("Lead access tenant does not match marketing form tenant");
                            }
                        } else {
                            customerAccess = customerAccessResolver.access(
                                    caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                            if (!tenantId.sameValueAs(customerAccess.tenantId())) {
                                throw accessDenied(
                                        "Customer access tenant does not match marketing form tenant");
                            }
                        }
                    }

                    @Override
                    public Pager<MarketingFormSubmissionLinkVO> execute() {
                        PageResult<MarketingFormSubmission> page = targetType
                                == MarketingFormTargetType.LEAD
                                ? submissionRepository.findVisibleLeadLinks(
                                        tenantId, id, range, leadAccess.visibility(),
                                        request.getPage(), request.getSize())
                                : submissionRepository.findVisibleCustomerLinks(
                                        tenantId, id, range, customerAccess.visibility(),
                                        request.getPage(), request.getSize());
                        Pager<MarketingFormSubmissionLinkVO> result = new Pager<>();
                        result.setPageNum(page.page());
                        result.setPageSize(page.size());
                        result.setTotalNum(page.total());
                        result.setObjectList(page.items().stream()
                                .map(submission -> new MarketingFormSubmissionLinkVO(
                                        submission.id().getValue(),
                                        submission.targetType().name(),
                                        submission.targetRecordId(),
                                        submission.formVersion(),
                                        submission.submittedTime()))
                                .toList());
                        return result;
                    }
                });
    }

    private LeadVO createLead(
            AuthenticatedCaller ownerCaller, LeadCreateRequest request,
            List<FileFieldValue> preparedFileValues) {
        try {
            Map<FieldCode, List<FileFieldValue>> preparedFileFields = preparedFileValues == null
                    ? Map.of() : preparedFileValues.stream().collect(Collectors.groupingBy(
                    FileFieldValue::fieldCode, LinkedHashMap::new, Collectors.toList()));
            return leadCreationCoordinator.createInCurrentTransaction(ownerCaller, request,
                    preparedFileFields);
        } catch (DomainException
                | IllegalArgumentException
                | ApplicationException
                | PessimisticLockingFailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApplicationException(ApplicationErrorCode.UNKNOWN, exception);
        }
    }

    private CustomerVO createCustomer(
            AuthenticatedCaller ownerCaller, CustomerCreateRequest request) {
        try {
            return customerCreationCoordinator.createInCurrentTransaction(ownerCaller, request);
        } catch (DomainException
                | IllegalArgumentException
                | ApplicationException
                | PessimisticLockingFailureException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApplicationException(ApplicationErrorCode.UNKNOWN, exception);
        }
    }

    private void rejectCustomerFileSubmission(MarketingFormSubmissionRequest request) {
        if (request.getFileDraftId() != null
                || request.getFileFields() != null && !request.getFileFields().isEmpty()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer marketing forms do not accept file submissions");
        }
    }

    private MarketingFormSubmission appendSubmission(
            TenantId tenantId, MarketingForm form,
            long targetRecordId, String idempotencyKey, String submissionDigest) {
        try {
            if (targetRecordId < 1) {
                throw new IllegalStateException(
                        "Persisted target record identity is unavailable for marketing attribution");
            }
            return submissionRepository.append(tenantId, form.getId(), form.getVersion(),
                    form.getTargetType(), targetRecordId, idempotencyKey, submissionDigest);
        } catch (DomainException | IllegalArgumentException | ApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApplicationException(ApplicationErrorCode.UNKNOWN, exception);
        }
    }

    private MarketingFormSubmission findExistingSubmission(
            TenantId tenantId, MarketingFormId formId, String idempotencyKey) {
        try {
            return submissionRepository.findByIdempotencyKey(tenantId, formId, idempotencyKey)
                    .orElse(null);
        } catch (DomainException | IllegalArgumentException | ApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApplicationException(ApplicationErrorCode.UNKNOWN, exception);
        }
    }

    private static MarketingFormSubmissionVO submitted(MarketingFormSubmission submission) {
        return new MarketingFormSubmissionVO(submission.id().getValue(), true,
                submission.idempotencyKey());
    }

    private String idempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form submission idempotency key is invalid");
        }
        return normalized;
    }

    private String submissionDigest(MarketingForm form, MarketingFormSubmissionRequest request) {
        try {
            if (form == null || request == null || request.getValues() == null) {
                throw new IllegalArgumentException("marketing form submission is invalid");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            writeString(digest, "atom-crm:marketing-form-submission:v1");
            writeLong(digest, form.getId().getValue());
            writeLong(digest, form.getVersion());
            writeStringLists(digest, request.getValues());
            Long draftId = request.getFileDraftId();
            writeBoolean(digest, draftId != null);
            if (draftId != null) {
                writeLong(digest, draftId);
            }
            writeLongLists(digest, request.getFileFields());
            return HexFormat.of().formatHex(digest.digest());
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form submission is invalid");
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeStringLists(MessageDigest digest, Map<String, List<String>> values) {
        TreeMap<String, List<String>> ordered = new TreeMap<>();
        ordered.putAll(values == null ? Map.of() : values);
        writeInt(digest, ordered.size());
        ordered.forEach((field, items) -> {
            writeString(digest, field);
            List<String> safeItems = items == null ? List.of() : items;
            writeInt(digest, safeItems.size());
            safeItems.forEach(item -> writeString(digest, item));
        });
    }

    private static void writeLongLists(MessageDigest digest, Map<String, List<Long>> values) {
        TreeMap<String, List<Long>> ordered = new TreeMap<>();
        ordered.putAll(values == null ? Map.of() : values);
        writeInt(digest, ordered.size());
        ordered.forEach((field, items) -> {
            writeString(digest, field);
            List<Long> safeItems = items == null ? List.of() : items;
            writeInt(digest, safeItems.size());
            safeItems.forEach(item -> {
                if (item == null) {
                    throw new IllegalArgumentException("marketing file ID is invalid");
                }
                writeLong(digest, item);
            });
        });
    }

    private static void writeString(MessageDigest digest, String value) {
        if (value == null) {
            throw new IllegalArgumentException("marketing form value is invalid");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void writeBoolean(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void writeInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void writeLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private LeadCreateRequest leadRequest(
            MarketingForm form, Map<String, List<String>> submitted,
            Map<FieldCode, FieldDefinition> definitions) {
        Map<FieldCode, List<String>> values = submittedFieldValues(form, submitted, definitions);
        LeadCreateRequest request = new LeadCreateRequest()
                .setOwnerActorId(form.getOwnerMemberId().getValue());
        Map<String, List<String>> customFields = new LinkedHashMap<>();
        for (Map.Entry<FieldCode, List<String>> entry : values.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            FieldDefinition definition = definitions.get(entry.getKey());
            if (definition == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form field is unavailable");
            }
            if (definition.getFieldType() == io.github.archetom.crm.domain.model.CustomFieldType.FILE) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form file field cannot be a scalar value");
            }
            if (!definition.isBuiltin()) {
                customFields.put(entry.getKey().getValue(), entry.getValue());
                continue;
            }
            applyLeadBuiltin(request, entry.getKey().getValue(), entry.getValue());
        }
        request.setCustomFields(customFields.isEmpty() ? null : Map.copyOf(customFields));
        return request;
    }

    private CustomerCreateRequest customerRequest(
            MarketingForm form, Map<String, List<String>> submitted,
            Map<FieldCode, FieldDefinition> definitions) {
        Map<FieldCode, List<String>> values = submittedFieldValues(form, submitted, definitions);
        CustomerCreateRequest request = new CustomerCreateRequest()
                .setOwnerActorId(form.getOwnerMemberId().getValue());
        Map<String, List<String>> customFields = new LinkedHashMap<>();
        for (Map.Entry<FieldCode, List<String>> entry : values.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            FieldDefinition definition = definitions.get(entry.getKey());
            if (definition == null || definition.getFieldType()
                    == io.github.archetom.crm.domain.model.CustomFieldType.FILE) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form field is unavailable");
            }
            if (!definition.isBuiltin()) {
                customFields.put(entry.getKey().getValue(), entry.getValue());
                continue;
            }
            applyCustomerBuiltin(request, entry.getKey().getValue(), entry.getValue());
        }
        request.setCustomFields(customFields.isEmpty() ? null : Map.copyOf(customFields));
        return request;
    }

    private Map<FieldCode, List<String>> submittedFieldValues(
            MarketingForm form, Map<String, List<String>> submitted,
            Map<FieldCode, FieldDefinition> definitions) {
        if (submitted == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form values are invalid");
        }
        Map<FieldCode, MarketingFormField> configured = form.getFields().stream()
                .collect(Collectors.toMap(MarketingFormField::fieldCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        Map<FieldCode, List<String>> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : submitted.entrySet()) {
            FieldCode code;
            try {
                code = new FieldCode(entry.getKey());
            } catch (RuntimeException exception) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form field code is invalid");
            }
            if (!configured.containsKey(code)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form contains an unconfigured field");
            }
            values.put(code, submittedValues(entry.getValue()));
        }
        for (MarketingFormField field : form.getFields()) {
            List<String> input = values.getOrDefault(field.fieldCode(), List.of());
            FieldDefinition definition = definitions.get(field.fieldCode());
            if (definition != null
                    && definition.getFieldType() == io.github.archetom.crm.domain.model.CustomFieldType.FILE) {
                if (!input.isEmpty()) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Marketing form file field cannot be a scalar value");
                }
                continue;
            }
            if (field.required() && input.isEmpty()) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "A required marketing form field is empty");
            }
        }

        return Map.copyOf(values);
    }

    private void applyLeadBuiltin(
            LeadCreateRequest request, String code, List<String> values) {
        String value = single(values, code);
        switch (code) {
            case LeadFieldCatalog.NAME -> request.setName(text(value, 200, "Lead name"));
            case LeadFieldCatalog.PHONE_NUMBER -> request.setPhoneNumber(phone(value));
            case LeadFieldCatalog.TELEPHONE -> request.setTelephone(text(value, 50, "Telephone"));
            case LeadFieldCatalog.JOB_TITLE -> request.setJobTitle(text(value, 200, "Job title"));
            case LeadFieldCatalog.EMAIL -> request.setEmail(email(value));
            case LeadFieldCatalog.WEBSITE -> request.setWebsite(url(value));
            case LeadFieldCatalog.DESCRIPTION -> request.setDescription(
                    text(value, 2000, "Description"));
            case LeadFieldCatalog.NEXT_CONTACT_TIME -> request.setNextContactTime(dateTime(value));
            default -> throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form built-in field is unavailable");
        }
    }

    private void applyCustomerBuiltin(
            CustomerCreateRequest request, String code, List<String> values) {
        String value = single(values, code);
        switch (code) {
            case CustomerFieldCatalog.NAME -> request.setName(text(value, 200, "Customer name"));
            case CustomerFieldCatalog.PHONE_NUMBER -> request.setPhoneNumber(phone(value));
            case CustomerFieldCatalog.TELEPHONE -> request.setTelephone(text(value, 50, "Telephone"));
            case CustomerFieldCatalog.EMAIL -> request.setEmail(email(value));
            case CustomerFieldCatalog.WEBSITE -> request.setWebsite(url(value));
            case CustomerFieldCatalog.DESCRIPTION -> request.setDescription(
                    text(value, 2000, "Description"));
            default -> throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form built-in field is unavailable");
        }
    }

    private List<String> submittedValues(List<String> values) {
        if (values == null || values.size() > 20) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form field values are invalid");
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 500) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form field value is invalid");
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private String single(List<String> values, String code) {
        if (values.size() != 1) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form field requires one value: " + code);
        }
        return values.getFirst();
    }

    private String text(String value, int max, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > max) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is invalid");
        }
        return normalized;
    }

    private String phone(String value) {
        String normalized = text(value, 30, "Phone number");
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Phone number is invalid");
        }
        return normalized;
    }

    private String email(String value) {
        String normalized = text(value, 254, "Email").toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1
                || normalized.indexOf('@', at + 1) >= 0) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Email is invalid");
        }
        return normalized;
    }

    private String url(String value) {
        String normalized = text(value, 500, "Website");
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID, "Website is invalid");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Website is invalid");
        }
    }

    private LocalDateTime dateTime(String value) {
        try {
            return LocalDateTime.parse(text(value, 50, "Next contact time"));
        } catch (DateTimeParseException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Next contact time is invalid");
        }
    }

    private MarketingForm requirePublicForm(MarketingFormPublicId.Parts parts) {
        return formRepository.findActiveByPublicKey(parts.tenantId(), parts.publicKey())
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Marketing form was not found"));
    }

    private MarketingForm requirePublicFormForUpdate(MarketingFormPublicId.Parts parts) {
        return formRepository.findActiveByPublicKeyForUpdate(
                        parts.tenantId(), parts.publicKey())
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Marketing form was not found"));
    }

    private MarketingForm requireForm(TenantId tenantId, MarketingFormId id) {
        return formRepository.findById(tenantId, id)
                .filter(form -> !form.isDeleted())
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Marketing form was not found"));
    }

    private MarketingForm requireHistoricalForm(
            TenantId tenantId, MarketingFormId id) {
        return formRepository.findById(tenantId, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Marketing form was not found"));
    }

    private MarketingForm requireFormForUpdate(TenantId tenantId, MarketingFormId id) {
        return formRepository.findByIdForUpdate(tenantId, id)
                .filter(form -> !form.isDeleted())
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Marketing form was not found"));
    }

    private MarketingFormPublicId.Parts parsePublicId(String value) {
        return MarketingFormPublicId.parse(value)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Marketing form was not found"));
    }

    private List<MarketingFormField> fields(
            Collection<MarketingFormFieldRequest> requests) {
        if (requests == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form fields are required");
        }
        return requests.stream().map(request -> {
            if (request == null || request.getRequired() == null
                    || request.getSortOrder() == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Marketing form field is invalid");
            }
            return new MarketingFormField(new FieldCode(request.getFieldCode()),
                    request.getLabel(), request.getPlaceholder(), request.getHelpText(),
                    request.getRequired(), request.getSortOrder());
        }).toList();
    }

    private Map<FieldCode, FieldDefinition> definitions(
            TenantId tenantId, MarketingFormTargetType targetType, boolean includeInactive) {
        return fieldRepository.findAll(tenantId, targetType.businessObjectType(), includeInactive)
                .stream().collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private MarketingFormVO toVO(
            MarketingForm form, Map<FieldCode, FieldDefinition> definitions) {
        return new MarketingFormVO(form.getId().getValue(),
                MarketingFormPublicId.value(form.getTenantId(), form.getPublicKey()),
                form.getTargetType().name(),
                form.getName(), form.getTitle(), form.getDescription(),
                form.getSubmitButtonText(), form.getSuccessMessage(),
                form.getOwnerMemberId().getValue(), form.getStatus().name(),
                fieldVOs(form.getFields(), definitions), form.getVersion(),
                form.getCreatedTime(), form.getUpdatedTime());
    }

    private List<MarketingFormFieldVO> fieldVOs(
            Collection<MarketingFormField> fields,
            Map<FieldCode, FieldDefinition> definitions) {
        return fields.stream().map(field -> {
            FieldDefinition definition = definitions.get(field.fieldCode());
            List<MarketingFormFieldOptionVO> options = definition == null ? List.of()
                    : definition.getOptions().stream()
                    .map(option -> new MarketingFormFieldOptionVO(
                            option.value(), option.label(), option.order()))
                    .toList();
            return new MarketingFormFieldVO(field.fieldCode().getValue(), field.label(),
                    field.placeholder(), field.helpText(), field.required(), field.sortOrder(),
                    definition == null ? null : definition.getFieldType().name(), options);
        }).toList();
    }

    private static MarketingFormConfigurationFieldVO configurationField(
            FieldDefinition definition) {
        return new MarketingFormConfigurationFieldVO(
                definition.getCode().getValue(), definition.getDisplayName(),
                definition.getFieldType().name(), definition.isRequired(),
                definition.getOptions().stream()
                        .map(option -> new MarketingFormFieldOptionVO(
                                option.value(), option.label(), option.order()))
                        .toList());
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
                    "Marketing form page is outside the supported range");
        }
    }

    private void validateSubmissionPage(MarketingFormSubmissionQueryRequest request) {
        if (request.getPage() == null) {
            request.setPage(MarketingFormSubmissionQueryRequest.DEFAULT_PAGE);
        }
        if (request.getSize() == null) {
            request.setSize(MarketingFormSubmissionQueryRequest.DEFAULT_SIZE);
        }
        if (request.getPage() < 1
                || request.getPage() > MarketingFormSubmissionQueryRequest.MAX_PAGE
                || request.getSize() < 1
                || request.getSize() > MarketingFormSubmissionQueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form submission page is outside the supported range");
        }
    }

    private MarketingFormSubmissionDateRange submissionRange(
            MarketingFormSubmissionQueryRequest request) {
        if (request.getFrom() == null && request.getTo() == null) {
            return null;
        }
        if (request.getFrom() == null || request.getTo() == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form submission dates must be provided together");
        }
        MarketingFormSubmissionDateRange range =
                new MarketingFormSubmissionDateRange(
                        request.getFrom(), request.getTo());
        if (range.inclusiveDays() > MAX_SUBMISSION_LINK_DAYS) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form submission date range is too large");
        }
        return range;
    }

    private MarketingFormStatus parseOptionalStatus(String value) {
        return value == null || value.isBlank() ? null : parseStatus(value);
    }

    private MarketingFormStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form status is required");
        }
        try {
            return MarketingFormStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form status is invalid");
        }
    }

    private MarketingFormTargetType targetType(String value) {
        if (value == null || value.isBlank()) {
            return MarketingFormTargetType.LEAD;
        }
        try {
            return MarketingFormTargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form target object type is invalid");
        }
    }

    private void requireVersion(MarketingForm form, Long version) {
        if (version == null || !version.equals(form.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Marketing form version is stale");
        }
    }

    private MarketingFormId id(Long value) {
        try {
            return new MarketingFormId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Marketing form ID is invalid");
        }
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    private NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }

    private NonRetryableApplicationException accessDenied(String message) {
        return error(ApplicationErrorCode.ACCESS_DENIED, message);
    }

}
