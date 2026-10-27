package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerBatchTagRequest;
import io.github.archetom.crm.api.dto.request.CustomerCollaboratorsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerParentUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStarUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerStateUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerTagsUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerVersionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.CustomerStructureLock;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.service.CustomerCommandService;
import io.github.archetom.crm.application.service.CustomerCreationCoordinator;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.entity.CustomerCollaborator;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.exception.CustomerNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerDealStatus;
import io.github.archetom.crm.domain.model.CustomerHierarchyStats;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.CustomerStarRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerCommandServiceImpl
        implements CustomerCommandService, CustomerCreationCoordinator {

    private static final int MAX_HIERARCHY_NODES = CustomerHierarchyStats.MAX_TREE_NODES;
    private static final int MAX_HIERARCHY_DEPTH = CustomerHierarchyStats.MAX_DEPTH;
    private static final int HIERARCHY_NODE_PROBE = MAX_HIERARCHY_NODES + 1;
    private static final int HIERARCHY_DEPTH_PROBE = MAX_HIERARCHY_DEPTH + 1;

    private final CustomerRepository customerRepository;
    private final CustomerStarRepository starRepository;
    private final CustomerAccessResolver accessResolver;
    private final CustomerApplicationSupport support;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final CustomerStructureLock structureLock;
    private final CustomizationObjectLock objectLock;
    private final CommandServiceTemplate commandTemplate;

    @Override
    public Result<CustomerVO> create(
            AuthenticatedCaller caller, CustomerCreateRequest request) {
        return commandTemplate.execute(
                UseCaseOperation.CUSTOMER_CREATE, createOperation(caller, request));
    }

    @Override
    public CustomerVO createInCurrentTransaction(
            AuthenticatedCaller caller, CustomerCreateRequest request) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Customer creation coordination requires an active transaction");
        }
        ServiceOperation<CustomerVO> operation = createOperation(caller, request);
        operation.validate();
        operation.prepare();
        CustomerVO result = operation.execute();
        operation.onSuccess(result);
        return result;
    }

    private ServiceOperation<CustomerVO> createOperation(
            AuthenticatedCaller caller, CustomerCreateRequest request) {
        return new ServiceOperation<>() {
            private CustomerAccess writeAccess;
            private CustomerAccess assignAccess;
            private MemberId ownerMemberId;

            @Override
            public void validate() {
                writeAccess = accessResolver.access(
                        caller, AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
                requireRequest(request, "Customer create request");
                ownerMemberId = new MemberId(request.getOwnerActorId() == null
                        ? caller.actorId() : request.getOwnerActorId());
                if (!ownerMemberId.sameValueAs(writeAccess.memberId())
                        || request.getCollaborators() != null && !request.getCollaborators().isEmpty()) {
                    assignAccess = accessResolver.access(
                            caller, AuthorityCatalog.CUSTOMERS_ASSIGN, CustomerAccessMode.OWNER_SCOPE);
                }
            }

            @Override
            public CustomerVO execute() {
                objectLock.acquire(writeAccess.tenantId(), BusinessObjectType.CUSTOMER);
                List<FieldDefinition> fields = support.fields(writeAccess.tenantId());
                Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                var fieldAccess = support.accesses(writeAccess.tenantId(), writeAccess.memberId());
                requireCreateFieldAccess(request, fieldAccess);
                OrganizationMembership owner = support.requireActiveMember(
                        writeAccess.tenantId(), ownerMemberId);
                if (assignAccess != null) {
                    requireAssignableOwner(owner, assignAccess.visibility());
                }
                List<CustomerAddress> addresses = support.addresses(request.getAddresses());
                List<CustomerFieldValue> customFields = support.customFields(
                        request.getCustomFields(), List.of(), true, fieldsByCode, fieldAccess);
                Customer customer = Customer.create(writeAccess.tenantId(), ownerMemberId,
                        request.getName(), request.getPhoneNumber(), request.getTelephone(),
                        request.getEmail(), request.getWebsite(), request.getDescription(),
                        addresses, customFields);
                customer.replaceTags(request.getTags());
                List<CustomerCollaborator> collaborators = support.collaborators(
                        request.getCollaborators());
                support.requireActiveMembers(writeAccess.tenantId(), collaborators.stream()
                        .map(CustomerCollaborator::memberId).toList());
                customer.replaceCollaborators(collaborators);
                if (request.getParentCustomerId() != null) {
                    structureLock.acquire(writeAccess.tenantId());
                    CustomerId parentId = new CustomerId(request.getParentCustomerId());
                    requireVisible(writeAccess, parentId);
                    requireCreateHierarchyCapacity(writeAccess, parentId);
                    customer.changeParent(parentId);
                }
                Customer saved = customerRepository.save(
                        writeAccess.tenantId(), customer, fields);
                fileFieldBindingService.consumeCreateDraft(writeAccess.tenantId(), writeAccess.memberId(),
                        BusinessObjectType.CUSTOMER, request.getFileFieldDraftId(),
                        new AttachmentTarget(AttachmentTargetType.CUSTOMER, saved.getId().getValue()),
                        request.getFileFields(), fieldsByCode, fieldAccess);
                return support.toVO(saved, false, fieldAccess,
                        visibleParentIds(List.of(saved), writeAccess), assignAccess != null);
            }
        };
    }

    @Override
    public Result<CustomerVO> update(
            AuthenticatedCaller caller, Long customerId, CustomerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_UPDATE, new ServiceOperation<>() {
            private CustomerAccess access;
            private CustomerId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
                id = id(customerId);
                requireRequest(request, "Customer update request");
            }

            @Override
            public CustomerVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                Customer customer = requireVisible(access, id);
                support.requireVersion(customer, request.getVersion());
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Map<FieldCode, FieldDefinition> fieldsByCode = support.fieldsByCode(fields);
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                String name = patched(request.getName(), customer.getName(), fieldAccess,
                        CustomerFieldCatalog.NAME);
                String phone = patched(request.getPhoneNumber(), customer.getPhoneNumber(), fieldAccess,
                        CustomerFieldCatalog.PHONE_NUMBER);
                String telephone = patched(request.getTelephone(), customer.getTelephone(), fieldAccess,
                        CustomerFieldCatalog.TELEPHONE);
                String email = patched(request.getEmail(), customer.getEmail(), fieldAccess,
                        CustomerFieldCatalog.EMAIL);
                String website = patched(request.getWebsite(), customer.getWebsite(), fieldAccess,
                        CustomerFieldCatalog.WEBSITE);
                String description = patched(request.getDescription(), customer.getDescription(), fieldAccess,
                        CustomerFieldCatalog.DESCRIPTION);
                List<CustomerAddress> addresses = customer.getAddresses();
                if (request.getAddresses() != null) {
                    support.requireWrite(fieldAccess, CustomerFieldCatalog.ADDRESSES);
                    addresses = support.addresses(request.getAddresses());
                }
                List<CustomerFieldValue> customFields = support.customFields(
                        request.getCustomFields(), customer.getCustomFieldValues(), false,
                        fieldsByCode, fieldAccess);
                customer.replaceDetails(name, phone, telephone, email, website, description,
                        addresses, customFields);
                Customer saved = customerRepository.save(access.tenantId(), customer, fields);
                fileFieldBindingService.replaceExisting(access.tenantId(), access.memberId(),
                        BusinessObjectType.CUSTOMER,
                        new AttachmentTarget(AttachmentTargetType.CUSTOMER, saved.getId().getValue()),
                        request.getFileFields(), fieldsByCode, fieldAccess);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access));
            }
        });
    }

    @Override
    public Result<Void> delete(
            AuthenticatedCaller caller, Long customerId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_DELETE, new ServiceOperation<>() {
            private CustomerAccess access;
            private CustomerId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CUSTOMERS_DELETE, CustomerAccessMode.OWNER_SCOPE);
                id = id(customerId);
            }

            @Override
            public Void execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                structureLock.acquire(access.tenantId());
                Customer customer = requireVisibleForUpdate(access, id);
                support.requireVersion(customer, version);
                if (customerRepository.existsActiveChild(access.tenantId(), id)) {
                    throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Customer with child customers cannot be deleted");
                }
                List<FieldDefinition> fields = support.fields(access.tenantId());
                customer.delete();
                attachmentParentLifecycle.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.CUSTOMER, id.getValue()));
                fileFieldBindingService.withdrawAll(access.tenantId(),
                        new AttachmentTarget(AttachmentTargetType.CUSTOMER, id.getValue()));
                customerRepository.save(access.tenantId(), customer, fields);
                return null;
            }
        });
    }

    @Override
    public Result<CustomerVO> updateState(
            AuthenticatedCaller caller, Long customerId, CustomerStateUpdateRequest request) {
        return mutate(caller, customerId, request, UseCaseOperation.CUSTOMER_STATE_UPDATE,
                (customer, access, fields, fieldAccess) -> {
                    support.requireWrite(fieldAccess, CustomerFieldCatalog.LOCKED);
                    support.requireWrite(fieldAccess, CustomerFieldCatalog.DEAL_STATUS);
                    customer.changeState(Boolean.TRUE.equals(request.getLocked()),
                            support.parse(CustomerDealStatus.class, request.getDealStatus(),
                                    "Customer deal status"));
                });
    }

    @Override
    public Result<CustomerVO> updateOwner(
            AuthenticatedCaller caller, Long customerId, CustomerOwnerUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_OWNER_UPDATE, new ServiceOperation<>() {
            private CustomerAccess access;
            private CustomerId id;

            @Override
            public void validate() {
                access = accessResolver.access(
                        caller, AuthorityCatalog.CUSTOMERS_ASSIGN, CustomerAccessMode.OWNER_SCOPE);
                id = id(customerId);
                requireRequest(request, "Customer owner request");
            }

            @Override
            public CustomerVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                Customer customer = requireVisible(access, id);
                support.requireVersion(customer, request.getVersion());
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                support.requireWrite(fieldAccess, CustomerFieldCatalog.OWNER_ACTOR);
                MemberId ownerId = new MemberId(request.getOwnerActorId());
                OrganizationMembership owner = support.requireActiveMember(access.tenantId(), ownerId);
                requireAssignableOwner(owner, access.visibility());
                customer.changeOwner(ownerId);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                Customer saved = customerRepository.save(access.tenantId(), customer, fields);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access), true);
            }
        });
    }

    @Override
    public Result<CustomerVO> replaceTags(
            AuthenticatedCaller caller, Long customerId, CustomerTagsUpdateRequest request) {
        return mutate(caller, customerId, request, UseCaseOperation.CUSTOMER_TAG_REPLACE,
                (customer, access, fields, fieldAccess) -> {
                    support.requireWrite(fieldAccess, CustomerFieldCatalog.TAGS);
                    customer.replaceTags(request.getTags());
                });
    }

    @Override
    public Result<List<CustomerVO>> changeTags(
            AuthenticatedCaller caller, CustomerBatchTagRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_TAG_BATCH_CHANGE,
                new ServiceOperation<>() {
                    private CustomerAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_WRITE,
                                CustomerAccessMode.WRITE);
                        requireRequest(request, "Customer batch tag request");
                        if (request.getCustomers() == null || request.getCustomers().isEmpty()) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Customer batch must not be empty");
                        }
                    }

                    @Override
                    public List<CustomerVO> execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, CustomerFieldCatalog.TAGS);
                        Map<CustomerId, Long> versions = versions(request.getCustomers());
                        List<Customer> customers = customerRepository.findVisibleByIds(
                                access.tenantId(), versions.keySet(), access.visibility(), 100);
                        if (customers.size() != versions.size()) {
                            throw new CustomerNotFoundException();
                        }
                        Set<CustomerId> visibleParents = visibleParentIds(customers, access);
                        Set<CustomerId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), versions.keySet());
                        List<CustomerVO> result = new ArrayList<>();
                        for (Customer customer : customers) {
                            support.requireVersion(customer, versions.get(customer.getId()));
                            customer.changeTags(request.getAddedTags(), request.getRemovedTags());
                            Customer saved = customerRepository.save(
                                    access.tenantId(), customer, fields);
                            result.add(support.toVO(saved, starred.contains(saved.getId()),
                                    fieldAccess, visibleParents));
                        }
                        return List.copyOf(result);
                    }
                });
    }

    @Override
    public Result<CustomerVO> replaceCollaborators(
            AuthenticatedCaller caller, Long customerId,
            CustomerCollaboratorsUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_COLLABORATOR_REPLACE,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_ASSIGN,
                                CustomerAccessMode.OWNER_SCOPE);
                        id = id(customerId);
                        requireRequest(request, "Customer collaborator request");
                    }

                    @Override
                    public CustomerVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                        Customer customer = requireVisible(access, id);
                        support.requireVersion(customer, request.getVersion());
                        List<CustomerCollaborator> collaborators = support.collaborators(
                                request.getCollaborators());
                        support.requireActiveMembers(access.tenantId(), collaborators.stream()
                                .map(CustomerCollaborator::memberId).toList());
                        customer.replaceCollaborators(collaborators);
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        Customer saved = customerRepository.save(access.tenantId(), customer, fields);
                        return support.toVO(saved, starRepository.isStarred(
                                        access.tenantId(), access.memberId(), id), fieldAccess,
                                visibleParentIds(List.of(saved), access), true);
                    }
                });
    }

    @Override
    public Result<Void> exitCollaborator(
            AuthenticatedCaller caller, Long customerId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_COLLABORATOR_EXIT,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                        id = id(customerId);
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                        Customer customer = requireVisible(access, id);
                        support.requireVersion(customer, version);
                        if (!customer.exitCollaborator(access.memberId())) {
                            throw new CustomerNotFoundException();
                        }
                        customerRepository.save(access.tenantId(), customer,
                                support.fields(access.tenantId()));
                        return null;
                    }
                });
    }

    @Override
    public Result<CustomerVO> updateParent(
            AuthenticatedCaller caller, Long customerId, CustomerParentUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_PARENT_UPDATE,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_WRITE,
                                CustomerAccessMode.WRITE);
                        id = id(customerId);
                        requireRequest(request, "Customer parent request");
                    }

                    @Override
                    public CustomerVO execute() {
                        objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                        structureLock.acquire(access.tenantId());
                        Customer customer = requireVisible(access, id);
                        support.requireVersion(customer, request.getVersion());
                        var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireWrite(fieldAccess, CustomerFieldCatalog.PARENT_CUSTOMER);
                        CustomerId parentId = request.getParentCustomerId() == null
                                ? null : new CustomerId(request.getParentCustomerId());
                        if (parentId != null) {
                            requireVisible(access, parentId);
                            CustomerHierarchyStats sourceStats = hierarchyStats(access, id);
                            CustomerHierarchyStats parentStats = hierarchyStats(access, parentId);
                            requireQueryableHierarchy(sourceStats);
                            requireQueryableHierarchy(parentStats);
                            List<Customer> descendants = customerRepository.findDescendants(
                                    access.tenantId(), id, HIERARCHY_NODE_PROBE);
                            if (descendants.stream().anyMatch(value -> value.getId().sameValueAs(parentId))) {
                                throw support.error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Customer hierarchy cannot contain a cycle");
                            }
                            requireMoveHierarchyCapacity(sourceStats, parentStats);
                        }
                        customer.changeParent(parentId);
                        Customer saved = customerRepository.save(
                                access.tenantId(), customer, support.fields(access.tenantId()));
                        return support.toVO(saved, starRepository.isStarred(
                                        access.tenantId(), access.memberId(), id), fieldAccess,
                                visibleParentIds(List.of(saved), access));
                    }
                });
    }

    @Override
    public Result<Void> setStarred(
            AuthenticatedCaller caller, Long customerId, CustomerStarUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_STAR_UPDATE,
                new ServiceOperation<>() {
                    private CustomerAccess access;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_READ,
                                CustomerAccessMode.READ);
                        id = id(customerId);
                        requireRequest(request, "Customer star request");
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

    private Result<CustomerVO> mutate(
            AuthenticatedCaller caller, Long customerId, Object request,
            UseCaseOperation operation, CustomerMutation mutation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private CustomerAccess access;
            private CustomerId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.CUSTOMERS_WRITE,
                        CustomerAccessMode.WRITE);
                id = id(customerId);
                requireRequest(request, "Customer mutation request");
            }

            @Override
            public CustomerVO execute() {
                objectLock.acquire(access.tenantId(), BusinessObjectType.CUSTOMER);
                Customer customer = requireVisible(access, id);
                Long version = request instanceof CustomerStateUpdateRequest value ? value.getVersion()
                        : ((CustomerTagsUpdateRequest) request).getVersion();
                support.requireVersion(customer, version);
                List<FieldDefinition> fields = support.fields(access.tenantId());
                var fieldAccess = support.accesses(access.tenantId(), access.memberId());
                mutation.apply(customer, access, fields, fieldAccess);
                Customer saved = customerRepository.save(access.tenantId(), customer, fields);
                return support.toVO(saved, starRepository.isStarred(
                                access.tenantId(), access.memberId(), id), fieldAccess,
                        visibleParentIds(List.of(saved), access));
            }
        });
    }

    private void requireCreateHierarchyCapacity(
            CustomerAccess access, CustomerId parentId) {
        CustomerHierarchyStats parentStats = hierarchyStats(access, parentId);
        requireQueryableHierarchy(parentStats);
        if (parentStats.treeNodeCount() >= MAX_HIERARCHY_NODES
                || parentStats.depth() >= MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
    }

    private CustomerHierarchyStats hierarchyStats(
            CustomerAccess access, CustomerId customerId) {
        return customerRepository.hierarchyStats(
                        access.tenantId(), customerId,
                        HIERARCHY_NODE_PROBE, HIERARCHY_DEPTH_PROBE)
                .orElseThrow(CustomerNotFoundException::new);
    }

    private void requireQueryableHierarchy(CustomerHierarchyStats stats) {
        if (stats.depth() > MAX_HIERARCHY_DEPTH
                || stats.subtreeNodeCount() > MAX_HIERARCHY_NODES
                || stats.subtreeHeight() > MAX_HIERARCHY_DEPTH
                || stats.treeNodeCount() > MAX_HIERARCHY_NODES
                || stats.treeHeight() > MAX_HIERARCHY_DEPTH) {
            throw hierarchyLimitExceeded();
        }
    }

    private void requireMoveHierarchyCapacity(
            CustomerHierarchyStats source, CustomerHierarchyStats parent) {
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
                "Customer hierarchy exceeds the supported limit");
    }

    private Set<CustomerId> visibleParentIds(
            Collection<Customer> customers, CustomerAccess access) {
        Set<CustomerId> parentIds = new LinkedHashSet<>();
        if (customers != null) {
            customers.stream().map(Customer::getParentCustomerId)
                    .filter(java.util.Objects::nonNull).forEach(parentIds::add);
        }
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return customerRepository.findVisibleByIds(
                        access.tenantId(), parentIds, access.visibility(), parentIds.size())
                .stream().map(Customer::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void requireCreateFieldAccess(
            CustomerCreateRequest request,
            Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> accesses) {
        support.requireWrite(accesses, CustomerFieldCatalog.NAME);
        support.requireWrite(accesses, CustomerFieldCatalog.OWNER_ACTOR);
        if (request.getPhoneNumber() != null) {
            support.requireWrite(accesses, CustomerFieldCatalog.PHONE_NUMBER);
        }
        if (request.getTelephone() != null) {
            support.requireWrite(accesses, CustomerFieldCatalog.TELEPHONE);
        }
        if (request.getEmail() != null) {
            support.requireWrite(accesses, CustomerFieldCatalog.EMAIL);
        }
        if (request.getWebsite() != null) {
            support.requireWrite(accesses, CustomerFieldCatalog.WEBSITE);
        }
        if (request.getDescription() != null) {
            support.requireWrite(accesses, CustomerFieldCatalog.DESCRIPTION);
        }
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            support.requireWrite(accesses, CustomerFieldCatalog.TAGS);
        }
        if (request.getAddresses() != null && !request.getAddresses().isEmpty()) {
            support.requireWrite(accesses, CustomerFieldCatalog.ADDRESSES);
        }
        if (request.getParentCustomerId() != null) {
            support.requireWrite(accesses, CustomerFieldCatalog.PARENT_CUSTOMER);
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

    private void requireAssignableOwner(
            OrganizationMembership owner, CustomerVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        if (visibility.dataScope() == DataScope.SELF) {
            if (!owner.getMemberId().sameValueAs(visibility.callerMemberId())) {
                throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                        "Customer owner is outside the assignment scope");
            }
            return;
        }
        if (owner.getDepartmentId() == null
                || !visibility.ownerDepartmentIds().contains(owner.getDepartmentId())) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Customer owner is outside the assignment scope");
        }
    }

    private Map<CustomerId, Long> versions(List<CustomerVersionRequest> requests) {
        Map<CustomerId, Long> values = new LinkedHashMap<>();
        for (CustomerVersionRequest request : requests) {
            if (request == null || request.getVersion() == null
                    || values.putIfAbsent(id(request.getCustomerId()), request.getVersion()) != null) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer batch entries must be unique");
            }
        }
        return values;
    }

    private Customer requireVisible(CustomerAccess access, CustomerId id) {
        return customerRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(CustomerNotFoundException::new);
    }

    private Customer requireVisibleForUpdate(CustomerAccess access, CustomerId id) {
        return customerRepository.findVisibleByIdForUpdate(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(CustomerNotFoundException::new);
    }

    private CustomerId id(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    @FunctionalInterface
    private interface CustomerMutation {
        void apply(Customer customer, CustomerAccess access, List<FieldDefinition> fields,
                   Map<FieldCode, io.github.archetom.crm.domain.model.EffectiveFieldAccess> fieldAccess);
    }
}
