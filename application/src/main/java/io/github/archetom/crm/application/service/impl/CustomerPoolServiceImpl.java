package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerPoolAssignRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCreateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerBatchRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolCustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolRuleRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolStatusUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolTransferRequest;
import io.github.archetom.crm.api.dto.request.CustomerPoolUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerVersionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationScheduleManager;
import io.github.archetom.crm.application.port.out.CustomerPoolCatalogQuery;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.TenantAuthorizationEvaluator;
import io.github.archetom.crm.application.service.CustomerPoolService;
import io.github.archetom.crm.application.service.FileFieldBindingService;
import io.github.archetom.crm.application.service.attachment.AttachmentParentLifecycle;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.CustomerPoolRuleVO;
import io.github.archetom.crm.application.vo.CustomerPoolVO;
import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolMembership;
import io.github.archetom.crm.domain.entity.CustomerPoolRule;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerPoolRuleType;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.SystemRoleKey;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.CustomerPoolRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
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
public class CustomerPoolServiceImpl implements CustomerPoolService {

    private final CustomerPoolRepository poolRepository;
    private final CustomerPoolCatalogQuery poolCatalogQuery;
    private final CustomerRepository customerRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final DepartmentRepository departmentRepository;
    private final SavedViewRepository savedViewRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final CustomerAccessResolver customerAccessResolver;
    private final OrganizationAccessGuard accessGuard;
    private final TenantAuthorizationEvaluator authorizationEvaluator;
    private final CustomerApplicationSupport customerSupport;
    private final AttachmentParentLifecycle attachmentParentLifecycle;
    private final FileFieldBindingService fileFieldBindingService;
    private final CustomizationObjectLock objectLock;
    private final AutomationScheduleManager automationSchedules;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<CustomerPoolVO>> query(
            AuthenticatedCaller caller, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_POOL_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private boolean tenantOwner;
            private int resolvedPage;
            private int resolvedSize;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE);
                memberId = new MemberId(caller.actorId());
                tenantOwner = isTenantOwner(tenantId, memberId);
                resolvedPage = page(page);
                resolvedSize = size(size);
            }

            @Override
            public Pager<CustomerPoolVO> execute() {
                PageResult<CustomerPool> result = poolRepository.findAll(
                        tenantId, resolvedPage, resolvedSize);
                Map<CustomerPoolId, Long> counts = customerCounts(tenantId, result.items());
                Pager<CustomerPoolVO> pager = pager(result);
                pager.setObjectList(result.items().stream()
                        .map(pool -> toVO(memberId, pool,
                                counts.getOrDefault(pool.getId(), 0L), tenantOwner)).toList());
                return pager;
            }
        });
    }

    @Override
    public Result<List<CustomerPoolVO>> catalog(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_POOL_CATALOG, new ServiceOperation<>() {
            private TenantId tenantId;
            private OrganizationMembership membership;
            private boolean tenantOwner;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.CUSTOMER_POOLS_READ);
                membership = activeMembership(tenantId, new MemberId(caller.actorId()));
                tenantOwner = isTenantOwner(tenantId, membership.getMemberId());
            }

            @Override
            public List<CustomerPoolVO> execute() {
                List<CustomerPoolCatalogQuery.Entry> pools = poolCatalogQuery.findAccessible(tenantId,
                        membership.getMemberId(), membership.getDepartmentId(), tenantOwner);
                Map<CustomerPoolId, Long> counts = poolRepository.countCustomers(tenantId,
                        pools.stream().map(CustomerPoolCatalogQuery.Entry::id).toList());
                return pools.stream().map(pool -> toCatalogVO(pool,
                        counts.getOrDefault(pool.id(), 0L), tenantOwner)).toList();
            }
        });
    }

    @Override
    public Result<CustomerPoolVO> get(AuthenticatedCaller caller, Long poolId) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_POOL_GET, new ServiceOperation<>() {
            private PoolContext context;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE, poolId);
            }

            @Override
            public CustomerPoolVO execute() {
                CustomerPool pool = requirePool(context.tenantId(), context.poolId());
                return toVO(context.memberId(), pool,
                        poolRepository.countCustomers(context.tenantId(), pool.getId()),
                        context.tenantOwner());
            }
        });
    }

    @Override
    public Result<CustomerPoolVO> create(
            AuthenticatedCaller caller, CustomerPoolCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId callerId;
            private boolean tenantOwner;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE);
                callerId = new MemberId(caller.actorId());
                tenantOwner = isTenantOwner(tenantId, callerId);
                require(request, "Customer pool create request");
            }

            @Override
            public CustomerPoolVO execute() {
                boolean automationConfiguration = Boolean.TRUE.equals(request.getAutomaticReleaseEnabled());
                if (automationConfiguration) {
                    objectLock.acquire(tenantId, BusinessObjectType.CUSTOMER);
                    automationSchedules.preparePoolConfiguration(tenantId,
                            AutomationKind.CUSTOMER_POOL_RELEASE, true);
                }
                CustomerPool pool = CustomerPool.create(tenantId, request.getName(),
                        members(request.getAdministratorActorIds()), members(request.getEligibleActorIds()),
                        departments(request.getEligibleDepartmentIds()), value(request.getDailyClaimLimit()),
                        value(request.getPreviousOwnerCooldownDays()),
                        Boolean.TRUE.equals(request.getAutomaticReleaseEnabled()), rules(request.getRules()));
                validateReferences(tenantId, pool);
                if (poolRepository.existsByName(tenantId, pool.getName(), null)) {
                    throw error(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Customer pool name already exists");
                }
                CustomerPool saved = poolRepository.save(tenantId, pool);
                if (automationConfiguration) synchronizeAutomation(tenantId);
                return toVO(callerId, saved, 0L, tenantOwner);
            }
        });
    }

    @Override
    public Result<CustomerPoolVO> update(
            AuthenticatedCaller caller, Long poolId, CustomerPoolUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_UPDATE, new ServiceOperation<>() {
            private PoolContext context;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE, poolId);
                require(request, "Customer pool update request");
            }

            @Override
            public CustomerPoolVO execute() {
                CustomerPool current = requirePool(context.tenantId(), context.poolId());
                boolean automationConfiguration = current.isActive()
                        && (current.isAutomaticReleaseEnabled()
                        || Boolean.TRUE.equals(request.getAutomaticReleaseEnabled()));
                if (automationConfiguration) {
                    objectLock.acquire(context.tenantId(), BusinessObjectType.CUSTOMER);
                    automationSchedules.preparePoolConfiguration(context.tenantId(),
                            AutomationKind.CUSTOMER_POOL_RELEASE, true);
                }
                CustomerPool pool = requireLockedPool(context);
                requireAdministrator(context, pool);
                requireVersion(pool, request.getVersion());
                pool.changeConfiguration(request.getName(), members(request.getAdministratorActorIds()),
                        members(request.getEligibleActorIds()), departments(request.getEligibleDepartmentIds()),
                        value(request.getDailyClaimLimit()), value(request.getPreviousOwnerCooldownDays()),
                        Boolean.TRUE.equals(request.getAutomaticReleaseEnabled()), rules(request.getRules()));
                validateReferences(context.tenantId(), pool);
                if (poolRepository.existsByName(context.tenantId(), pool.getName(), pool.getId())) {
                    throw error(ApplicationErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Customer pool name already exists");
                }
                CustomerPool saved = poolRepository.save(context.tenantId(), pool);
                if (automationConfiguration) synchronizeAutomation(context.tenantId());
                return toVO(context.memberId(), saved,
                        poolRepository.countCustomers(context.tenantId(), saved.getId()),
                        context.tenantOwner());
            }
        });
    }

    @Override
    public Result<CustomerPoolVO> updateStatus(
            AuthenticatedCaller caller, Long poolId, CustomerPoolStatusUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_STATUS_UPDATE,
                new ServiceOperation<>() {
                    private PoolContext context;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE, poolId);
                        require(request, "Customer pool status request");
                    }

                    @Override
                    public CustomerPoolVO execute() {
                        CustomerPool current = requirePool(context.tenantId(), context.poolId());
                        boolean automationConfiguration = current.isAutomaticReleaseEnabled()
                                && (current.isActive() || Boolean.TRUE.equals(request.getActive()));
                        if (automationConfiguration) {
                            objectLock.acquire(context.tenantId(), BusinessObjectType.CUSTOMER);
                            automationSchedules.preparePoolConfiguration(context.tenantId(),
                                    AutomationKind.CUSTOMER_POOL_RELEASE, current.isActive()
                                            || Boolean.TRUE.equals(request.getActive()));
                        }
                        CustomerPool pool = requireLockedPool(context);
                        requireAdministrator(context, pool);
                        requireVersion(pool, request.getVersion());
                        if (!Boolean.TRUE.equals(request.getActive())
                                && poolRepository.countCustomers(context.tenantId(), pool.getId()) > 0) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "A customer pool with customers cannot be deactivated");
                        }
                        pool.changeStatus(Boolean.TRUE.equals(request.getActive())
                                ? CustomerPoolStatus.ACTIVE : CustomerPoolStatus.INACTIVE);
                        CustomerPool saved = poolRepository.save(context.tenantId(), pool);
                        if (automationConfiguration) synchronizeAutomation(context.tenantId());
                        return toVO(context.memberId(), saved,
                                poolRepository.countCustomers(context.tenantId(), saved.getId()),
                                context.tenantOwner());
                    }
                });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long poolId, Long version) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_DELETE, new ServiceOperation<>() {
            private PoolContext context;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE, poolId);
            }

            @Override
            public Void execute() {
                CustomerPool current = requirePool(context.tenantId(), context.poolId());
                boolean automationConfiguration = current.isActive() && current.isAutomaticReleaseEnabled();
                if (automationConfiguration) {
                    objectLock.acquire(context.tenantId(), BusinessObjectType.CUSTOMER);
                    automationSchedules.preparePoolConfiguration(context.tenantId(),
                            AutomationKind.CUSTOMER_POOL_RELEASE, false);
                }
                CustomerPool pool = requireLockedPool(context);
                requireAdministrator(context, pool);
                requireVersion(pool, version);
                if (poolRepository.countCustomers(context.tenantId(), pool.getId()) > 0) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "A customer pool with customers cannot be deleted");
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
            AuthenticatedCaller caller, Long poolId, CustomerPoolTransferRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_TRANSFER, new ServiceOperation<>() {
            private PoolContext context;
            private CustomerPoolId targetId;

            @Override
            public void validate() {
                context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_MANAGE, poolId);
                require(request, "Customer pool transfer request");
                targetId = poolId(request.getTargetPoolId());
                if (context.poolId().sameValueAs(targetId)) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Target customer pool must be different");
                }
            }

            @Override
            public Void execute() {
                List<CustomerPoolId> ordered = List.of(context.poolId(), targetId).stream()
                        .sorted(Comparator.comparing(CustomerPoolId::getValue)).toList();
                Map<CustomerPoolId, CustomerPool> locked = new LinkedHashMap<>();
                ordered.forEach(id -> locked.put(id, poolRepository.findByIdForUpdate(context.tenantId(), id)
                        .orElseThrow(() -> notFound("Customer pool was not found"))));
                CustomerPool source = locked.get(context.poolId());
                CustomerPool target = locked.get(targetId);
                requireAdministrator(context, source);
                if (!target.isActive()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Target customer pool is inactive");
                }
                poolRepository.transferMemberships(context.tenantId(), source.getId(), target.getId());
                return null;
            }
        });
    }

    @Override
    public Result<Pager<CustomerVO>> customers(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_POOL_CUSTOMER_QUERY,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private int page;
                    private int size;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_READ, poolId);
                        require(request, "Customer pool query request");
                        page = page(request.getPage());
                        size = size(request.getSize());
                    }

                    @Override
                    public Pager<CustomerVO> execute() {
                        CustomerPool pool = requirePool(context.tenantId(), context.poolId());
                        requireAccessible(context, pool);
                        List<FieldDefinition> fields = customerSupport.fields(context.tenantId()).stream()
                                .filter(FieldDefinition::isActive).toList();
                        Map<FieldCode, EffectiveFieldAccess> accesses = customerSupport.accesses(
                                context.tenantId(), context.memberId());
                        if (request.getName() != null && !request.getName().isBlank()) {
                            requireReadable(accesses, new FieldCode("customer_name"));
                        }
                        List<FilterCondition> conditions = conditions(
                                context, request.getSavedViewId(), fields, accesses);
                        PageResult<Customer> result = customerRepository.findPoolCustomers(
                                context.tenantId(), context.poolId(), request.getName(), conditions, page, size);
                        Set<CustomerId> visibleRelations = visiblePoolRelations(context, result.items());
                        Pager<CustomerVO> pager = pager(result);
                        pager.setObjectList(result.items().stream()
                                .map(customer -> customerSupport.toVO(
                                        customer, false, accesses, visibleRelations))
                                .toList());
                        return pager;
                    }
                });
    }

    @Override
    public Result<CustomerVO> customer(
            AuthenticatedCaller caller, Long poolId, Long customerId) {
        return queryTemplate.execute(UseCaseOperation.CUSTOMER_POOL_CUSTOMER_GET,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private CustomerId id;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_READ, poolId);
                        id = customerId(customerId);
                    }

                    @Override
                    public CustomerVO execute() {
                        CustomerPool pool = requirePool(context.tenantId(), context.poolId());
                        requireAccessible(context, pool);
                        Customer customer = requirePoolCustomer(context.tenantId(), context.poolId(), id);
                        return customerSupport.toVO(customer, false,
                                customerSupport.accesses(context.tenantId(), context.memberId()),
                                visiblePoolRelations(context, List.of(customer)));
                    }
                });
    }

    @Override
    public Result<List<CustomerVO>> release(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerBatchRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_CUSTOMER_RELEASE,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private CustomerAccessResolver.CustomerAccess customerAccess;
                    private Map<CustomerId, Long> versions;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_RELEASE, poolId);
                        customerAccess = customerAccessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
                        versions = versions(request == null ? null : request.getCustomers());
                    }

                    @Override
                    public List<CustomerVO> execute() {
                        objectLock.acquire(context.tenantId(), BusinessObjectType.CUSTOMER);
                        CustomerPool pool = requireLockedPool(context);
                        requireAccessible(context, pool);
                        if (!pool.isActive()) {
                            throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                    "Customer pool is inactive");
                        }
                        List<FieldDefinition> fields = customerSupport.fields(context.tenantId());
                        List<Customer> changed = new java.util.ArrayList<>();
                        for (Map.Entry<CustomerId, Long> entry : versions.entrySet()) {
                            Customer customer = customerRepository.findVisibleById(
                                            context.tenantId(), entry.getKey(), customerAccess.visibility())
                                    .orElseThrow(() -> notFound("Customer was not found"));
                            customerSupport.requireVersion(customer, entry.getValue());
                            MemberId previousOwner = customer.getOwnerMemberId();
                            LocalDateTime releasedTime = LocalDateTime.now();
                            customer.enterPool();
                            customerRepository.save(context.tenantId(), customer, fields);
                            poolRepository.addMembership(new CustomerPoolMembership(
                                    context.tenantId(), pool.getId(), customer.getId(), previousOwner,
                                    releasedTime));
                            poolRepository.recordRelease(context.tenantId(), pool.getId(),
                                    customer.getId(), previousOwner, releasedTime);
                            changed.add(customer);
                        }
                        return customerVOs(context, changed);
                    }
                });
    }

    @Override
    public Result<List<CustomerVO>> claim(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerBatchRequest request) {
        return leavePool(caller, poolId, request == null ? null : request.getCustomers(),
                null, false, UseCaseOperation.CUSTOMER_POOL_CUSTOMER_CLAIM);
    }

    @Override
    public Result<List<CustomerVO>> assign(
            AuthenticatedCaller caller, Long poolId, CustomerPoolAssignRequest request) {
        if (request == null) {
            return leavePool(caller, poolId, null, null, true,
                    UseCaseOperation.CUSTOMER_POOL_CUSTOMER_ASSIGN);
        }
        MemberId target;
        try {
            target = new MemberId(request.getOwnerActorId());
        } catch (RuntimeException exception) {
            return leavePool(caller, poolId, null, null, true,
                    UseCaseOperation.CUSTOMER_POOL_CUSTOMER_ASSIGN);
        }
        return leavePool(caller, poolId, request.getCustomers(), target, true,
                UseCaseOperation.CUSTOMER_POOL_CUSTOMER_ASSIGN);
    }

    @Override
    public Result<Void> deleteCustomers(
            AuthenticatedCaller caller, Long poolId, CustomerPoolCustomerBatchRequest request) {
        return commandTemplate.execute(UseCaseOperation.CUSTOMER_POOL_CUSTOMER_DELETE,
                new ServiceOperation<>() {
                    private PoolContext context;
                    private Map<CustomerId, Long> versions;

                    @Override
                    public void validate() {
                        context = context(caller, AuthorityCatalog.CUSTOMER_POOLS_DELETE, poolId);
                        versions = versions(request == null ? null : request.getCustomers());
                    }

                    @Override
                    public Void execute() {
                        objectLock.acquire(context.tenantId(), BusinessObjectType.CUSTOMER);
                        CustomerPool pool = requireLockedPool(context);
                        requireAdministrator(context, pool);
                        List<FieldDefinition> fields = customerSupport.fields(context.tenantId());
                        for (Map.Entry<CustomerId, Long> entry : versions.entrySet()) {
                            poolRepository.findMembershipForUpdate(
                                            context.tenantId(), pool.getId(), entry.getKey())
                                    .orElseThrow(() -> notFound("Customer was not found in the pool"));
                            Customer customer = requirePoolCustomerForUpdate(
                                    context.tenantId(), pool.getId(), entry.getKey());
                            customerSupport.requireVersion(customer, entry.getValue());
                            if (customerRepository.existsActiveChild(context.tenantId(), customer.getId())) {
                                throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                        "Customer with child customers cannot be deleted");
                            }
                            customer.deleteFromPool();
                            attachmentParentLifecycle.withdrawAll(context.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.CUSTOMER,
                                            customer.getId().getValue()));
                            fileFieldBindingService.withdrawAll(context.tenantId(),
                                    new AttachmentTarget(AttachmentTargetType.CUSTOMER,
                                            customer.getId().getValue()));
                            customerRepository.save(context.tenantId(), customer, fields);
                            poolRepository.removeMembership(context.tenantId(), pool.getId(), customer.getId());
                        }
                        return null;
                    }
                });
    }

    private Result<List<CustomerVO>> leavePool(
            AuthenticatedCaller caller, Long poolId, List<CustomerVersionRequest> requested,
            MemberId requestedOwner, boolean assigned, UseCaseOperation operation) {
        return commandTemplate.execute(operation, new ServiceOperation<>() {
            private PoolContext context;
            private Map<CustomerId, Long> versions;
            private MemberId owner;

            @Override
            public void validate() {
                context = context(caller, assigned ? AuthorityCatalog.CUSTOMER_POOLS_ASSIGN
                        : AuthorityCatalog.CUSTOMER_POOLS_CLAIM, poolId);
                versions = versions(requested);
                owner = assigned ? requestedOwner : context.memberId();
                if (owner == null) {
                    throw error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Customer pool owner is invalid");
                }
                activeMembership(context.tenantId(), owner);
            }

            @Override
            public List<CustomerVO> execute() {
                objectLock.acquire(context.tenantId(), BusinessObjectType.CUSTOMER);
                CustomerPool pool = requireLockedPool(context);
                if (!pool.isActive()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Customer pool is inactive");
                }
                if (assigned) requireAdministrator(context, pool);
                else requireAccessible(context, pool);
                LocalDateTime now = LocalDateTime.now();
                if (!assigned && pool.getDailyClaimLimit() > 0
                        && poolRepository.countClaims(context.tenantId(), pool.getId(), owner,
                        now.toLocalDate()) + versions.size() > pool.getDailyClaimLimit()) {
                    throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                            "Customer pool daily claim limit was reached");
                }
                List<FieldDefinition> fields = customerSupport.fields(context.tenantId());
                List<Customer> changed = new java.util.ArrayList<>();
                for (Map.Entry<CustomerId, Long> entry : versions.entrySet()) {
                    CustomerPoolMembership membership = poolRepository.findMembershipForUpdate(
                                    context.tenantId(), pool.getId(), entry.getKey())
                            .orElseThrow(() -> notFound("Customer was not found in the pool"));
                    Customer customer = requirePoolCustomer(context.tenantId(), pool.getId(), entry.getKey());
                    customerSupport.requireVersion(customer, entry.getValue());
                    if (!assigned && membership.previousOwnerMemberId() != null
                            && membership.previousOwnerMemberId().sameValueAs(owner)
                            && membership.enteredTime().plusDays(pool.getPreviousOwnerCooldownDays())
                            .isAfter(now)) {
                        throw error(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                                "Previous owner cooldown is still active");
                    }
                    customer.leavePool(owner);
                    attachmentParentLifecycle.resumeAll(context.tenantId(),
                            new AttachmentTarget(AttachmentTargetType.CUSTOMER,
                                    customer.getId().getValue()));
                    customerRepository.save(context.tenantId(), customer, fields);
                    poolRepository.removeMembership(context.tenantId(), pool.getId(), customer.getId());
                    poolRepository.recordClaim(context.tenantId(), pool.getId(), customer.getId(),
                            owner, now, assigned);
                    changed.add(customer);
                }
                return customerVOs(context, changed);
            }
        });
    }

    private List<CustomerVO> customerVOs(PoolContext context, Collection<Customer> customers) {
        Map<FieldCode, EffectiveFieldAccess> accesses = customerSupport.accesses(
                context.tenantId(), context.memberId());
        Set<CustomerId> visibleRelations = visiblePoolRelations(context, customers);
        return customers.stream().map(customer -> customerSupport.toVO(
                customer, false, accesses, visibleRelations)).toList();
    }

    private Set<CustomerId> visiblePoolRelations(
            PoolContext context, Collection<Customer> customers) {
        Set<CustomerId> parentIds = customers == null ? Set.of() : customers.stream()
                .map(Customer::getParentCustomerId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return customerRepository.findPoolCustomersByIds(
                        context.tenantId(), context.poolId(), parentIds, parentIds.size()).stream()
                .map(Customer::getId).collect(Collectors.toUnmodifiableSet());
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
        if (view.getObjectType() != BusinessObjectType.CUSTOMER) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a customer view");
        }
        Map<FieldCode, CustomFieldType> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        FieldDefinition::getFieldType, (left, right) -> left));
        view.assertApplicableTo(searchable);
        view.getConditions().forEach(condition -> requireReadable(accesses, condition.fieldCode()));
        return view.getConditions();
    }

    private void requireReadable(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Customer field cannot be used for this query");
        }
    }

    private void validateReferences(TenantId tenantId, CustomerPool pool) {
        Set<MemberId> members = new LinkedHashSet<>(pool.getAdministratorMemberIds());
        members.addAll(pool.getEligibleMemberIds());
        customerSupport.requireActiveMembers(tenantId, members);
        if (!pool.getEligibleDepartmentIds().isEmpty()) {
            Set<DepartmentId> active = departmentRepository.findByIds(
                            tenantId, pool.getEligibleDepartmentIds().stream().toList()).stream()
                    .filter(io.github.archetom.crm.domain.entity.Department::isActive)
                    .map(io.github.archetom.crm.domain.entity.Department::getId)
                    .collect(Collectors.toSet());
            if (!active.containsAll(pool.getEligibleDepartmentIds())) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer pool department reference is invalid");
            }
        }
    }

    private PoolContext context(AuthenticatedCaller caller, String authority, Long poolId) {
        TenantId tenantId = accessGuard.requireActiveMembership(caller, authority);
        MemberId memberId = new MemberId(caller.actorId());
        OrganizationMembership membership = activeMembership(tenantId, memberId);
        return new PoolContext(tenantId, memberId, membership.getDepartmentId(), poolId(poolId),
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
                        "Customer pool member is inactive"));
    }

    private CustomerPool requirePool(TenantId tenantId, CustomerPoolId poolId) {
        return poolRepository.findById(tenantId, poolId)
                .orElseThrow(() -> notFound("Customer pool was not found"));
    }

    private CustomerPool requireLockedPool(PoolContext context) {
        return poolRepository.findByIdForUpdate(context.tenantId(), context.poolId())
                .orElseThrow(() -> notFound("Customer pool was not found"));
    }

    private void synchronizeAutomation(TenantId tenantId) {
        automationSchedules.synchronize(tenantId, AutomationKind.CUSTOMER_POOL_RELEASE,
                poolRepository.hasActiveAutomaticRelease(tenantId));
    }

    private Customer requirePoolCustomer(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId) {
        return customerRepository.findPoolCustomer(tenantId, poolId, customerId)
                .orElseThrow(() -> notFound("Customer was not found in the pool"));
    }

    private Customer requirePoolCustomerForUpdate(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId) {
        return customerRepository.findPoolCustomerForUpdate(tenantId, poolId, customerId)
                .orElseThrow(() -> notFound("Customer was not found in the pool"));
    }

    private void requireAccessible(PoolContext context, CustomerPool pool) {
        if (!context.tenantOwner() && !pool.isEligible(context.memberId(), context.departmentId())) {
            throw notFound("Customer pool was not found");
        }
    }

    private void requireAdministrator(PoolContext context, CustomerPool pool) {
        if (!context.tenantOwner() && !pool.isAdministrator(context.memberId())) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Customer pool administrator authority is required");
        }
    }

    private void requireVersion(CustomerPool pool, Long version) {
        if (version == null || !version.equals(pool.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT,
                    "Customer pool version is stale");
        }
    }

    private Map<CustomerId, Long> versions(List<CustomerVersionRequest> values) {
        if (values == null || values.isEmpty() || values.size() > 100) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer pool batch is invalid");
        }
        Map<CustomerId, Long> result = new LinkedHashMap<>();
        for (CustomerVersionRequest value : values) {
            if (value == null || value.getVersion() == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer pool batch entry is invalid");
            }
            CustomerId id = customerId(value.getCustomerId());
            if (result.putIfAbsent(id, value.getVersion()) != null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer pool batch contains duplicate customers");
            }
        }
        return Map.copyOf(result);
    }

    private List<CustomerPoolRule> rules(List<CustomerPoolRuleRequest> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> {
            if (value == null || value.getInactiveDays() == null) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Customer pool rule is invalid");
            }
            return new CustomerPoolRule(parse(CustomerPoolRuleType.class, value.getType(),
                    "Customer pool rule type"), value.getInactiveDays());
        }).toList();
    }

    private Set<MemberId> members(List<Long> values) {
        if (values == null) return Set.of();
        try {
            return values.stream().map(MemberId::new)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer pool member ID is invalid");
        }
    }

    private Set<DepartmentId> departments(List<Long> values) {
        if (values == null) return Set.of();
        try {
            return values.stream().map(DepartmentId::new)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer pool department ID is invalid");
        }
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    private Map<CustomerPoolId, Long> customerCounts(
            TenantId tenantId, Collection<CustomerPool> pools) {
        return poolRepository.countCustomers(tenantId, pools.stream()
                .map(CustomerPool::getId).filter(java.util.Objects::nonNull).toList());
    }

    private CustomerPoolVO toVO(
            MemberId memberId, CustomerPool pool, long customerCount, boolean tenantOwner) {
        return new CustomerPoolVO(pool.getId().getValue(), pool.getName(), pool.getStatus().name(),
                sorted(pool.getAdministratorMemberIds(), MemberId::getValue),
                sorted(pool.getEligibleMemberIds(), MemberId::getValue),
                sorted(pool.getEligibleDepartmentIds(), DepartmentId::getValue),
                pool.getDailyClaimLimit(), pool.getPreviousOwnerCooldownDays(),
                pool.isAutomaticReleaseEnabled(), pool.getRules().stream()
                .map(rule -> new CustomerPoolRuleVO(rule.type().name(), rule.inactiveDays())).toList(),
                customerCount, pool.isAdministrator(memberId) || tenantOwner,
                pool.getVersion(), pool.getCreatedTime(), pool.getUpdatedTime());
    }

    private CustomerPoolVO toCatalogVO(
            CustomerPoolCatalogQuery.Entry pool, long customerCount, boolean tenantOwner) {
        return new CustomerPoolVO(pool.id().getValue(), pool.name(), pool.status().name(),
                List.of(), List.of(), List.of(), null, null, null, List.of(),
                customerCount, pool.administrator() || tenantOwner,
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

    private CustomerPoolId poolId(Long value) {
        try {
            return new CustomerPoolId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Customer pool ID must be positive");
        }
    }

    private CustomerId customerId(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Customer ID must be positive");
        }
    }

    private void require(Object value, String description) {
        if (value == null) throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is required");
    }

    private io.github.archetom.crm.shared.exception.NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return customerSupport.error(code, message);
    }

    private io.github.archetom.crm.shared.exception.NonRetryableApplicationException notFound(String message) {
        return error(ApplicationErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private record PoolContext(
            TenantId tenantId,
            MemberId memberId,
            DepartmentId departmentId,
            CustomerPoolId poolId,
            boolean tenantOwner) {
    }
}
