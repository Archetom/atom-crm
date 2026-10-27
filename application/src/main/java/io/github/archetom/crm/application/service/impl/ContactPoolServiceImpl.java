package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactBatchRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolContactQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolRuleRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.ContactPoolUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContactVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.port.out.ContactPoolCatalogQuery;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.ContactPoolService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ContactPoolRuleVO;
import io.github.archetom.crm.application.vo.ContactPoolVO;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolMembership;
import io.github.archetom.crm.domain.entity.ContactPoolRule;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactPoolRuleType;
import io.github.archetom.crm.domain.model.ContactPoolStatus;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactPoolRepository;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactPoolServiceImpl implements ContactPoolService {

    private final ContactPoolRepository poolRepository;
    private final ContactPoolCatalogQuery poolCatalogQuery;
    private final ContactRepository contactRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final SavedViewRepository savedViewRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final ContactAccessResolver contactAccessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final CustomerRepository customerRepository;
    private final OrganizationAccessGuard accessGuard;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final ContactApplicationSupport contactSupport;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final CustomizationObjectLock objectLock;
    private final AutomationScheduleManager automationSchedules;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ContactPoolVO>> query(
            AuthenticatedCaller caller, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_POOL_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private boolean tenantOwner;
            private int resolvedPage;
            private int resolvedSize;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE);
                memberId = new MemberId(caller.actorId());
                tenantOwner = isTenantOwner(tenantId, memberId);
                resolvedPage = page(page);
                resolvedSize = size(size);
            }

            @Override
            public Pager<ContactPoolVO> execute() {
                PageResult<ContactPool> result = poolRepository.findAll(
                        tenantId, resolvedPage, resolvedSize);
                Map<ContactPoolId, Long> counts = contactCounts(tenantId, result.items());
                Pager<ContactPoolVO> pager = pager(result);
                pager.setObjectList(result.items().stream()
                        .map(pool -> toVO(memberId, pool,
                                counts.getOrDefault(pool.getId(), 0L), tenantOwner)).toList());
                return pager;
            }
        });
    }

    @Override
    public Result<List<ContactPoolVO>> catalog(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_POOL_CATALOG, new ServiceOperation<>() {
            private TenantId tenantId;
            private OrganizationMembership membership;
            private boolean tenantOwner;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.CONTACT_POOLS_READ);
                membership = activeMembership(tenantId, new MemberId(caller.actorId()));
                tenantOwner = isTenantOwner(tenantId, membership.getMemberId());
            }

            @Override
            public List<ContactPoolVO> execute() {
                List<ContactPoolCatalogQuery.Entry> pools = poolCatalogQuery.findAccessible(tenantId,
                        membership.getMemberId(), membership.getDepartmentId(), tenantOwner);
                Map<ContactPoolId, Long> counts = poolRepository.countContacts(tenantId,
                        pools.stream().map(ContactPoolCatalogQuery.Entry::id).toList());
                return pools.stream().map(pool -> toCatalogVO(pool,
                        counts.getOrDefault(pool.id(), 0L), tenantOwner)).toList();
            }
        });
    }

    @Override
    public Result<ContactPoolVO> get(AuthenticatedCaller caller, Long poolId) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_POOL_GET, new ServiceOperation<>() {
            private PoolContext context;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE, poolId);
            }

            @Override
            public ContactPoolVO execute() {
                ContactPool pool = requirePool(context.tenantId(), context.poolId());
                return toVO(context.memberId(), pool,
                        poolRepository.countContacts(context.tenantId(), pool.getId()),
                        context.tenantOwner());
            }
        });
    }

    @Override
    public Result<ContactPoolVO> create(
            AuthenticatedCaller caller, ContactPoolCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId callerId;
            private boolean tenantOwner;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE);
                callerId = new MemberId(caller.actorId());
                tenantOwner = isTenantOwner(tenantId, callerId);
                require(request, "Contact pool create request");
            }

            @Override
            public ContactPoolVO execute() {
                boolean automationConfiguration = Boolean.TRUE.equals(request.getAutomaticReleaseEnabled());
                if (automationConfiguration) {
                    objectLock.acquire(tenantId, BusinessObjectType.CONTACT);
                    automationSchedules.preparePoolConfiguration(tenantId,
                            AutomationKind.CONTACT_POOL_RELEASE, true);
                }
                ContactPool pool = ContactPool.create(tenantId, request.getName(),
                        members(request.getAdministratorActorIds()), members(request.getEligibleActorIds()),
                        departments(request.getEligibleDepartmentIds()), value(request.getDailyClaimLimit()),
                        value(request.getPreviousOwnerCooldownDays()),
                        Boolean.TRUE.equals(request.getAutomaticReleaseEnabled()), rules(request.getRules()));
                validateReferences(tenantId, pool);
                if (poolRepository.existsByName(tenantId, pool.getName(), null)) {
                    throw error(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Contact pool name already exists");
                }
                ContactPool saved = poolRepository.save(tenantId, pool);
                if (automationConfiguration) synchronizeAutomation(tenantId);
                return toVO(callerId, saved, 0L, tenantOwner);
            }
        });
    }

    @Override
    public Result<ContactPoolVO> update(
            AuthenticatedCaller caller, Long poolId, ContactPoolUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_UPDATE, new ServiceOperation<>() {
            private PoolContext context;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE, poolId);
                require(request, "Contact pool update request");
            }

            @Override
            public ContactPoolVO execute() {
                ContactPool current = requirePool(context.tenantId(), context.poolId());
                boolean automationConfiguration = current.isActive()
                        && (current.isAutomaticReleaseEnabled()
                        || Boolean.TRUE.equals(request.getAutomaticReleaseEnabled()));
                if (automationConfiguration) {
                    objectLock.acquire(context.tenantId(), BusinessObjectType.CONTACT);
                    automationSchedules.preparePoolConfiguration(context.tenantId(),
                            AutomationKind.CONTACT_POOL_RELEASE, true);
                }
                ContactPool pool = requireLockedPool(context);
                requireAdministrator(context, pool);
                requireVersion(pool, request.getVersion());
                pool.changeConfiguration(request.getName(), members(request.getAdministratorActorIds()),
                        members(request.getEligibleActorIds()), departments(request.getEligibleDepartmentIds()),
                        value(request.getDailyClaimLimit()), value(request.getPreviousOwnerCooldownDays()),
                        Boolean.TRUE.equals(request.getAutomaticReleaseEnabled()), rules(request.getRules()));
                validateReferences(context.tenantId(), pool);
                if (poolRepository.existsByName(context.tenantId(), pool.getName(), pool.getId())) {
                    throw error(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Contact pool name already exists");
                }
                ContactPool saved = poolRepository.save(context.tenantId(), pool);
                if (automationConfiguration) synchronizeAutomation(context.tenantId());
                return toVO(context.memberId(), saved,
                        poolRepository.countContacts(context.tenantId(), saved.getId()),
                        context.tenantOwner());
            }
        });
    }

    @Override
    public Result<ContactPoolVO> updateStatus(
            AuthenticatedCaller caller, Long poolId, ContactPoolStatusUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private PoolContext context;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE, poolId);
                        require(request, "Contact pool status request");
                    }

                    @Override
                    public ContactPoolVO execute() {
                        ContactPool current = requirePool(context.tenantId(), context.poolId());
                        boolean automationConfiguration = current.isAutomaticReleaseEnabled()
                                && (current.isActive() || Boolean.TRUE.equals(request.getActive()));
                        if (automationConfiguration) {
                            objectLock.acquire(context.tenantId(), BusinessObjectType.CONTACT);
                            automationSchedules.preparePoolConfiguration(context.tenantId(),
                                    AutomationKind.CONTACT_POOL_RELEASE, current.isActive()
                                            || Boolean.TRUE.equals(request.getActive()));
                        }
                        ContactPool pool = requireLockedPool(context);
                        requireAdministrator(context, pool);
                        requireVersion(pool, request.getVersion());
                        if (!Boolean.TRUE.equals(request.getActive())
                                && poolRepository.countContacts(context.tenantId(), pool.getId()) > 0) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A contact pool with contacts cannot be deactivated");
                        }
                        pool.changeStatus(Boolean.TRUE.equals(request.getActive())
                                ? ContactPoolStatus.ACTIVE : ContactPoolStatus.INACTIVE);
                        ContactPool saved = poolRepository.save(context.tenantId(), pool);
                        if (automationConfiguration) synchronizeAutomation(context.tenantId());
                        return toVO(context.memberId(), saved,
                                poolRepository.countContacts(context.tenantId(), saved.getId()),
                                context.tenantOwner());
                    }
                });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_DELETE, new ServiceOperation<>() {
            private PoolContext context;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE, poolId);
            }

            @Override
            public Void execute() {
                ContactPool current = requirePool(context.tenantId(), context.poolId());
                boolean automationConfiguration = current.isActive() && current.isAutomaticReleaseEnabled();
                if (automationConfiguration) {
                    objectLock.acquire(context.tenantId(), BusinessObjectType.CONTACT);
                    automationSchedules.preparePoolConfiguration(context.tenantId(),
                            AutomationKind.CONTACT_POOL_RELEASE, false);
                }
                ContactPool pool = requireLockedPool(context);
                requireAdministrator(context, pool);
                requireVersion(pool, version);
                if (poolRepository.countContacts(context.tenantId(), pool.getId()) > 0) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "A contact pool with contacts cannot be deleted");
                }
                pool.delete();
                poolRepository.save(context.tenantId(), pool);
                if (automationConfiguration) synchronizeAutomation(context.tenantId());
                return null;
            }
        });
    }

    @Override
    public Result<Void> transfer(
            AuthenticatedCaller caller, Long poolId, ContactPoolTransferRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_TRANSFER, new ServiceOperation<>() {
            private PoolContext context;
            private ContactPoolId targetId;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CONTACT_POOLS_MANAGE, poolId);
                require(request, "Contact pool transfer request");
                targetId = poolId(request.getTargetPoolId());
                if (context.poolId().sameValueAs(targetId)) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Target contact pool must be different");
                }
            }

            @Override
            public Void execute() {
                List<ContactPoolId> ordered = List.of(context.poolId(), targetId).stream()
                        .sorted(Comparator.comparing(ContactPoolId::getValue)).toList();
                Map<ContactPoolId, ContactPool> locked = new LinkedHashMap<>();
                ordered.forEach(id -> locked.put(id, poolRepository.findByIdForUpdate(context.tenantId(), id)
                        .orElseThrow(() -> notFound("Contact pool was not found"))));
                ContactPool source = locked.get(context.poolId());
                ContactPool target = locked.get(targetId);
                requireAdministrator(context, source);
                if (!target.isActive()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Target contact pool is inactive");
                }
                poolRepository.transferMemberships(context.tenantId(), source.getId(), target.getId());
                return null;
            }
        });
    }

    @Override
    public Result<Pager<ContactVO>> contacts(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_POOL_CONTACT_QUERY,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private int page;
                    private int size;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CONTACT_POOLS_READ, poolId);
                        require(request, "Contact pool query request");
                        page = page(request.getPage());
                        size = size(request.getSize());
                    }

                    @Override
                    public Pager<ContactVO> execute() {
                        ContactPool pool = requirePool(context.tenantId(), context.poolId());
                        requireAccessible(context, pool);
                        List<FieldDefinition> fields = contactSupport.fields(context.tenantId()).stream()
                                .filter(FieldDefinition::isActive).toList();
                        Map<FieldCode, EffectiveFieldAccess> accesses = contactSupport.accesses(
                                context.tenantId(), context.memberId());
                        if (request.getName() != null && !request.getName().isBlank()) {
                            requireReadable(accesses, new FieldCode("contact_name"));
                        }
                        List<FilterCondition> conditions = conditions(
                                context, request.getSavedViewId(), fields, accesses);
                        PageResult<Contact> result = contactRepository.findPoolContacts(
                                context.tenantId(), context.poolId(), request.getName(), conditions, page, size);
                        Set<ContactId> visibleRelations = visiblePoolRelations(context, result.items());
                        Set<CustomerId> visibleCustomers = visibleCustomers(context, result.items());
                        Pager<ContactVO> pager = pager(result);
                        pager.setObjectList(result.items().stream()
                                .map(contact -> contactSupport.toVO(
                                        contact, false, accesses, visibleRelations,
                                        visibleCustomers, false))
                                .toList());
                        return pager;
                    }
                });
    }

    @Override
    public Result<ContactVO> contact(
            AuthenticatedCaller caller, Long poolId, Long contactId) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_POOL_CONTACT_GET,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private ContactId id;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CONTACT_POOLS_READ, poolId);
                        id = contactId(contactId);
                    }

                    @Override
                    public ContactVO execute() {
                        ContactPool pool = requirePool(context.tenantId(), context.poolId());
                        requireAccessible(context, pool);
                        Contact contact = requirePoolContact(context.tenantId(), context.poolId(), id);
                        return contactSupport.toVO(contact, false,
                                contactSupport.accesses(context.tenantId(), context.memberId()),
                                visiblePoolRelations(context, List.of(contact)),
                                visibleCustomers(context, List.of(contact)), false);
                    }
                });
    }

    @Override
    public Result<List<ContactVO>> release(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactBatchRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_CONTACT_RELEASE,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private ContactAccessResolver.ContactAccess contactAccess;
                    private Map<ContactId, Long> versions;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CONTACT_POOLS_RELEASE, poolId);
                        contactAccess = contactAccessResolver.access(caller,
                                AuthorityCatalog.CONTACTS_WRITE, ContactAccessMode.WRITE);
                        versions = versions(request == null ? null : request.getContacts());
                    }

                    @Override
                    public List<ContactVO> execute() {
                        objectLock.acquire(context.tenantId(), BusinessObjectType.CONTACT);
                        ContactPool pool = requireLockedPool(context);
                        requireAccessible(context, pool);
                        if (!pool.isActive()) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Contact pool is inactive");
                        }
                        List<FieldDefinition> fields = contactSupport.fields(context.tenantId());
                        List<Contact> changed = new java.util.ArrayList<>();
                        for (Map.Entry<ContactId, Long> entry : versions.entrySet()) {
                            Contact contact = contactRepository.findVisibleById(
                                            context.tenantId(), entry.getKey(), contactAccess.visibility())
                                    .orElseThrow(() -> notFound("Contact was not found"));
                            contactSupport.requireVersion(contact, entry.getValue());
                            MemberId previousOwner = contact.getOwnerMemberId();
                            contact.enterPool();
                            contactRepository.save(context.tenantId(), contact, fields);
                            poolRepository.addMembership(new ContactPoolMembership(
                                    context.tenantId(), pool.getId(), contact.getId(), previousOwner,
                                    LocalDateTime.now()));
                            changed.add(contact);
                        }
                        return contactVOs(context, changed);
                    }
                });
    }

    @Override
    public Result<List<ContactVO>> claim(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactBatchRequest request) {
        return leavePool(caller, poolId, request == null ? null : request.getContacts(),
                null, false, UseCaseOperation.CONTACT_POOL_CONTACT_CLAIM);
    }

    @Override
    public Result<List<ContactVO>> assign(
            AuthenticatedCaller caller, Long poolId, ContactPoolAssignRequest request) {
        if (request == null) {
            return leavePool(caller, poolId, null, null, true,
                    UseCaseOperation.CONTACT_POOL_CONTACT_ASSIGN);
        }
        MemberId target;
        try {
            target = new MemberId(request.getOwnerActorId());
        } catch (RuntimeException exception) {
            return leavePool(caller, poolId, null, null, true,
                    UseCaseOperation.CONTACT_POOL_CONTACT_ASSIGN);
        }
        return leavePool(caller, poolId, request.getContacts(), target, true,
                UseCaseOperation.CONTACT_POOL_CONTACT_ASSIGN);
    }

    @Override
    public Result<Void> deleteContacts(
            AuthenticatedCaller caller, Long poolId, ContactPoolContactBatchRequest request) {
        return commandTemplate.execute(UseCaseOperation.CONTACT_POOL_CONTACT_DELETE,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private Map<ContactId, Long> versions;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CONTACT_POOLS_DELETE, poolId);
                        versions = versions(request == null ? null : request.getContacts());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(context.tenantId(), BusinessObjectType.CONTACT);
                        ContactPool pool = requireLockedPool(context);
                        requireAdministrator(context, pool);
                        List<FieldDefinition> fields = contactSupport.fields(context.tenantId());
                        for (Map.Entry<ContactId, Long> entry : versions.entrySet()) {
                            poolRepository.findMembershipForUpdate(
                                            context.tenantId(), pool.getId(), entry.getKey())
                                    .orElseThrow(() -> notFound("Contact was not found in the pool"));
                            Contact contact = requirePoolContactForUpdate(
                                    context.tenantId(), pool.getId(), entry.getKey());
                            contactSupport.requireVersion(contact, entry.getValue());
                            if (contactRepository.existsActiveChild(context.tenantId(), contact.getId())) {
                                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Contact with child contacts cannot be deleted");
                            }
                            contact.deleteFromPool();
                            attachmentParentLifecycle.withdrawAll(context.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.CONTACT,
                                            contact.getId().getValue()));
                            fileFieldBindingService.withdrawAll(context.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.CONTACT,
                                            contact.getId().getValue()));
                            contactRepository.save(context.tenantId(), contact, fields);
                            poolRepository.removeMembership(context.tenantId(), pool.getId(), contact.getId());
                        }
                        return null;
                    }
                });
    }

    private Result<List<ContactVO>> leavePool(
            AuthenticatedCaller caller, Long poolId, List<ContactVersionRequest> requested,
            MemberId requestedOwner, boolean assigned, UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private PoolContext context;
            private Map<ContactId, Long> versions;
            private MemberId owner;

            @Override
            public void validate() {
                context = context(caller, assigned ? AuthorityCatalog.CONTACT_POOLS_ASSIGN
                        : AuthorityCatalog.CONTACT_POOLS_CLAIM, poolId);
                versions = versions(requested);
                owner = assigned ? requestedOwner : context.memberId();
                if (owner == null) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contact pool owner is invalid");
                }
                activeMembership(context.tenantId(), owner);
            }

            @Override
            public List<ContactVO> execute() {
                objectLock.acquire(context.tenantId(), BusinessObjectType.CONTACT);
                ContactPool pool = requireLockedPool(context);
                if (!pool.isActive()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Contact pool is inactive");
                }
                if (assigned) requireAdministrator(context, pool);
                else requireAccessible(context, pool);
                LocalDateTime now = LocalDateTime.now();
                if (!assigned && pool.getDailyClaimLimit() > 0
                        && poolRepository.countClaims(context.tenantId(), pool.getId(), owner,
                        now.toLocalDate()) + versions.size() > pool.getDailyClaimLimit()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Contact pool daily claim limit was reached");
                }
                List<FieldDefinition> fields = contactSupport.fields(context.tenantId());
                List<Contact> changed = new java.util.ArrayList<>();
                for (Map.Entry<ContactId, Long> entry : versions.entrySet()) {
                    ContactPoolMembership membership = poolRepository.findMembershipForUpdate(
                                    context.tenantId(), pool.getId(), entry.getKey())
                            .orElseThrow(() -> notFound("Contact was not found in the pool"));
                    Contact contact = requirePoolContact(context.tenantId(), pool.getId(), entry.getKey());
                    contactSupport.requireVersion(contact, entry.getValue());
                    if (!assigned && membership.previousOwnerMemberId() != null
                            && membership.previousOwnerMemberId().sameValueAs(owner)
                            && membership.enteredTime().plusDays(pool.getPreviousOwnerCooldownDays())
                            .isAfter(now)) {
                        throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Previous owner cooldown is still active");
                    }
                    contact.leavePool(owner);
                    attachmentParentLifecycle.resumeAll(context.tenantId(),
                            new AttachmentTarget(AttachmentTargetType.CONTACT,
                                    contact.getId().getValue()));
                    contactRepository.save(context.tenantId(), contact, fields);
                    poolRepository.removeMembership(context.tenantId(), pool.getId(), contact.getId());
                    poolRepository.recordClaim(context.tenantId(), pool.getId(), contact.getId(),
                            owner, now, assigned);
                    changed.add(contact);
                }
                return contactVOs(context, changed);
            }
        });
    }

    private List<ContactVO> contactVOs(PoolContext context, Collection<Contact> contacts) {
        Map<FieldCode, EffectiveFieldAccess> accesses = contactSupport.accesses(
                context.tenantId(), context.memberId());
        Set<ContactId> visibleRelations = visiblePoolRelations(context, contacts);
        Set<CustomerId> visibleCustomers = visibleCustomers(context, contacts);
        return contacts.stream().map(contact -> contactSupport.toVO(
                contact, false, accesses, visibleRelations, visibleCustomers, false)).toList();
    }

    private Set<ContactId> visiblePoolRelations(
            PoolContext context, Collection<Contact> contacts) {
        Set<ContactId> parentIds = contacts == null ? Set.of() : contacts.stream()
                .map(Contact::getParentContactId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return contactRepository.findPoolContactsByIds(
                        context.tenantId(), context.poolId(), parentIds, parentIds.size()).stream()
                .map(Contact::getId).collect(Collectors.toUnmodifiableSet());
    }

    private Set<CustomerId> visibleCustomers(
            PoolContext context, Collection<Contact> contacts) {
        if (context == null || !context.caller().hasAuthority(AuthorityCatalog.CUSTOMERS_READ)
                || contacts == null || contacts.isEmpty()) {
            return Set.of();
        }
        Set<CustomerId> ids = contacts.stream().map(Contact::getCustomerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        CustomerAccess access = customerAccessResolver.access(context.caller(),
                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        return customerRepository.findVisibleByIds(access.tenantId(), ids,
                        access.visibility(), ids.size()).stream()
                .map(io.github.archetom.crm.domain.entity.Customer::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<FilterCondition> conditions(
            PoolContext context, Long savedViewId, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        if (savedViewId == null) return List.of();
        SavedView view;
        try {
            view = savedViewRepository.findById(context.tenantId(), context.memberId(),
                            new SavedViewId(savedViewId))
                    .orElseThrow(() -> notFound("Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.CONTACT) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a contact view");
        }
        Map<FieldCode, CustomFieldType> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        FieldDefinition::getFieldType, (left, right) -> left));
        view.assertApplicableTo(searchable);
        view.getConditions().forEach(condition -> {
            requireReadable(accesses, condition.fieldCode());
            validateRelationCondition(context, condition);
        });
        return view.getConditions();
    }

    private void validateRelationCondition(PoolContext context, FilterCondition condition) {
        if (new FieldCode(ContactFieldCatalog.CUSTOMER).equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            Set<CustomerId> ids = customerIds(condition);
            CustomerAccess access = customerAccessResolver.access(context.caller(),
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            Set<CustomerId> visible = customerRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size())
                    .stream().map(io.github.archetom.crm.domain.entity.Customer::getId)
                    .collect(Collectors.toSet());
            if (!visible.containsAll(ids)) {
                throw relationConditionDenied();
            }
        } else if (new FieldCode(ContactFieldCatalog.PARENT_CONTACT).equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            Set<ContactId> ids = contactIds(condition);
            Set<ContactId> visible = contactRepository.findPoolContactsByIds(
                            context.tenantId(), context.poolId(), ids, ids.size())
                    .stream().map(Contact::getId).collect(Collectors.toSet());
            if (!visible.containsAll(ids)) {
                throw relationConditionDenied();
            }
        }
    }

    private void requireExactRelationOperator(FilterCondition condition) {
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw relationConditionDenied();
        }
    }

    private Set<CustomerId> customerIds(FilterCondition condition) {
        try {
            return condition.values().stream().map(value -> new CustomerId(Long.parseLong(value)))
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact customer relation value is invalid");
        }
    }

    private Set<ContactId> contactIds(FilterCondition condition) {
        try {
            return condition.values().stream().map(value -> new ContactId(Long.parseLong(value)))
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact parent relation value is invalid");
        }
    }

    private RuntimeException relationConditionDenied() {
        return error(ApplicationErrorCode.ACCESS_DENIED,
                "Contact relation condition is not available");
    }

    private void requireReadable(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Contact field cannot be used for this query");
        }
    }

    private void validateReferences(TenantId tenantId, ContactPool pool) {
        Set<MemberId> members = new LinkedHashSet<>(pool.getAdministratorMemberIds());
        members.addAll(pool.getEligibleMemberIds());
        contactSupport.requireActiveMembers(tenantId, members);
        if (!pool.getEligibleDepartmentIds().isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, pool.getEligibleDepartmentIds().stream().toList()).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(pool.getEligibleDepartmentIds())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contact pool department reference is invalid");
            }
        }
    }

    private PoolContext context(AuthenticatedCaller caller, String authority, Long poolId) {
        TenantId tenantId = accessGuard.requireActiveMembership(caller, authority);
        MemberId memberId = new MemberId(caller.actorId());
        OrganizationMembership membership = activeMembership(tenantId, memberId);
        return new PoolContext(caller, tenantId, memberId, membership.getDepartmentId(), poolId(poolId),
                isTenantOwner(tenantId, memberId));
    }

    private boolean isTenantOwner(TenantId tenantId, MemberId memberId) {
        return authorizationEvaluator.evaluate(tenantId, memberId).roles().stream()
                .anyMatch(role -> role.isActive() && role.getSystemKey() == SystemRoleKey.TENANT_OWNER);
    }

    private OrganizationMembership activeMembership(TenantId tenantId, MemberId memberId) {
        return membershipRepository.findByMember(tenantId, memberId)
                .filter(OrganizationMembership::isActive)
                .orElseThrow(() -> error(ApplicationErrorCode.ACCESS_DENIED,
                        "Contact pool member is inactive"));
    }

    private ContactPool requirePool(TenantId tenantId, ContactPoolId poolId) {
        return poolRepository.findById(tenantId, poolId)
                .orElseThrow(() -> notFound("Contact pool was not found"));
    }

    private ContactPool requireLockedPool(PoolContext context) {
        return poolRepository.findByIdForUpdate(context.tenantId(), context.poolId())
                .orElseThrow(() -> notFound("Contact pool was not found"));
    }

    private void synchronizeAutomation(TenantId tenantId) {
        automationSchedules.synchronize(tenantId, AutomationKind.CONTACT_POOL_RELEASE,
                poolRepository.hasActiveAutomaticRelease(tenantId));
    }

    private Contact requirePoolContact(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId) {
        return contactRepository.findPoolContact(tenantId, poolId, contactId)
                .orElseThrow(() -> notFound("Contact was not found in the pool"));
    }

    private Contact requirePoolContactForUpdate(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId) {
        return contactRepository.findPoolContactForUpdate(tenantId, poolId, contactId)
                .orElseThrow(() -> notFound("Contact was not found in the pool"));
    }

    private void requireAccessible(PoolContext context, ContactPool pool) {
        if (!context.tenantOwner() && !pool.isEligible(context.memberId(), context.departmentId())) {
            throw notFound("Contact pool was not found");
        }
    }

    private void requireAdministrator(PoolContext context, ContactPool pool) {
        if (!context.tenantOwner() && !pool.isAdministrator(context.memberId())) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Contact pool administrator authority is required");
        }
    }

    private void requireVersion(ContactPool pool, Long version) {
        if (version == null || !version.equals(pool.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Contact pool version is stale");
        }
    }

    private Map<ContactId, Long> versions(List<ContactVersionRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 100) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact pool batch is invalid");
        }
        Map<ContactId, Long> result = new LinkedHashMap<>();
        for (ContactVersionRequest value : values) {
            if (value == null || value.getVersion() == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contact pool batch entry is invalid");
            }
            ContactId id = contactId(value.getContactId());
            if (result.putIfAbsent(id, value.getVersion()) != null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contact pool batch contains duplicate contacts");
            }
        }
        return Map.copyOf(result);
    }

    private List<ContactPoolRule> rules(List<ContactPoolRuleRequest> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> {
            if (value == null || value.getInactiveDays() == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contact pool rule is invalid");
            }
            return new ContactPoolRule(parse(ContactPoolRuleType.class, value.getType(),
                    "Contact pool rule type"), value.getInactiveDays());
        }).toList();
    }

    private Set<MemberId> members(List<Long> values) {
        if (values == null) return Set.of();
        try {
            return values.stream().map(MemberId::new)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact pool member ID is invalid");
        }
    }

    private Set<DepartmentId> departments(List<Long> values) {
        if (values == null) return Set.of();
        try {
            return values.stream().map(DepartmentId::new)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact pool department ID is invalid");
        }
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    private Map<ContactPoolId, Long> contactCounts(
            TenantId tenantId, Collection<ContactPool> pools) {
        return poolRepository.countContacts(tenantId, pools.stream()
                .map(ContactPool::getId).filter(java.util.Objects::nonNull).toList());
    }

    private ContactPoolVO toVO(
            MemberId memberId, ContactPool pool, long contactCount, boolean tenantOwner) {
        return new ContactPoolVO(pool.getId().getValue(), pool.getName(), pool.getStatus().name(),
                sorted(pool.getAdministratorMemberIds(), MemberId::getValue),
                sorted(pool.getEligibleMemberIds(), MemberId::getValue),
                sorted(pool.getEligibleDepartmentIds(), DepartmentId::getValue),
                pool.getDailyClaimLimit(), pool.getPreviousOwnerCooldownDays(),
                pool.isAutomaticReleaseEnabled(), pool.getRules().stream()
                .map(rule -> new ContactPoolRuleVO(rule.type().name(), rule.inactiveDays())).toList(),
                contactCount, pool.isAdministrator(memberId) || tenantOwner,
                pool.getVersion(), pool.getCreatedTime(), pool.getUpdatedTime());
    }

    private ContactPoolVO toCatalogVO(
            ContactPoolCatalogQuery.Entry pool, long contactCount, boolean tenantOwner) {
        return new ContactPoolVO(pool.id().getValue(), pool.name(), pool.status().name(),
                List.of(), List.of(), List.of(), null, null, null, List.of(),
                contactCount, pool.administrator() || tenantOwner,
                null, null, null);
    }

    private <T> List<Long> sorted(Collection<T> values, Function<T, Long> mapper) {
        return values.stream().map(mapper).sorted().toList();
    }

    private <T> Pager<T> pager(PageResult<?> result) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(result.page());
        pager.setPageSize(result.size());
        pager.setTotalNum(result.total());
        return pager;
    }

    private int page(Integer value) {
        int resolved = value == null ? QueryRequest.DEFAULT_PAGE : value;
        if (resolved < 1 || resolved > QueryRequest.MAX_PAGE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Page is outside the supported range");
        }
        return resolved;
    }

    private int size(Integer value) {
        int resolved = value == null ? QueryRequest.DEFAULT_SIZE : value;
        if (resolved < 1 || resolved > QueryRequest.MAX_SIZE) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Page size is outside the supported range");
        }
        return resolved;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private ContactPoolId poolId(Long value) {
        try {
            return new ContactPoolId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Contact pool ID must be positive");
        }
    }

    private ContactId contactId(Long value) {
        try {
            return new ContactId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Contact ID must be positive");
        }
    }

    private void require(Object value, String description) {
        if (value == null) throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is required");
    }

    private io.github.archetom.crm.shared.exception.NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return contactSupport.error(code, message);
    }

    private io.github.archetom.crm.shared.exception.NonRetryableApplicationException notFound(String message) {
        return error(ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private record PoolContext(
            AuthenticatedCaller caller,
            TenantId tenantId,
            MemberId memberId,
            DepartmentId departmentId,
            ContactPoolId poolId,
            boolean tenantOwner) {
    }
}
