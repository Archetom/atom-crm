package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchTagRequest;
import io.github.archetom.crm.api.dto.request.ContactBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ContactCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactVersionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.ContactStructureLock;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContactAccessResolver.ContactAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.service.ContactCommandService;
import io.github.archetom.crm.application.service.ContactCreationCoordinator;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactAddress;
import io.github.archetom.crm.domain.entity.ContactCollaborator;
import io.github.archetom.crm.domain.entity.ContactFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.ContactNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactDealStatus;
import io.github.archetom.crm.domain.model.ContactHierarchyStats;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContactStarRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ContactCommandServiceImpl implements ContactCommandService, ContactCreationCoordinator {

    private static final int MAX_HIERARCHY_NODES = ContactHierarchyStats.MAX_TREE_NODES;
    private static final int MAX_HIERARCHY_DEPTH = ContactHierarchyStats.MAX_DEPTH;
    private static final int HIERARCHY_NODE_PROBE = MAX_HIERARCHY_NODES + 1;
    private static final int HIERARCHY_DEPTH_PROBE = MAX_HIERARCHY_DEPTH + 1;

    private final ContactRepository contactRepository;
    private final ContactStarRepository starRepository;
    private final ContactAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContactApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final ContactStructureLock structureLock;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<ContactVO> create(
            AuthenticatedCaller caller, ContactCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_CREATE, createOperation(caller, request));
    }

    @Override
    public ContactVO createInCurrentTransaction(AuthenticatedCaller caller, ContactCreateRequest request) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Contact creation coordination requires an active transaction");
        }
        ServiceOperation<ContactVO> operation = createOperation(caller, request);
        operation.validate();
        operation.prepare();
        ContactVO result = operation.execute();
        operation.onSuccess(result);
        return result;
    }

    private ServiceOperation<ContactVO> createOperation(
            AuthenticatedCaller caller, ContactCreateRequest request) {
        return new ServiceOperation<>() {
            private ContactAccess writeAccess;
            private ContactAccess assignAccess;
            private CustomerAccess customerReadAccess;
            private MemberId ownerMemberId;

            @Override
            public void validate() {
                writeAccess = accessResolver.access(
                        caller, AuthorityCatalog.CONTACTS_WRITE, ContactAccessMode.WRITE);
                requireRequest(request, "Contact create request");
                ownerMemberId = new MemberId(request.getOwnerActorId() == null
                        ? caller.actorId() : request.getOwnerActorId());
                if (!ownerMemberId.sameValueAs(writeAccess.memberId())
                        || request.getCollaborators() != null && !request.getCollaborators().isEmpty()) {
                    assignAccess = accessResolver.access(
                            caller, AuthorityCatalog.CONTACTS_ASSIGN, ContactAccessMode.OWNER_SCOPE);
                }
                if (request.getCustomerId() != null) {
                    customerReadAccess = customerAccessResolver.access(
                            caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                }
            }

            @Override
            public ContactVO execute() {
                objectLock.acquire(writeAccess.tenantId(), BusinessObjectType.CONTACT);
                List<FieldDefinition> fields = support.fields(writeAccess.tenantId());
                Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                var fieldAccess = support.accesses(writeAccess.tenantId(), writeAccess.memberId());
                requireCreateFieldAccess(request, fieldAccess);
                OrganizationMembership owner = support.requireActiveMember(
                        writeAccess.tenantId(), ownerMemberId);
                if (assignAccess != null) {
                    requireAssignableOwner(owner, assignAccess.visibility());
                }
                List<ContactAddress> addresses = support.addresses(request.getAddresses());
                List<ContactFieldValue> customFields = support.customFields(
                        request.getCustomFields(), List.of(), true, fieldsByCode, fieldAccess);
                Contact contact = Contact.create(writeAccess.tenantId(), ownerMemberId,
                        request.getName(), request.getPhoneNumber(), request.getTelephone(),
                        request.getJobTitle(), request.getEmail(), request.getWebsite(),
                        request.getDescription(), request.getNextContactTime(),
                        addresses, customFields);
                contact.replaceTags(request.getTags());
                List<ContactCollaborator> collaborators = support.collaborators(
                        request.getCollaborators());
                support.requireActiveMembers(writeAccess.tenantId(), collaborators.stream()
                        .map(ContactCollaborator::memberId).toList());
                contact.replaceCollaborators(collaborators);
                if (request.getCustomerId() != null) {
                    support.requireWrite(fieldAccess, ContactFieldCatalog.CUSTOMER);
                    requireVisibleCustomer(customerReadAccess, request.getCustomerId());
                    contact.changeCustomer(new CustomerId(request.getCustomerId()));
                }
                if (request.getParentContactId() != null) {
                    structureLock.acquire(writeAccess.tenantId());
                    ContactId parentId = new ContactId(request.getParentContactId());
                    requireVisible(writeAccess, parentId);
                    requireCreateHierarchyCapacity(writeAccess, parentId);
                    contact.changeParent(parentId);
                }
                Contact saved = contactRepository.save(
                        writeAccess.tenantId(), contact, fields);
                fileFieldBindingService.consumeCreateDraft(writeAccess.tenantId(), writeAccess.memberId(),
                        BusinessObjectType.CONTACT, request.getFileFieldDraftId(),
                        new AttachmentTarget(AttachmentTargetType.CONTACT, saved.getId().getValue()),
                        request.getFileFields(), fieldsByCode, fieldAccess);
                return support.toVO(saved, false, fieldAccess,
                        visibleParentIds(List.of(saved), writeAccess),
                        visibleCustomerIds(caller, List.of(saved)), assignAccess != null);
            }
        };
    }

    @Override
    public Result<ContactVO> update(
            AuthenticatedCaller caller, Long contactId, ContactUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_UPDATE, new ServiceOperation<>() {
            private ContactAccess access;
            private CustomerAccess customerReadAccess;
            private ContactId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CONTACTS_WRITE, ContactAccessMode.WRITE);
                id = id(contactId);
                requireRequest(request, "Contact update request");
                if (request.customerIdSpecified() && request.getCustomerId() != null) {
                    customerReadAccess = customerAccessResolver.access(
                            caller, AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                }
            }

            @Override
            public ContactVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                Contact contact = requireVisible(access, id);
                support.requireVersion(contact, request.getVersion());
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                String name = patched(request.getName(), contact.getName(), fieldAccess,
                        ContactFieldCatalog.NAME);
                String phone = patched(request.getPhoneNumber(), contact.getPhoneNumber(), fieldAccess,
                        ContactFieldCatalog.PHONE_NUMBER);
                String telephone = patched(request.getTelephone(), contact.getTelephone(), fieldAccess,
                        ContactFieldCatalog.TELEPHONE);
                String jobTitle = patched(request.getJobTitle(), contact.getJobTitle(), fieldAccess,
                        ContactFieldCatalog.JOB_TITLE);
                String email = patched(request.getEmail(), contact.getEmail(), fieldAccess,
                        ContactFieldCatalog.EMAIL);
                String website = patched(request.getWebsite(), contact.getWebsite(), fieldAccess,
                        ContactFieldCatalog.WEBSITE);
                String description = patched(request.getDescription(), contact.getDescription(), fieldAccess,
                        ContactFieldCatalog.DESCRIPTION);
                LocalDateTime nextContactTime = patched(request.nextContactTimeSpecified(),
                        request.getNextContactTime(),
                        contact.getNextContactTime(), fieldAccess, ContactFieldCatalog.NEXT_CONTACT_TIME);
                List<ContactAddress> addresses = contact.getAddresses();
                if (request.getAddresses() != null) {
                    support.requireWrite(fieldAccess, ContactFieldCatalog.ADDRESSES);
                    addresses = support.addresses(request.getAddresses());
                }
                List<ContactFieldValue> customFields = support.customFields(
                        request.getCustomFields(), contact.getCustomFieldValues(), false,
                        fieldsByCode, fieldAccess);
                contact.replaceDetails(name, phone, telephone, jobTitle, email, website, description,
                        nextContactTime,
                        addresses, customFields);
                if (request.customerIdSpecified()) {
                    support.requireWrite(fieldAccess, ContactFieldCatalog.CUSTOMER);
                    if (request.getCustomerId() != null) {
                        requireVisibleCustomer(customerReadAccess, request.getCustomerId());
                        contact.changeCustomer(new CustomerId(request.getCustomerId()));
                    } else {
                        contact.changeCustomer(null);
                    }
                }
                Contact saved = contactRepository.save(access.tenantId(), contact, fields);
                fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                        BusinessObjectType.CONTACT,
                        new AttachmentTarget(AttachmentTargetType.CONTACT, saved.getId().getValue()),
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
            AuthenticatedCaller caller, Long contactId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_DELETE, new ServiceOperation<>() {
            private ContactAccess access;
            private ContactId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CONTACTS_DELETE, ContactAccessMode.OWNER_SCOPE);
                id = id(contactId);
            }

            @Override
            public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                structureLock.acquire(access.tenantId());
                Contact contact = requireVisibleForUpdate(access, id);
                support.requireVersion(contact, version);
                if (contactRepository.existsActiveChild(access.tenantId(), id)) {
                    throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Contact with child contacts cannot be deleted");
                }
                List<FieldDefinition> fields = support.fields(access.tenantId());
                contact.delete();
                attachmentParentLifecycle.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.CONTACT, id.getValue()));
                fileFieldBindingService.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.CONTACT, id.getValue()));
                contactRepository.save(access.tenantId(), contact, fields);
                return null;
            }
        });
    }

    @Override
    public Result<Void> deleteBatch(
            AuthenticatedCaller caller, ContactBatchDeleteRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_BATCH_DELETE, new ServiceOperation<>() {
            private ContactAccess access;
            private List<ContactBatchVersionRequest> targets;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CONTACTS_DELETE, ContactAccessMode.OWNER_SCOPE);
                requireRequest(request, "Contact batch delete request");
                targets = batchTargets(request.getContacts(), "Contact batch delete targets");
            }

            @Override
            public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                structureLock.acquire(access.tenantId());
                List<Contact> contacts = batchContacts(access, targets);
                for (int index = 0; index < contacts.size(); index++) {
                    Contact contact = contacts.get(index);
                    ContactId id = contact.getId();
                    support.requireVersion(contact, targets.get(index).getVersion());
                    if (contactRepository.existsActiveChild(access.tenantId(), id)) {
                        throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Contact with child contacts cannot be deleted");
                    }
                }
                List<FieldDefinition> fields = support.fields(access.tenantId());
                for (Contact contact : contacts) {
                    ContactId id = contact.getId();
                    contact.delete();
                    attachmentParentLifecycle.withdrawAll(access.tenantId(),
                            new AttachmentTarget(AttachmentTargetType.CONTACT, id.getValue()));
                    fileFieldBindingService.withdrawAll(access.tenantId(),
                            new AttachmentTarget(AttachmentTargetType.CONTACT, id.getValue()));
                    contactRepository.save(access.tenantId(), contact, fields);
                }
                return null;
            }
        });
    }

    @Override
    public Result<ContactVO> updateState(
            AuthenticatedCaller caller, Long contactId, ContactStateUpdateRequest request) {
        return mutate(caller, contactId, request, UseCaseOperation.CONTACT_STATE_UPDATE,
                (contact, access, fields, fieldAccess) -> {
                    support.requireWrite(fieldAccess, ContactFieldCatalog.LOCKED);
                    support.requireWrite(fieldAccess, ContactFieldCatalog.DEAL_STATUS);
                    contact.changeState(Boolean.TRUE.equals(request.getLocked()),
                            support.parse(ContactDealStatus.class, request.getDealStatus(),
                                    "Contact deal status"));
                });
    }

    @Override
    public Result<ContactVO> updateOwner(
            AuthenticatedCaller caller, Long contactId, ContactOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_OWNER_UPDATE, new ServiceOperation<>() {
            private ContactAccess access;
            private ContactId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CONTACTS_ASSIGN, ContactAccessMode.OWNER_SCOPE);
                id = id(contactId);
                requireRequest(request, "Contact owner request");
            }

            @Override
            public ContactVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                Contact contact = requireVisible(access, id);
                support.requireVersion(contact, request.getVersion());
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                support.requireWrite(fieldAccess, ContactFieldCatalog.OWNER_ACTOR);
                MemberId ownerId = new MemberId(request.getOwnerActorId());
                OrganizationMembership owner = support.requireActiveMember(access.tenantId(), ownerId);
                requireAssignableOwner(owner, access.visibility());
                contact.changeOwner(ownerId);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Contact saved = contactRepository.save(access.tenantId(), contact, fields);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access),
                        visibleCustomerIds(caller, List.of(saved)), true);
            }
        });
    }

    @Override
    public Result<Void> updateOwners(
            AuthenticatedCaller caller, ContactBatchOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_BATCH_OWNER_UPDATE,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private List<ContactBatchVersionRequest> targets;
                    private MemberId owner;

                    @Override
                    public void validate() {
                        access = accessResolver.access(
                                caller, AuthorityCatalog.CONTACTS_ASSIGN,
                                ContactAccessMode.OWNER_SCOPE);
                        requireRequest(request, "Contact batch owner request");
                        targets = batchTargets(request.getContacts(), "Contact batch owner targets");
                        owner = new MemberId(request.getOwnerActorId());
                        OrganizationMembership ownerMembership = support.requireActiveMember(
                                access.tenantId(), owner);
                        requireAssignableOwner(ownerMembership, access.visibility());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, ContactFieldCatalog.OWNER_ACTOR);
                        List<Contact> contacts = batchContacts(access, targets);
                        for (int index = 0; index < contacts.size(); index++) {
                            support.requireVersion(contacts.get(index), targets.get(index).getVersion());
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        for (Contact contact : contacts) {
                            contact.changeOwner(owner);
                            contactRepository.save(access.tenantId(), contact, fields);
                        }
                        return null;
                    }
                });
    }

    @Override
    public Result<ContactVO> replaceTags(
            AuthenticatedCaller caller, Long contactId, ContactTagsUpdateRequest request) {
        return mutate(caller, contactId, request, UseCaseOperation.CONTACT_TAG_REPLACE,
                (contact, access, fields, fieldAccess) -> {
                    support.requireWrite(fieldAccess, ContactFieldCatalog.TAGS);
                    contact.replaceTags(request.getTags());
                });
    }

    @Override
    public Result<List<ContactVO>> changeTags(
            AuthenticatedCaller caller, ContactBatchTagRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_TAG_BATCH_CHANGE,
                new ServiceOperation<>() {
                    private ContactAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_WRITE,
                                ContactAccessMode.WRITE);
                        requireRequest(request, "Contact batch tag request");
                        if (request.getContacts() == null || request.getContacts().isEmpty()) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Contact batch must not be empty");
                        }
                    }

                    @Override
                    public List<ContactVO> execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, ContactFieldCatalog.TAGS);
                        Map<ContactId, Long> versions = versions(request.getContacts());
                        List<Contact> contacts = contactRepository.findVisibleByIds(
                                access.tenantId(), versions.keySet(), access.visibility(), 100);
                        if (contacts.size() != versions.size()) {
                            throw new ContactNotFoundException();
                        }
                        Set<ContactId> visibleParents = visibleParentIds(contacts, access);
                        Set<CustomerId> visibleCustomers = visibleCustomerIds(caller, contacts);
                        Set<ContactId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), versions.keySet());
                        List<ContactVO> result = new ArrayList<>();
                        for (Contact contact : contacts) {
                            support.requireVersion(contact, versions.get(contact.getId()));
                            contact.changeTags(request.getAddedTags(), request.getRemovedTags());
                            Contact saved = contactRepository.save(
                                    access.tenantId(), contact, fields);
                            result.add(support.toVO(saved, starred.contains(saved.getId()),
                                    fieldAccess, visibleParents, visibleCustomers, false));
                        }
                        return List.copyOf(result);
                    }
                });
    }

    @Override
    public Result<ContactVO> replaceCollaborators(
            AuthenticatedCaller caller, Long contactId,
            ContactCollaboratorsUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_COLLABORATOR_REPLACE,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private ContactId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_ASSIGN,
                                ContactAccessMode.OWNER_SCOPE);
                        id = id(contactId);
                        requireRequest(request, "Contact collaborator request");
                    }

                    @Override
                    public ContactVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                        Contact contact = requireVisible(access, id);
                        support.requireVersion(contact, request.getVersion());
                        List<ContactCollaborator> collaborators = support.collaborators(
                                request.getCollaborators());
                        support.requireActiveMembers(access.tenantId(), collaborators.stream()
                                .map(ContactCollaborator::memberId).toList());
                        contact.replaceCollaborators(collaborators);
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        Contact saved = contactRepository.save(access.tenantId(), contact, fields);
                        return support.toVO(saved, starRepository.isStarred(
                                        access.tenantId(), access.memberId(), id), fieldAccess,
                                visibleParentIds(List.of(saved), access),
                                visibleCustomerIds(caller, List.of(saved)), true);
                    }
                });
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long contactId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_COLLABORATOR_EXIT,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private ContactId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                        id = id(contactId);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                        Contact contact = requireVisible(access, id);
                        support.requireVersion(contact, version);
                        if (!contact.exitCollaborator(access.memberId())) {
                            throw new ContactNotFoundException();
                        }
                        contactRepository.save(access.tenantId(), contact,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<ContactVO> updateParent(
            AuthenticatedCaller caller, Long contactId, ContactParentUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_PARENT_UPDATE,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private ContactId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_WRITE,
                                ContactAccessMode.WRITE);
                        id = id(contactId);
                        requireRequest(request, "Contact parent request");
                    }

                    @Override
                    public ContactVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                        structureLock.acquire(access.tenantId());
                        Contact contact = requireVisible(access, id);
                        support.requireVersion(contact, request.getVersion());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, ContactFieldCatalog.PARENT_CONTACT);
                        ContactId parentId = request.getParentContactId() == null
                                ? null : new ContactId(request.getParentContactId());
                        if (parentId != null) {
                            requireVisible(access, parentId);
                            ContactHierarchyStats sourceStats = hierarchyStats(access, id);
                            ContactHierarchyStats parentStats = hierarchyStats(access, parentId);
                            requireQueryableHierarchy(sourceStats);
                            requireQueryableHierarchy(parentStats);
                            List<Contact> descendants = contactRepository.findDescendants(
                                    access.tenantId(), id, HIERARCHY_NODE_PROBE);
                            if (descendants.stream().anyMatch(value -> value.getId().sameValueAs(parentId))) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Contact hierarchy cannot contain a cycle");
                            }
                            requireMoveHierarchyCapacity(sourceStats, parentStats);
                        }
                        contact.changeParent(parentId);
                        Contact saved = contactRepository.save(
                                access.tenantId(), contact, support.fields(access.tenantId()));
                        return support.toVO(saved, starRepository.isStarred(
                                        access.tenantId(), access.memberId(), id), fieldAccess,
                                visibleParentIds(List.of(saved), access),
                                visibleCustomerIds(caller, List.of(saved)), false);
                    }
                });
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long contactId, ContactStarUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_STAR_UPDATE,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private ContactId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                        id = id(contactId);
                        requireRequest(request, "Contact star request");
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

    private Result<ContactVO> mutate(
            AuthenticatedCaller caller, Long contactId, Object request,
            UseCaseOperation operation, ContactMutation mutation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private ContactAccess access;
            private ContactId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_WRITE,
                        ContactAccessMode.WRITE);
                id = id(contactId);
                requireRequest(request, "Contact mutation request");
            }

            @Override
            public ContactVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CONTACT);
                Contact contact = requireVisible(access, id);
                Long version = request instanceof ContactStateUpdateRequest value ? value.getVersion()
                        : ((ContactTagsUpdateRequest) request).getVersion();
                support.requireVersion(contact, version);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                mutation.apply(contact, access, fields, fieldAccess);
                Contact saved = contactRepository.save(access.tenantId(), contact, fields);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access),
                        visibleCustomerIds(caller, List.of(saved)), false);
            }
        });
    }

    private void requireCreateHierarchyCapacity(
            ContactAccess access, ContactId parentId) {
        ContactHierarchyStats parentStats = hierarchyStats(access, parentId);
        requireQueryableHierarchy(parentStats);
        if (parentStats.treeNodeCount() >= MAX_HIERARCHY_NODES
                || parentStats.depth() >= MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
    }

    private ContactHierarchyStats hierarchyStats(
            ContactAccess access, ContactId contactId) {
        return contactRepository.hierarchyStats(
                        access.tenantId(), contactId,
                        HIERARCHY_NODE_PROBE, HIERARCHY_DEPTH_PROBE)
                .orElseThrow(ContactNotFoundException::new);
    }

    private void requireQueryableHierarchy(ContactHierarchyStats stats) {
        if (stats.depth() > MAX_HIERARCHY_DEPTH
                || stats.subtreeNodeCount() > MAX_HIERARCHY_NODES
                || stats.subtreeHeight() > MAX_HIERARCHY_DEPTH
                || stats.treeNodeCount() > MAX_HIERARCHY_NODES
                || stats.treeHeight() > MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
    }

    private void requireMoveHierarchyCapacity(
            ContactHierarchyStats source, ContactHierarchyStats parent) {
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
                "Contact hierarchy exceeds the supported limit");
    }

    private Set<ContactId> visibleParentIds(
            Collection<Contact> contacts, ContactAccess access) {
        Set<ContactId> parentIds = new LinkedHashSet<>();
        if (contacts != null) {
            contacts.stream().map(Contact::getParentContactId)
                    .filter(java.util.Objects::nonNull).forEach(parentIds::add);
        }
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return contactRepository.findVisibleByIds(
                        access.tenantId(), parentIds, access.visibility(), parentIds.size())
                .stream().map(Contact::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<CustomerId> visibleCustomerIds(
            AuthenticatedCaller caller, Collection<Contact> contacts) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)
                || contacts == null || contacts.isEmpty()) {
            return Set.of();
        }
        Set<CustomerId> ids = contacts.stream().map(Contact::getCustomerId)
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
            ContactCreateRequest request,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses) {
        support.requireWrite(accesses, ContactFieldCatalog.NAME);
        support.requireWrite(accesses, ContactFieldCatalog.OWNER_ACTOR);
        if (request.getPhoneNumber() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.PHONE_NUMBER);
        }
        if (request.getTelephone() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.TELEPHONE);
        }
        if (request.getJobTitle() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.JOB_TITLE);
        }
        if (request.getEmail() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.EMAIL);
        }
        if (request.getWebsite() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.WEBSITE);
        }
        if (request.getDescription() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.DESCRIPTION);
        }
        if (request.getNextContactTime() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.NEXT_CONTACT_TIME);
        }
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            support.requireWrite(accesses, ContactFieldCatalog.TAGS);
        }
        if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
            support.requireWrite(accesses, ContactFieldCatalog.ADDRESSES);
        }
        if (request.getParentContactId() != null) {
            support.requireWrite(accesses, ContactFieldCatalog.PARENT_CONTACT);
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
            OrganizationMembership owner, ContactVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(visibility.callerMemberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Contact owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !visibility.ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Contact owner is outside the assignment scope");
        }
    }

    private Map<ContactId, Long> versions(List<ContactVersionRequest> requests) {
        Map<ContactId, Long> values = new LinkedHashMap<>();
        for (ContactVersionRequest request : requests) {
            if (request == null || request.getVersion() == null
                    || values.putIfAbsent(id(request.getContactId()), request.getVersion()) != null) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contact batch entries must be unique");
            }
        }
        return values;
    }

    private List<ContactBatchVersionRequest> batchTargets(
            List<ContactBatchVersionRequest> requests, String description) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " are invalid");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (ContactBatchVersionRequest request : requests) {
            if (request == null || request.getContactId() == null || request.getVersion() == null
                    || request.getVersion() < 0 || !ids.add(request.getContactId())) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        description + " are invalid");
            }
            id(request.getContactId());
        }
        return requests.stream().sorted(Comparator.comparing(ContactBatchVersionRequest::getContactId))
                .toList();
    }

    private List<Contact> batchContacts(
            ContactAccess access, List<ContactBatchVersionRequest> targets) {
        return targets.stream().map(target -> requireVisibleForUpdate(access, id(target.getContactId())))
                .toList();
    }

    private Contact requireVisible(ContactAccess access, ContactId id) {
        return contactRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(ContactNotFoundException::new);
    }

    private Contact requireVisibleForUpdate(ContactAccess access, ContactId id) {
        return contactRepository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(ContactNotFoundException::new);
    }

    private void requireVisibleCustomer(CustomerAccess access, Long customerId) {
        if (access == null || customerId == null || customerRepository.findVisibleById(
                access.tenantId(), new CustomerId(customerId), access.visibility()).isEmpty()) {
            throw new ContactNotFoundException();
        }
    }

    private ContactId id(Long value) {
        try {
            return new ContactId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact ID must be positive");
        }
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    @FunctionalInterface
    private interface ContactMutation {
        void apply(Contact contact, ContactAccess access, List<FieldDefinition> fields,
                   Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> fieldAccess);
    }
}
