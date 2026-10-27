package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchTagRequest;
import io.github.archetom.crm.api.dto.request.LeadBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.LeadCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadConvertRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import io.github.archetom.crm.api.dto.request.LeadVersionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.LeadStructureLock;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver.LeadAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.service.LeadCommandService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.LeadCreationCoordinator;
import io.github.archetom.crm.application.service.LeadConversionService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.application.vo.LeadConversionVO;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadAddress;
import io.github.archetom.crm.domain.entity.LeadCollaborator;
import io.github.archetom.crm.domain.entity.LeadFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.LeadNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadDealStatus;
import io.github.archetom.crm.domain.model.LeadHierarchyStats;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FileFieldValue;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.LeadStarRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class LeadCommandServiceImpl implements LeadCommandService, LeadCreationCoordinator {

    private static final int MAX_HIERARCHY_NODES = LeadHierarchyStats.MAX_TREE_NODES;
    private static final int MAX_HIERARCHY_DEPTH = LeadHierarchyStats.MAX_DEPTH;
    private static final int HIERARCHY_NODE_PROBE = MAX_HIERARCHY_NODES + 1;
    private static final int HIERARCHY_DEPTH_PROBE = MAX_HIERARCHY_DEPTH + 1;

    private final LeadRepository leadRepository;
    private final LeadStarRepository starRepository;
    private final LeadAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final CustomerRepository customerRepository;
    private final LeadApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final LeadConversionService conversionService;
    private final LeadStructureLock structureLock;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    /** Compatibility constructor retained for focused policy tests that do not exercise FILE fields. */
    LeadCommandServiceImpl(LeadRepository leadRepository, LeadStarRepository starRepository,
                           LeadAccessResolver accessResolver, CustomerAccessResolver customerAccessResolver,
                           CustomerRepository customerRepository, LeadApplicationSupport support,
                           AttachmentParentLifecycle attachmentParentLifecycle,
                           LeadConversionService conversionService, LeadStructureLock structureLock,
                           CustomizationObjectLock objectLock, CommandServiceTemplate commandTemplate) {
        this(leadRepository, starRepository, accessResolver, customerAccessResolver, customerRepository,
                support, attachmentParentLifecycle, null, conversionService, structureLock, objectLock,
                commandTemplate);
    }

    @Override
    public Result<LeadVO> create(
            AuthenticatedCaller caller, LeadCreateRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.LEAD_CREATE, createOperation(caller, request, Map.of()));
    }

    @Override
    public LeadVO createInCurrentTransaction(
            AuthenticatedCaller caller, LeadCreateRequest request) {
        return createInCurrentTransaction(caller, request, Map.of());
    }

    @Override
    public LeadVO createInCurrentTransaction(
            AuthenticatedCaller caller, LeadCreateRequest request,
            Map<FieldCode, List<FileFieldValue>> preparedFileFields) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Lead creation coordination requires an active transaction");
        }
        ServiceOperation<LeadVO> operation = createOperation(caller, request, preparedFileFields);
        operation.validate();
        operation.prepare();
        LeadVO result = operation.execute();
        operation.onSuccess(result);
        return result;
    }

    private ServiceOperation<LeadVO> createOperation(
            AuthenticatedCaller caller, LeadCreateRequest request,
            Map<FieldCode, List<FileFieldValue>> preparedFileFields) {
        return new ServiceOperation<>() {
            private LeadAccess writeAccess;
            private LeadAccess assignAccess;
            private CustomerAccess customerReadAccess;
            private MemberId ownerMemberId;

            @Override
            public void validate() {
                writeAccess = accessResolver.access(
                        caller, AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE);
                requireRequest(request, "Lead create request");
                ownerMemberId = new MemberId(request.getOwnerActorId() == null
                        ? caller.actorId() : request.getOwnerActorId());
                if (!ownerMemberId.sameValueAs(writeAccess.memberId())
                        || request.getCollaborators() != null && !request.getCollaborators().isEmpty()) {
                    assignAccess = accessResolver.access(
                            caller, AuthorityCatalog.LEADS_ASSIGN, LeadAccessMode.OWNER_SCOPE);
                }
                if (request.getCustomerId() != null) {
                    customerReadAccess = customerAccessResolver.access(
                            caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                }
            }

            @Override
            public LeadVO execute() {
                objectLock.acquire(writeAccess.tenantId(), BusinessObjectType.LEAD);
                List<FieldDefinition> fields = support.fields(writeAccess.tenantId());
                Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                var fieldAccess = support.accesses(writeAccess.tenantId(), writeAccess.memberId());
                requireCreateFieldAccess(request, fieldAccess);
                OrganizationMembership owner = support.requireActiveMember(
                        writeAccess.tenantId(), ownerMemberId);
                if (assignAccess != null) {
                    requireAssignableOwner(owner, assignAccess.visibility());
                }
                List<LeadAddress> addresses = support.addresses(request.getAddresses());
                Set<FieldCode> preparedFileCodes = preparedFileCodes(preparedFileFields,
                        fieldsByCode, fieldAccess);
                List<LeadFieldValue> customFields = support.customFields(
                        request.getCustomFields(), List.of(), true, fieldsByCode, fieldAccess,
                        preparedFileCodes);
                Lead lead = Lead.create(writeAccess.tenantId(), ownerMemberId,
                        request.getName(), request.getPhoneNumber(), request.getTelephone(),
                        request.getJobTitle(), request.getEmail(), request.getWebsite(),
                        request.getDescription(), request.getNextContactTime(),
                        addresses, customFields);
                lead.replaceTags(request.getTags());
                List<LeadCollaborator> collaborators = support.collaborators(
                        request.getCollaborators());
                support.requireActiveMembers(writeAccess.tenantId(), collaborators.stream()
                        .map(LeadCollaborator::memberId).toList());
                lead.replaceCollaborators(collaborators);
                if (request.getCustomerId() != null) {
                    support.requireWrite(fieldAccess, LeadFieldCatalog.CUSTOMER);
                    requireVisibleCustomer(customerReadAccess, request.getCustomerId());
                    lead.changeCustomer(new CustomerId(request.getCustomerId()));
                }
                if (request.getParentLeadId() != null) {
                    structureLock.acquire(writeAccess.tenantId());
                    LeadId parentId = new LeadId(request.getParentLeadId());
                    requireVisible(writeAccess, parentId);
                    requireCreateHierarchyCapacity(writeAccess, parentId);
                    lead.changeParent(parentId);
                }
                Lead saved = leadRepository.save(
                        writeAccess.tenantId(), lead, fields);
                // Public MarketingForm FILE facts are already locked, validated and later bound
                // by MarketingFileDraftService in this same outer transaction. Do not route them
                // through the human FileFieldDraft consumer: it would re-run its own required
                // FILE validation against an intentionally empty human draft request.
                if (preparedFileCodes.isEmpty()) {
                    fileFieldBindingService.consumeCreateDraft(writeAccess.tenantId(), writeAccess.memberId(),
                            BusinessObjectType.LEAD, request.getFileFieldDraftId(),
                            new AttachmentTarget(AttachmentTargetType.LEAD, saved.getId().getValue()),
                            request.getFileFields(), fieldsByCode, fieldAccess);
                }
                return support.toVO(saved, false, fieldAccess,
                        visibleParentIds(List.of(saved), writeAccess),
                        visibleCustomerIds(caller, List.of(saved)), assignAccess != null);
            }
        };
    }

    private Set<FieldCode> preparedFileCodes(
            Map<FieldCode, List<FileFieldValue>> preparedFileFields,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> fieldAccess) {
        if (preparedFileFields == null || preparedFileFields.isEmpty()) return Set.of();
        LinkedHashSet<FieldCode> result = new LinkedHashSet<>();
        for (Map.Entry<FieldCode, List<FileFieldValue>> entry : preparedFileFields.entrySet()) {
            FieldCode code = entry.getKey();
            FieldDefinition field = code == null ? null : fields.get(code);
            if (field == null || field.isBuiltin() || !field.isActive()
                    || field.getFieldType() != CustomFieldType.FILE
                    || entry.getValue() == null || entry.getValue().isEmpty()) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Prepared lead file field is invalid");
            }
            support.requireWrite(fieldAccess, code.getValue());
            for (FileFieldValue value : entry.getValue()) {
                if (value == null || !code.equals(value.fieldCode())) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Prepared lead file field is invalid");
                }
            }
            result.add(code);
        }
        return Set.copyOf(result);
    }

    @Override
    public Result<LeadConversionVO> convert(
            AuthenticatedCaller caller, Long leadId, LeadConvertRequest request) {
        return conversionService.convert(caller, leadId, request);
    }

    @Override
    public Result<LeadVO> update(
            AuthenticatedCaller caller, Long leadId, LeadUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_UPDATE, new ServiceOperation<>() {
            private LeadAccess access;
            private CustomerAccess customerReadAccess;
            private LeadId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.LEADS_WRITE, LeadAccessMode.WRITE);
                id = id(leadId);
                requireRequest(request, "Lead update request");
                if (request.customerIdSpecified() && request.getCustomerId() != null) {
                    customerReadAccess = customerAccessResolver.access(
                            caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                }
            }

            @Override
            public LeadVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                Lead lead = requireVisible(access, id);
                support.requireVersion(lead, request.getVersion());
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                String name = patched(request.getName(), lead.getName(), fieldAccess,
                        LeadFieldCatalog.NAME);
                String phone = patched(request.getPhoneNumber(), lead.getPhoneNumber(), fieldAccess,
                        LeadFieldCatalog.PHONE_NUMBER);
                String telephone = patched(request.getTelephone(), lead.getTelephone(), fieldAccess,
                        LeadFieldCatalog.TELEPHONE);
                String jobTitle = patched(request.getJobTitle(), lead.getJobTitle(), fieldAccess,
                        LeadFieldCatalog.JOB_TITLE);
                String email = patched(request.getEmail(), lead.getEmail(), fieldAccess,
                        LeadFieldCatalog.EMAIL);
                String website = patched(request.getWebsite(), lead.getWebsite(), fieldAccess,
                        LeadFieldCatalog.WEBSITE);
                String description = patched(request.getDescription(), lead.getDescription(), fieldAccess,
                        LeadFieldCatalog.DESCRIPTION);
                LocalDateTime nextContactTime = patched(request.nextContactTimeSpecified(),
                        request.getNextContactTime(),
                        lead.getNextContactTime(), fieldAccess, LeadFieldCatalog.NEXT_CONTACT_TIME);
                List<LeadAddress> addresses = lead.getAddresses();
                if (request.getAddresses() != null) {
                    support.requireWrite(fieldAccess, LeadFieldCatalog.ADDRESSES);
                    addresses = support.addresses(request.getAddresses());
                }
                List<LeadFieldValue> customFields = support.customFields(
                        request.getCustomFields(), lead.getCustomFieldValues(), false,
                        fieldsByCode, fieldAccess);
                lead.replaceDetails(name, phone, telephone, jobTitle, email, website, description,
                        nextContactTime,
                        addresses, customFields);
                if (request.customerIdSpecified()) {
                    support.requireWrite(fieldAccess, LeadFieldCatalog.CUSTOMER);
                    if (request.getCustomerId() != null) {
                        requireVisibleCustomer(customerReadAccess, request.getCustomerId());
                        lead.changeCustomer(new CustomerId(request.getCustomerId()));
                    } else {
                        lead.changeCustomer(null);
                    }
                }
                Lead saved = leadRepository.save(access.tenantId(), lead, fields);
                fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                        BusinessObjectType.LEAD,
                        new AttachmentTarget(AttachmentTargetType.LEAD, saved.getId().getValue()),
                        request.getFileFields(), fieldsByCode, fieldAccess);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access),
                        visibleCustomerIds(caller, List.of(saved)), false);
            }
        });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long leadId, Long version) {
        return commandTemplate.execute(UseCaseOperation.LEAD_DELETE, new ServiceOperation<>() {
            private LeadAccess access;
            private LeadId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.LEADS_DELETE, LeadAccessMode.OWNER_SCOPE);
                id = id(leadId);
            }

            @Override
            public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                structureLock.acquire(access.tenantId());
                Lead lead = requireVisibleForUpdate(access, id);
                support.requireVersion(lead, version);
                if (leadRepository.existsActiveChild(access.tenantId(), id)) {
                    throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Lead with child leads cannot be deleted");
                }
                List<FieldDefinition> fields = support.fields(access.tenantId());
                lead.delete();
                attachmentParentLifecycle.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.LEAD, id.getValue()));
                fileFieldBindingService.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.LEAD, id.getValue()));
                leadRepository.save(access.tenantId(), lead, fields);
                return null;
            }
        });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, LeadBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_BATCH_DELETE, new ServiceOperation<>() {
            private LeadAccess access;
            private List<LeadBatchVersionRequest> targets;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.LEADS_DELETE, LeadAccessMode.OWNER_SCOPE);
                requireRequest(request, "Lead batch delete request");
                targets = batchTargets(request.getLeads(), "Lead batch delete targets");
            }

            @Override
            public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                structureLock.acquire(access.tenantId());
                List<Lead> leads = batchLeads(access, targets);
                for (int index = 0; index < leads.size(); index++) {
                    Lead lead = leads.get(index);
                    LeadId id = lead.getId();
                    support.requireVersion(lead, targets.get(index).getVersion());
                    if (leadRepository.existsActiveChild(access.tenantId(), id)) {
                        throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Lead with child leads cannot be deleted");
                    }
                }
                List<FieldDefinition> fields = support.fields(access.tenantId());
                for (Lead lead : leads) {
                    LeadId id = lead.getId();
                    lead.delete();
                    attachmentParentLifecycle.withdrawAll(access.tenantId(),
                            new AttachmentTarget(AttachmentTargetType.LEAD, id.getValue()));
                    fileFieldBindingService.withdrawAll(access.tenantId(),
                            new AttachmentTarget(AttachmentTargetType.LEAD, id.getValue()));
                    leadRepository.save(access.tenantId(), lead, fields);
                }
                return null;
            }
        });
    }

    @Override
    public Result<LeadVO> updateState(
            AuthenticatedCaller caller, Long leadId, LeadStateUpdateRequest request) {
        return mutate(caller, leadId, request, UseCaseOperation.LEAD_STATE_UPDATE,
                (lead, access, fields, fieldAccess) -> {
                    support.requireWrite(fieldAccess, LeadFieldCatalog.LOCKED);
                    support.requireWrite(fieldAccess, LeadFieldCatalog.DEAL_STATUS);
                    lead.changeState(Boolean.TRUE.equals(request.getLocked()),
                            support.parse(LeadDealStatus.class, request.getDealStatus(),
                                    "Lead deal status"));
                });
    }

    @Override
    public Result<LeadVO> updateOwner(
            AuthenticatedCaller caller, Long leadId, LeadOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_OWNER_UPDATE, new ServiceOperation<>() {
            private LeadAccess access;
            private LeadId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.LEADS_ASSIGN, LeadAccessMode.OWNER_SCOPE);
                id = id(leadId);
                requireRequest(request, "Lead owner request");
            }

            @Override
            public LeadVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                Lead lead = requireVisible(access, id);
                support.requireVersion(lead, request.getVersion());
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                support.requireWrite(fieldAccess, LeadFieldCatalog.OWNER_ACTOR);
                MemberId ownerId = new MemberId(request.getOwnerActorId());
                OrganizationMembership owner = support.requireActiveMember(access.tenantId(), ownerId);
                requireAssignableOwner(owner, access.visibility());
                lead.changeOwner(ownerId);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Lead saved = leadRepository.save(access.tenantId(), lead, fields);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access),
                        visibleCustomerIds(caller, List.of(saved)), true);
            }
        });
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, LeadBatchOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_BATCH_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private List<LeadBatchVersionRequest> targets;
                    private MemberId owner;

                    @Override
                    public void validate() {
                        access = accessResolver.access(
                                caller, AuthorityCatalog.LEADS_ASSIGN, LeadAccessMode.OWNER_SCOPE);
                        requireRequest(request, "Lead batch owner request");
                        targets = batchTargets(request.getLeads(), "Lead batch owner targets");
                        owner = new MemberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        requireAssignableOwner(ownerMembership, access.visibility());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, LeadFieldCatalog.OWNER_ACTOR);
                        List<Lead> leads = batchLeads(access, targets);
                        for (int index = 0; index < leads.size(); index++) {
                            support.requireVersion(leads.get(index), targets.get(index).getVersion());
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        for (Lead lead : leads) {
                            lead.changeOwner(owner);
                            leadRepository.save(access.tenantId(), lead, fields);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<LeadVO> replaceTags(
            AuthenticatedCaller caller, Long leadId, LeadTagsUpdateRequest request) {
        return mutate(caller, leadId, request, UseCaseOperation.LEAD_TAG_REPLACE,
                (lead, access, fields, fieldAccess) -> {
                    support.requireWrite(fieldAccess, LeadFieldCatalog.TAGS);
                    lead.replaceTags(request.getTags());
                });
    }

    @Override
    public Result<List<LeadVO>> changeTags(
            AuthenticatedCaller caller, LeadBatchTagRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_TAG_BATCH_CHANGE,
                new ServiceOperation<>() {
                    private LeadAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_WRITE,
                                LeadAccessMode.WRITE);
                        requireRequest(request, "Lead batch tag request");
                        if (request.getLeads() == null || request.getLeads().isEmpty()) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Lead batch must not be empty");
                        }
                    }

                    @Override
                    public List<LeadVO> execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, LeadFieldCatalog.TAGS);
                        Map<LeadId, Long> versions = versions(request.getLeads());
                        List<Lead> leads = leadRepository.findVisibleByIds(
                                access.tenantId(), versions.keySet(), access.visibility(), 100);
                        if (leads.size() != versions.size()) {
                            throw new LeadNotFoundException();
                        }
                        Set<LeadId> visibleParents = visibleParentIds(leads, access);
                        Set<CustomerId> visibleCustomers = visibleCustomerIds(caller, leads);
                        Set<LeadId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), versions.keySet());
                        List<LeadVO> result = new ArrayList<>();
                        for (Lead lead : leads) {
                            support.requireVersion(lead, versions.get(lead.getId()));
                            lead.changeTags(request.getAddedTags(), request.getRemovedTags());
                            Lead saved = leadRepository.save(
                                    access.tenantId(), lead, fields);
                            result.add(support.toVO(saved, starred.contains(saved.getId()),
                                    fieldAccess, visibleParents, visibleCustomers, false));
                        }
                        return List.copyOf(result);
                    }
                });
    }

    @Override
    public Result<LeadVO> replaceCollaborators(
            AuthenticatedCaller caller, Long leadId,
            LeadCollaboratorsUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_COLLABORATOR_REPLACE,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private LeadId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_ASSIGN,
                                LeadAccessMode.OWNER_SCOPE);
                        id = id(leadId);
                        requireRequest(request, "Lead collaborator request");
                    }

                    @Override
                    public LeadVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                        Lead lead = requireVisible(access, id);
                        support.requireVersion(lead, request.getVersion());
                        List<LeadCollaborator> collaborators = support.collaborators(
                                request.getCollaborators());
                        support.requireActiveMembers(access.tenantId(), collaborators.stream()
                                .map(LeadCollaborator::memberId).toList());
                        lead.replaceCollaborators(collaborators);
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        Lead saved = leadRepository.save(access.tenantId(), lead, fields);
                        return support.toVO(saved, starRepository.isStarred(
                                        access.tenantId(), access.memberId(), id), fieldAccess,
                                visibleParentIds(List.of(saved), access),
                                visibleCustomerIds(caller, List.of(saved)), true);
                    }
                });
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long leadId, Long version) {
        return commandTemplate.execute(UseCaseOperation.LEAD_COLLABORATOR_EXIT,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private LeadId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                        id = id(leadId);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                        Lead lead = requireVisible(access, id);
                        support.requireVersion(lead, version);
                        if (!lead.exitCollaborator(access.memberId())) {
                            throw new LeadNotFoundException();
                        }
                        leadRepository.save(access.tenantId(), lead,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<LeadVO> updateParent(
            AuthenticatedCaller caller, Long leadId, LeadParentUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_PARENT_UPDATE,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private LeadId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_WRITE,
                                LeadAccessMode.WRITE);
                        id = id(leadId);
                        requireRequest(request, "Lead parent request");
                    }

                    @Override
                    public LeadVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                        structureLock.acquire(access.tenantId());
                        Lead lead = requireVisible(access, id);
                        support.requireVersion(lead, request.getVersion());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, LeadFieldCatalog.PARENT_LEAD);
                        LeadId parentId = request.getParentLeadId() == null
                                ? null : new LeadId(request.getParentLeadId());
                        if (parentId != null) {
                            requireVisible(access, parentId);
                            LeadHierarchyStats sourceStats = hierarchyStats(access, id);
                            LeadHierarchyStats parentStats = hierarchyStats(access, parentId);
                            requireQueryableHierarchy(sourceStats);
                            requireQueryableHierarchy(parentStats);
                            List<Lead> descendants = leadRepository.findDescendants(
                                    access.tenantId(), id, HIERARCHY_NODE_PROBE);
                            if (descendants.stream().anyMatch(value -> value.getId().sameValueAs(parentId))) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Lead hierarchy cannot contain a cycle");
                            }
                            requireMoveHierarchyCapacity(sourceStats, parentStats);
                        }
                        lead.changeParent(parentId);
                        Lead saved = leadRepository.save(
                                access.tenantId(), lead, support.fields(access.tenantId()));
                        return support.toVO(saved, starRepository.isStarred(
                                        access.tenantId(), access.memberId(), id), fieldAccess,
                                visibleParentIds(List.of(saved), access),
                                visibleCustomerIds(caller, List.of(saved)), false);
                    }
                });
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long leadId, LeadStarUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.LEAD_STAR_UPDATE,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private LeadId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                        id = id(leadId);
                        requireRequest(request, "Lead star request");
                    }

                    @Override
                    public Void execute() {
                        requireVisible(access, id);
                        starRepository.setStarred(access.tenantId(), access.memberId(), id,
                                Boolean.TRUE.equals(request.getStarred()));
                        return null;
                    }
                });
    }

    private Result<LeadVO> mutate(
            AuthenticatedCaller caller, Long leadId, Object request,
            UseCaseOperation operation, LeadMutation mutation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private LeadAccess access;
            private LeadId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.LEADS_WRITE,
                        LeadAccessMode.WRITE);
                id = id(leadId);
                requireRequest(request, "Lead mutation request");
            }

            @Override
            public LeadVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.LEAD);
                Lead lead = requireVisible(access, id);
                Long version = request instanceof LeadStateUpdateRequest value ? value.getVersion()
                        : ((LeadTagsUpdateRequest) request).getVersion();
                support.requireVersion(lead, version);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                mutation.apply(lead, access, fields, fieldAccess);
                Lead saved = leadRepository.save(access.tenantId(), lead, fields);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access),
                        visibleCustomerIds(caller, List.of(saved)), false);
            }
        });
    }

    private void requireCreateHierarchyCapacity(
            LeadAccess access, LeadId parentId) {
        LeadHierarchyStats parentStats = hierarchyStats(access, parentId);
        requireQueryableHierarchy(parentStats);
        if (parentStats.treeNodeCount() >= MAX_HIERARCHY_NODES
                || parentStats.depth() >= MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
    }

    private LeadHierarchyStats hierarchyStats(
            LeadAccess access, LeadId leadId) {
        return leadRepository.hierarchyStats(
                        access.tenantId(), leadId,
                        HIERARCHY_NODE_PROBE, HIERARCHY_DEPTH_PROBE)
                .orElseThrow(LeadNotFoundException::new);
    }

    private void requireQueryableHierarchy(LeadHierarchyStats stats) {
        if (stats.depth() > MAX_HIERARCHY_DEPTH
                || stats.subtreeNodeCount() > MAX_HIERARCHY_NODES
                || stats.subtreeHeight() > MAX_HIERARCHY_DEPTH
                || stats.treeNodeCount() > MAX_HIERARCHY_NODES
                || stats.treeHeight() > MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
    }

    private void requireMoveHierarchyCapacity(
            LeadHierarchyStats source, LeadHierarchyStats parent) {
        if (parent.depth() + 1 + source.subtreeHeight() > MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
        if (!source.rootId().sameValueAs(parent.rootId())
                && parent.treeNodeCount() + source.subtreeNodeCount() > MAX_HIERARCHY_NODES) {
            throw hierarchyLimitExceeded();
        }
    }

    private NonRetryableApplicationException hierarchyLimitExceeded() {
        return support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                "Lead hierarchy exceeds the supported limit");
    }

    private Set<LeadId> visibleParentIds(
            Collection<Lead> leads, LeadAccess access) {
        Set<LeadId> parentIds = new LinkedHashSet<>();
        if (leads != null) {
            leads.stream().map(Lead::getParentLeadId)
                    .filter(java.util.Objects::nonNull).forEach(parentIds::add);
        }
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return leadRepository.findVisibleByIds(
                        access.tenantId(), parentIds, access.visibility(), parentIds.size())
                .stream().map(Lead::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<CustomerId> visibleCustomerIds(
            AuthenticatedCaller caller, Collection<Lead> leads) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)
                || leads == null || leads.isEmpty()) {
            return Set.of();
        }
        Set<CustomerId> ids = leads.stream().map(Lead::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        CustomerAccess access = customerAccessResolver.access(caller,
                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        return customerRepository.findVisibleByIds(access.tenantId(), ids,
                        access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Customer::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void requireCreateFieldAccess(
            LeadCreateRequest request,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses) {
        support.requireWrite(accesses, LeadFieldCatalog.NAME);
        support.requireWrite(accesses, LeadFieldCatalog.OWNER_ACTOR);
        if (request.getPhoneNumber() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.PHONE_NUMBER);
        }
        if (request.getTelephone() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.TELEPHONE);
        }
        if (request.getJobTitle() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.JOB_TITLE);
        }
        if (request.getEmail() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.EMAIL);
        }
        if (request.getWebsite() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.WEBSITE);
        }
        if (request.getDescription() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.DESCRIPTION);
        }
        if (request.getNextContactTime() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.NEXT_CONTACT_TIME);
        }
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            support.requireWrite(accesses, LeadFieldCatalog.TAGS);
        }
        if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
            support.requireWrite(accesses, LeadFieldCatalog.ADDRESSES);
        }
        if (request.getParentLeadId() != null) {
            support.requireWrite(accesses, LeadFieldCatalog.PARENT_LEAD);
        }
    }

    private String patched(
            String requested, String existing,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses,
            String code) {
        if (requested == null) {
            return existing;
        }
        support.requireWrite(accesses, code);
        return requested;
    }

    private LocalDateTime patched(
            boolean specified, LocalDateTime requested, LocalDateTime existing,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses,
            String code) {
        if (!specified) {
            return existing;
        }
        support.requireWrite(accesses, code);
        return requested;
    }

    private void requireAssignableOwner(
            OrganizationMembership owner, LeadVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(visibility.callerMemberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Lead owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !visibility.ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Lead owner is outside the assignment scope");
        }
    }

    private Map<LeadId, Long> versions(List<LeadVersionRequest> requests) {
        Map<LeadId, Long> values = new LinkedHashMap<>();
        for (LeadVersionRequest request : requests) {
            if (request == null || request.getVersion() == null
                    || values.putIfAbsent(id(request.getLeadId()), request.getVersion()) != null) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Lead batch entries must be unique");
            }
        }
        return values;
    }

    private List<LeadBatchVersionRequest> batchTargets(
            List<LeadBatchVersionRequest> requests, String description) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " are invalid");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (LeadBatchVersionRequest request : requests) {
            if (request == null || request.getLeadId() == null || request.getVersion() == null
                    || request.getVersion() < 0 || !ids.add(request.getLeadId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        description + " are invalid");
            }
            id(request.getLeadId());
        }
        return requests.stream().sorted(Comparator.comparing(LeadBatchVersionRequest::getLeadId))
                .toList();
    }

    private List<Lead> batchLeads(LeadAccess access, List<LeadBatchVersionRequest> targets) {
        return targets.stream().map(target -> requireVisibleForUpdate(access, id(target.getLeadId())))
                .toList();
    }

    private Lead requireVisible(LeadAccess access, LeadId id) {
        return leadRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(LeadNotFoundException::new);
    }

    private Lead requireVisibleForUpdate(LeadAccess access, LeadId id) {
        return leadRepository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(LeadNotFoundException::new);
    }

    private void requireVisibleCustomer(CustomerAccess access, Long customerId) {
        if (access == null || customerId == null || customerRepository.findVisibleById(
                access.tenantId(), new CustomerId(customerId), access.visibility()).isEmpty()) {
            throw new LeadNotFoundException();
        }
    }

    private LeadId id(Long value) {
        try {
            return new LeadId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead ID must be positive");
        }
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    @FunctionalInterface
    private interface LeadMutation {
        void apply(Lead lead, LeadAccess access, List<FieldDefinition> fields,
                   Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> fieldAccess);
    }
}
