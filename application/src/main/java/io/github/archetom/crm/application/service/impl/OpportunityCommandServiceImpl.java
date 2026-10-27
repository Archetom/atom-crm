package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpportunityBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityContactsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPrimaryContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContactAccessResolver.ContactAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver.OpportunityAccess;
import io.github.archetom.crm.application.service.OpportunityCommandService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.OpportunityCollaborator;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.OpportunityNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.OpportunityStarRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpportunityCommandServiceImpl implements OpportunityCommandService {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityStarRepository starRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final OpportunityAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final OpportunityApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final CustomizationObjectLock objectLock;
    private final OpportunityPipelinePairValidator pipelinePairValidator;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<OpportunityVO> create(
            AuthenticatedCaller caller, OpportunityCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_CREATE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityAccess assignAccess;
                    private CustomerAccess customerAccess;
                    private ContactAccess contactAccess;
                    private MemberId owner;
                    private OpportunityApplicationSupport.TemporalValues temporalValues;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_WRITE,
                                OpportunityAccessMode.WRITE);
                        requireRequest(request, "Opportunity create request");
                        customerAccess = customerAccessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                        owner = new MemberId(request.getOwnerActorId() == null
                                ? caller.actorId() : request.getOwnerActorId());
                        if (!owner.sameValueAs(access.memberId())
                                || request.getCollaborators() != null
                                && !request.getCollaborators().isEmpty()) {
                            assignAccess = accessResolver.access(caller,
                                    AuthorityCatalog.OPPORTUNITIES_ASSIGN,
                                    OpportunityAccessMode.OWNER_SCOPE);
                        }
                        if (hasContacts(request.getContactIds(),
                                request.getPrimaryContactId())) {
                            contactAccess = contactAccessResolver.access(caller,
                                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
                        }
                        temporalValues = support.normalizeTemporalValues(
                                request.getExpectedCloseDate(), request.getNextContactTime());
                    }

                    @Override
                    public OpportunityVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                        pipelinePairValidator.acquire(access.tenantId());
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        var fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireCreateFields(request, fieldAccess);
                        OpportunityPipelinePairValidator.Pair pair = pipelinePairValidator.normalize(
                                request.getPipelineCode(), request.getStageCode());
                        pipelinePairValidator.requireActive(access.tenantId(), pair);
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        if (assignAccess != null) {
                            requireAssignableOwner(
                                    ownerMembership, assignAccess.visibility());
                        }
                        CustomerId customerId = new CustomerId(request.getCustomerId());
                        requireVisibleCustomer(customerAccess, request.getCustomerId());
                        List<ContactId> contacts = contactIds(request.getContactIds());
                        ContactId primary = contactId(request.getPrimaryContactId());
                        requireVisibleContacts(contactAccess, customerId, contacts, primary);
                        List<OpportunityCollaborator> collaborators = support.collaborators(
                                request.getCollaborators());
                        support.requireActiveMembers(access.tenantId(), collaborators.stream()
                                .map(OpportunityCollaborator::memberId).toList());
                        Opportunity opportunity = Opportunity.create(
                                access.tenantId(), owner,
                                customerId, request.getName(),
                                pair.pipelineCode(), pair.stageCode(),
                                temporalValues.expectedCloseDate(), request.getEstimatedAmount(),
                                temporalValues.nextContactTime(), request.getDescription(),
                                contacts, primary, collaborators,
                                support.customFields(request.getCustomFields(), List.of(), true,
                                        byCode, fieldAccess));
                        Opportunity saved = opportunityRepository.save(
                                access.tenantId(), opportunity, fields);
                        fileFieldBindingService.consumeCreateDraft(access.tenantId(), access.memberId(),
                                BusinessObjectType.OPPORTUNITY, request.getFileFieldDraftId(),
                                new AttachmentTarget(AttachmentTargetType.OPPORTUNITY, saved.getId().getValue()),
                                request.getFileFields(), byCode, fieldAccess);
                        return support.toVO(saved, false, fieldAccess,
                                true, Set.copyOf(contacts),
                                caller.hasAuthority(AuthorityCatalog.OPPORTUNITIES_ASSIGN));
                    }
                });
    }

    @Override
    public Result<OpportunityVO> update(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_UPDATE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private CustomerAccess customerAccess;
                    private OpportunityId id;
                    private OpportunityApplicationSupport.TemporalValues temporalValues;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_WRITE,
                                OpportunityAccessMode.WRITE);
                        requireRequest(request, "Opportunity update request");
                        customerAccess = customerAccessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                        id = id(opportunityId);
                        temporalValues = support.normalizeTemporalValues(
                                request.getExpectedCloseDate(), request.getNextContactTime());
                    }

                    @Override
                    public OpportunityVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                        pipelinePairValidator.acquire(access.tenantId());
                        Opportunity opportunity = requireVisible(access, id);
                        support.requireVersion(opportunity, request.getVersion());
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        var fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireUpdateFields(fieldAccess);
                        OpportunityPipelinePairValidator.Pair pair = pipelinePairValidator.normalize(
                                request.getPipelineCode(), request.getStageCode());
                        if (!pair.sameAs(opportunity.getPipelineCode(), opportunity.getStageCode())) {
                            pipelinePairValidator.requireActive(access.tenantId(), pair);
                        }
                        CustomerId customerId = new CustomerId(request.getCustomerId());
                        requireVisibleCustomer(customerAccess, request.getCustomerId());
                        if (!opportunity.getCustomerId().sameValueAs(customerId)
                                && !opportunity.getContactIds().isEmpty()) {
                            ContactAccess contactAccess = contactAccessResolver.access(caller,
                                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
                            requireVisibleContacts(contactAccess, customerId,
                                    opportunity.getContactIds(), opportunity.getPrimaryContactId());
                        }
                        opportunity.replaceDetails(request.getName(), pair.pipelineCode(),
                                pair.stageCode(), temporalValues.expectedCloseDate(),
                                request.getEstimatedAmount(), temporalValues.nextContactTime(),
                                request.getDescription(), support.customFields(
                                        request.getCustomFields(),
                                        opportunity.getCustomFieldValues(), false,
                                        byCode, fieldAccess));
                        opportunity.changeCustomer(customerId);
                        Opportunity saved = opportunityRepository.save(
                                access.tenantId(), opportunity, fields);
                        fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                                BusinessObjectType.OPPORTUNITY,
                                new AttachmentTarget(AttachmentTargetType.OPPORTUNITY, saved.getId().getValue()),
                                request.getFileFields(), byCode, fieldAccess);
                        return output(caller, access, saved, fieldAccess, false);
                    }
                });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long opportunityId, Long version) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_DELETE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_DELETE,
                                OpportunityAccessMode.OWNER_SCOPE);
                        id = id(opportunityId);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                        Opportunity opportunity = requireVisibleForUpdate(access, id);
                        support.requireVersion(opportunity, version);
                        opportunity.delete();
                        attachmentParentLifecycle.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.OPPORTUNITY, id.getValue()));
                        fileFieldBindingService.withdrawAll(access.tenantId(),
                                new AttachmentTarget(AttachmentTargetType.OPPORTUNITY, id.getValue()));
                        opportunityRepository.save(access.tenantId(), opportunity,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, OpportunityBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_BATCH_DELETE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private List<OpportunityBatchVersionRequest> targets;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_DELETE,
                                OpportunityAccessMode.OWNER_SCOPE);
                        requireRequest(request, "Opportunity batch delete request");
                        targets = batchTargets(request.getOpportunities(),
                                "Opportunity batch delete targets");
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                        List<Opportunity> opportunities = batchOpportunities(access, targets);
                        for (int index = 0; index < opportunities.size(); index++) {
                            support.requireVersion(opportunities.get(index),
                                    targets.get(index).getVersion());
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        for (Opportunity opportunity : opportunities) {
                            OpportunityId id = opportunity.getId();
                            opportunity.delete();
                            AttachmentTarget target = new AttachmentTarget(
                                    AttachmentTargetType.OPPORTUNITY, id.getValue());
                            attachmentParentLifecycle.withdrawAll(access.tenantId(), target);
                            fileFieldBindingService.withdrawAll(access.tenantId(), target);
                            opportunityRepository.save(access.tenantId(), opportunity, fields);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<OpportunityVO> updateOwner(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOwnerUpdateRequest request) {
        return mutate(caller, opportunityId, request,
                UseCaseOperation.OPPORTUNITY_OWNER_UPDATE,
                AuthorityCatalog.OPPORTUNITIES_ASSIGN,
                OpportunityAccessMode.OWNER_SCOPE,
                (opportunity, access, fieldAccess) -> {
                    support.requireVersion(opportunity, request.getVersion());
                    support.requireWrite(fieldAccess, OpportunityFieldCatalog.OWNER_ACTOR);
                    MemberId owner = new MemberId(request.getOwnerActorId());
                    OrganizationMembership ownerMembership = support.requireActiveMember(
                            access.tenantId(), owner);
                    requireAssignableOwner(ownerMembership, access.visibility());
                    opportunity.changeOwner(owner);
                }, true);
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, OpportunityBatchOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_BATCH_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private List<OpportunityBatchVersionRequest> targets;
                    private MemberId owner;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_ASSIGN,
                                OpportunityAccessMode.OWNER_SCOPE);
                        requireRequest(request, "Opportunity batch owner request");
                        targets = batchTargets(request.getOpportunities(),
                                "Opportunity batch owner targets");
                        owner = new MemberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        requireAssignableOwner(ownerMembership, access.visibility());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, OpportunityFieldCatalog.OWNER_ACTOR);
                        List<Opportunity> opportunities = batchOpportunities(access, targets);
                        for (int index = 0; index < opportunities.size(); index++) {
                            support.requireVersion(opportunities.get(index),
                                    targets.get(index).getVersion());
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        for (Opportunity opportunity : opportunities) {
                            opportunity.changeOwner(owner);
                            opportunityRepository.save(access.tenantId(), opportunity, fields);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<OpportunityVO> replaceCollaborators(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityCollaboratorsUpdateRequest request) {
        return mutate(caller, opportunityId, request,
                UseCaseOperation.OPPORTUNITY_COLLABORATOR_REPLACE,
                AuthorityCatalog.OPPORTUNITIES_ASSIGN,
                OpportunityAccessMode.OWNER_SCOPE,
                (opportunity, access, fieldAccess) -> {
                    support.requireVersion(opportunity, request.getVersion());
                    List<OpportunityCollaborator> values = support.collaborators(
                            request.getCollaborators());
                    support.requireActiveMembers(access.tenantId(), values.stream()
                            .map(OpportunityCollaborator::memberId).toList());
                    opportunity.replaceCollaborators(values);
                }, true);
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long opportunityId, Long version) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_COLLABORATOR_EXIT,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        id = id(opportunityId);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(),
                                BusinessObjectType.OPPORTUNITY);
                        Opportunity opportunity = requireVisible(access, id);
                        support.requireVersion(opportunity, version);
                        if (!opportunity.exitCollaborator(access.memberId())) {
                            throw new OpportunityNotFoundException();
                        }
                        opportunityRepository.save(access.tenantId(), opportunity,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<OpportunityVO> replaceContacts(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityContactsUpdateRequest request) {
        return mutate(caller, opportunityId, request,
                UseCaseOperation.OPPORTUNITY_CONTACT_REPLACE,
                AuthorityCatalog.OPPORTUNITIES_WRITE,
                OpportunityAccessMode.WRITE,
                (opportunity, access, fieldAccess) -> {
                    support.requireVersion(opportunity, request.getVersion());
                    support.requireWrite(fieldAccess, OpportunityFieldCatalog.CONTACTS);
                    support.requireWrite(fieldAccess,
                            OpportunityFieldCatalog.PRIMARY_CONTACT);
                    ContactAccess contactAccess = contactAccessResolver.access(caller,
                            AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
                    List<ContactId> contacts = contactIds(request.getContactIds());
                    ContactId primary = contactId(request.getPrimaryContactId());
                    requireVisibleContacts(contactAccess, opportunity.getCustomerId(), contacts, primary);
                    opportunity.replaceContacts(contacts, primary);
                }, false);
    }

    @Override
    public Result<OpportunityVO> updatePrimaryContact(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityPrimaryContactUpdateRequest request) {
        return mutate(caller, opportunityId, request,
                UseCaseOperation.OPPORTUNITY_PRIMARY_CONTACT_UPDATE,
                AuthorityCatalog.OPPORTUNITIES_WRITE,
                OpportunityAccessMode.WRITE,
                (opportunity, access, fieldAccess) -> {
                    support.requireVersion(opportunity, request.getVersion());
                    support.requireWrite(fieldAccess,
                            OpportunityFieldCatalog.PRIMARY_CONTACT);
                    support.requireReadableUnmasked(fieldAccess,
                            OpportunityFieldCatalog.CONTACTS);
                    ContactId primary = contactId(request.getContactId());
                    if (primary != null) {
                        ContactAccess contactAccess = contactAccessResolver.access(caller,
                                AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
                        requireVisibleContacts(contactAccess, opportunity.getCustomerId(),
                                List.of(primary), primary);
                    }
                    opportunity.changePrimaryContact(primary);
                }, false);
    }

    @Override
    public Result<OpportunityVO> updateStage(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStageUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_STAGE_UPDATE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_WRITE,
                                OpportunityAccessMode.WRITE);
                        requireRequest(request, "Opportunity stage request");
                        id = id(opportunityId);
                    }

                    @Override
                    public OpportunityVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                        pipelinePairValidator.acquire(access.tenantId());
                        Opportunity opportunity = requireVisible(access, id);
                        support.requireVersion(opportunity, request.getVersion());
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess,
                                OpportunityFieldCatalog.PIPELINE_CODE);
                        support.requireWrite(fieldAccess, OpportunityFieldCatalog.STAGE_CODE);
                        OpportunityPipelinePairValidator.Pair pair = pipelinePairValidator.normalize(
                                request.getPipelineCode(), request.getStageCode());
                        if (pair.sameAs(opportunity.getPipelineCode(), opportunity.getStageCode())) {
                            return output(caller, access, opportunity, fieldAccess, false);
                        }
                        pipelinePairValidator.requireActive(access.tenantId(), pair);
                        opportunity.changeStage(pair.pipelineCode(), pair.stageCode());
                        Opportunity saved = opportunityRepository.save(
                                access.tenantId(), opportunity, fields);
                        return output(caller, access, saved, fieldAccess, false);
                    }
                });
    }

    @Override
    public Result<OpportunityVO> updateOutcome(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityOutcomeUpdateRequest request) {
        return mutate(caller, opportunityId, request,
                UseCaseOperation.OPPORTUNITY_OUTCOME_UPDATE,
                AuthorityCatalog.OPPORTUNITIES_WRITE,
                OpportunityAccessMode.WRITE,
                (opportunity, access, fieldAccess) -> {
                    support.requireVersion(opportunity, request.getVersion());
                    support.requireWrite(fieldAccess, OpportunityFieldCatalog.OUTCOME);
                    support.requireWrite(fieldAccess,
                            OpportunityFieldCatalog.OUTCOME_REMARK);
                    opportunity.changeOutcome(support.parse(OpportunityOutcome.class,
                            request.getOutcome(), "Opportunity outcome"), request.getRemark());
                }, false);
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long opportunityId,
            OpportunityStarUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.OPPORTUNITY_STAR_UPDATE,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        requireRequest(request, "Opportunity star request");
                        id = id(opportunityId);
                    }

                    @Override
                    public Void execute() {
                        requireVisible(access, id);
                        starRepository.setStarred(access.tenantId(), access.memberId(),
                                id, Boolean.TRUE.equals(request.getStarred()));
                        return null;
                    }
                });
    }

    private Result<OpportunityVO> mutate(
            AuthenticatedCaller caller, Long value, Object request,
            UseCaseOperation operation, String authority,
            OpportunityAccessMode mode, Mutation mutation,
            boolean collaboratorsVisible) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private OpportunityAccess access;
            private OpportunityId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, authority, mode);
                requireRequest(request, "Opportunity mutation request");
                id = id(value);
            }

            @Override
            public OpportunityVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.OPPORTUNITY);
                Opportunity opportunity = requireVisible(access, id);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                var fieldAccess = support.accesses(
                        access.tenantId(), access.memberId());
                mutation.apply(opportunity, access, fieldAccess);
                Opportunity saved = opportunityRepository.save(
                        access.tenantId(), opportunity, fields);
                return output(caller, access, saved, fieldAccess,
                        collaboratorsVisible);
            }
        });
    }

    private OpportunityVO output(
            AuthenticatedCaller caller, OpportunityAccess access,
            Opportunity opportunity,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> fieldAccess,
            boolean collaboratorsVisible) {
        return support.toVO(opportunity, starRepository.isStarred(
                        access.tenantId(), access.memberId(), opportunity.getId()),
                fieldAccess, customerVisible(caller, opportunity),
                visibleContacts(caller, opportunity), collaboratorsVisible);
    }

    private boolean customerVisible(
            AuthenticatedCaller caller, Opportunity opportunity) {
        try {
            CustomerAccess access = customerAccessResolver.access(caller,
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            return customerRepository.findVisibleById(access.tenantId(),
                    opportunity.getCustomerId(), access.visibility()).isPresent();
        } catch (NonRetryableApplicationException exception) {
            return false;
        }
    }

    private Set<ContactId> visibleContacts(
            AuthenticatedCaller caller, Opportunity opportunity) {
        if (opportunity.getContactIds().isEmpty()) {
            return Set.of();
        }
        try {
            ContactAccess access = contactAccessResolver.access(caller,
                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
            return contactRepository.findVisibleByIds(access.tenantId(),
                            opportunity.getContactIds(), access.visibility(),
                            opportunity.getContactIds().size())
                    .stream().map(io.github.archetom.crm.domain.entity.Contact::getId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private void requireVisibleCustomer(CustomerAccess access, Long customerId) {
        if (access == null || customerId == null
                || customerRepository.findVisibleById(access.tenantId(),
                        new CustomerId(customerId), access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Customer was not found");
        }
    }

    private void requireVisibleContacts(
            ContactAccess access, CustomerId customerId, Collection<ContactId> contacts,
            ContactId primary) {
        Set<ContactId> requested = new LinkedHashSet<>(
                contacts == null ? List.of() : contacts);
        if (primary != null) {
            requested.add(primary);
        }
        if (requested.isEmpty()) {
            return;
        }
        if (access == null) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Contact was not found");
        }
        List<Contact> visible = contactRepository.findVisibleByIds(
                        access.tenantId(), requested, access.visibility(), requested.size())
                .stream().toList();
        Set<ContactId> visibleIds = visible.stream().map(Contact::getId)
                .collect(Collectors.toSet());
        if (!visibleIds.containsAll(requested)) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Contact was not found");
        }
        if (customerId == null || visible.stream().anyMatch(contact ->
                contact.getCustomerId() == null
                        || !contact.getCustomerId().sameValueAs(customerId))) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact belongs to another customer");
        }
    }

    private void requireCreateFields(
            OpportunityCreateRequest request,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> access) {
        requireWrites(access, OpportunityFieldCatalog.NAME,
                OpportunityFieldCatalog.CUSTOMER,
                OpportunityFieldCatalog.PIPELINE_CODE,
                OpportunityFieldCatalog.STAGE_CODE,
                OpportunityFieldCatalog.OWNER_ACTOR);
        if (request.getExpectedCloseDate() != null) {
            support.requireWrite(access, OpportunityFieldCatalog.EXPECTED_CLOSE_DATE);
        }
        if (request.getEstimatedAmount() != null) {
            support.requireWrite(access, OpportunityFieldCatalog.ESTIMATED_AMOUNT);
        }
        if (request.getNextContactTime() != null) {
            support.requireWrite(access, OpportunityFieldCatalog.NEXT_CONTACT_TIME);
        }
        if (request.getDescription() != null) {
            support.requireWrite(access, OpportunityFieldCatalog.DESCRIPTION);
        }
        if (hasContacts(request.getContactIds(), request.getPrimaryContactId())) {
            requireWrites(access, OpportunityFieldCatalog.CONTACTS,
                    OpportunityFieldCatalog.PRIMARY_CONTACT);
        }
    }

    private void requireUpdateFields(
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> access) {
        requireWrites(access, OpportunityFieldCatalog.NAME,
                OpportunityFieldCatalog.CUSTOMER,
                OpportunityFieldCatalog.PIPELINE_CODE,
                OpportunityFieldCatalog.STAGE_CODE,
                OpportunityFieldCatalog.EXPECTED_CLOSE_DATE,
                OpportunityFieldCatalog.ESTIMATED_AMOUNT,
                OpportunityFieldCatalog.NEXT_CONTACT_TIME,
                OpportunityFieldCatalog.DESCRIPTION);
    }

    private void requireWrites(
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> access,
            String... codes) {
        for (String code : codes) {
            support.requireWrite(access, code);
        }
    }

    private Opportunity requireVisible(
            OpportunityAccess access, OpportunityId id) {
        return opportunityRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(OpportunityNotFoundException::new);
    }

    private List<OpportunityBatchVersionRequest> batchTargets(
            List<OpportunityBatchVersionRequest> requests, String description) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " are invalid");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (OpportunityBatchVersionRequest request : requests) {
            if (request == null || request.getOpportunityId() == null || request.getVersion() == null
                    || request.getVersion() < 0 || !ids.add(request.getOpportunityId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        description + " are invalid");
            }
            id(request.getOpportunityId());
        }
        return requests.stream()
                .sorted(Comparator.comparing(OpportunityBatchVersionRequest::getOpportunityId))
                .toList();
    }

    private List<Opportunity> batchOpportunities(
            OpportunityAccess access, List<OpportunityBatchVersionRequest> targets) {
        return targets.stream().map(target ->
                        requireVisibleForUpdate(access, id(target.getOpportunityId())))
                .toList();
    }

    private Opportunity requireVisibleForUpdate(
            OpportunityAccess access, OpportunityId id) {
        return opportunityRepository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(OpportunityNotFoundException::new);
    }

    private void requireAssignableOwner(
            OrganizationMembership owner, OpportunityVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(visibility.callerMemberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Opportunity owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !visibility.ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Opportunity owner is outside the assignment scope");
        }
    }

    private List<ContactId> contactIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        try {
            return values.stream().map(ContactId::new).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity contact ID is invalid");
        }
    }

    private ContactId contactId(Long value) {
        try {
            return value == null ? null : new ContactId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity contact ID is invalid");
        }
    }

    private OpportunityId id(Long value) {
        try {
            return new OpportunityId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity ID must be positive");
        }
    }

    private boolean hasContacts(List<Long> contacts, Long primary) {
        return primary != null || contacts != null && !contacts.isEmpty();
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(
                Opportunity opportunity, OpportunityAccess access,
                Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess>
                        fieldAccess);
    }
}
