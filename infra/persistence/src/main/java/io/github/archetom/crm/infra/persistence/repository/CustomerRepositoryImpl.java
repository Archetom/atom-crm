package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.CustomerAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerSortDirection;
import io.github.archetom.crm.domain.model.CustomerDistance;
import io.github.archetom.crm.domain.model.CustomerExportCell;
import io.github.archetom.crm.domain.model.CustomerExportQuery;
import io.github.archetom.crm.domain.model.CustomerExportRow;
import io.github.archetom.crm.domain.model.CustomerHierarchyStats;
import io.github.archetom.crm.domain.model.CustomerNearbyQuery;
import io.github.archetom.crm.domain.model.CustomerQuery;
import io.github.archetom.crm.domain.model.CustomerContactReminderSummary;
import io.github.archetom.crm.domain.model.CustomerFollowUpSummary;
import io.github.archetom.crm.domain.model.CustomerStatus;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.CustomerPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerAddressMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerCollaboratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerRegisteredIdentityMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerTagMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerDistancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerExportRootPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerExportValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerHierarchyStatsPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerTagPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.support.ChildCollectionSynchronizer;
import io.github.archetom.crm.infra.persistence.support.GeoBoundingBox;
import io.github.archetom.crm.infra.persistence.support.JsonSemanticEquality;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Customer repository with explicit tenant predicates on every root and child query. */
@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    private static final int MAX_INTERNAL_LIMIT = 2_001;
    private static final int MAX_EXPORT_ID_LIMIT = 5_001;
    private static final int MAX_EXPORT_BATCH_LIMIT = 200;
    private static final int MAX_HIERARCHY_DEPTH = CustomerHierarchyStats.MAX_DEPTH;
    private static final int MAX_HIERARCHY_TRAVERSAL = CustomerHierarchyStats.MAX_TREE_NODES;
    private static final Map<FieldCode, String> BUILTIN_COLUMNS = builtinColumns();
    private static final Map<FieldCode, Function<CustomerExportRootPO, String>>
            BUILTIN_EXPORT_VALUES = builtinExportValues();

    private final CustomerMapper customerMapper;
    private final CustomerAddressMapper addressMapper;
    private final CustomerTagMapper tagMapper;
    private final CustomerCollaboratorMapper collaboratorMapper;
    private final CustomerFieldValueMapper fieldValueMapper;
    private final CustomerUniqueFieldValueMapper uniqueFieldValueMapper;
    private final CustomerRegisteredIdentityMapper registeredIdentityMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final CustomerPOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final ObjectMapper objectMapper;

    @Override
    public Customer save(
            TenantId tenantId, Customer customer,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, customer);
        Map<FieldCode, FieldDefinition> fieldsByCode = definitionsByCode(fieldDefinitions);
        CustomerPO po = converter.toPO(customer);
        try {
            if (po.getId() == null) {
                if (customerMapper.insert(po) != 1) {
                    throw new IllegalStateException("Customer insert did not affect exactly one row");
                }
            } else if (customerMapper.update(po, tenantRootFilter(tenantId)
                    .eq(CustomerPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }

            CustomerId customerId = new CustomerId(po.getId());
            synchronizeChildren(tenantId, customerId, customer, fieldsByCode);
            if (customer.isDeleted()) {
                registeredIdentityMapper.releaseCurrentForCustomer(
                        tenantId.getValue(), customerId.getValue());
            }
            customer.onPersisted(customerId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return customer;
        } catch (DuplicateKeyException exception) {
            throw new CustomerAlreadyExistsException();
        }
    }

    @Override
    public Optional<Customer> findById(TenantId tenantId, CustomerId customerId) {
        requireTenant(tenantId);
        if (customerId == null) {
            return Optional.empty();
        }
        CustomerPO po = customerMapper.selectOne(tenantRootFilter(tenantId)
                .eq(CustomerPO::getId, customerId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Customer> findByIdForAttachmentMutation(TenantId tenantId, CustomerId customerId) {
        requireTenant(tenantId);
        if (customerId == null) return Optional.empty();
        CustomerPO po = customerMapper.selectOne(tenantRootFilter(tenantId)
                .eq(CustomerPO::getId, customerId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Customer> findPoolCustomer(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId) {
        requireTenant(tenantId);
        if (poolId == null || customerId == null) {
            return Optional.empty();
        }
        QueryWrapper<CustomerPO> query = poolRootQuery(tenantId, poolId)
                .eq("id", customerId.getValue());
        CustomerPO po = customerMapper.selectOne(query);
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Customer> findPoolCustomerForUpdate(
            TenantId tenantId, CustomerPoolId poolId, CustomerId customerId) {
        requireTenant(tenantId);
        if (poolId == null || customerId == null) return Optional.empty();
        CustomerPO po = customerMapper.selectOne(poolRootQuery(tenantId, poolId)
                .eq("id", customerId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public List<Customer> findPoolCustomersByIds(
            TenantId tenantId, CustomerPoolId poolId,
            Collection<CustomerId> customerIds, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (poolId == null) {
            throw new IllegalArgumentException("customer pool ID must not be null");
        }
        List<Long> ids = ids(customerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<CustomerPO> query = poolRootQuery(tenantId, poolId)
                .in("id", ids).orderByAsc("id").last("LIMIT " + boundedLimit);
        return hydrated(tenantId, customerMapper.selectList(query));
    }

    @Override
    public PageResult<Customer> findPoolCustomers(
            TenantId tenantId, CustomerPoolId poolId, String name,
            List<FilterCondition> conditions, int page, int size) {
        requireTenant(tenantId);
        if (poolId == null || page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("customer pool query is invalid");
        }
        QueryWrapper<CustomerPO> query = poolRootQuery(tenantId, poolId);
        if (name != null && !name.isBlank()) {
            applyNamePrefix(query, name.trim());
        }
        if (conditions != null) {
            conditions.forEach(condition -> applyCondition(query, condition));
        }
        query.orderByDesc("updated_time").orderByDesc("id");
        IPage<CustomerPO> result = customerMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()),
                hydrated(tenantId, result.getRecords()));
    }

    @Override
    public Optional<Customer> findVisibleById(
            TenantId tenantId, CustomerId customerId, CustomerVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            return Optional.empty();
        }
        QueryWrapper<CustomerPO> query = visibleRootQuery(tenantId, visibility)
                .eq("id", customerId.getValue());
        CustomerPO po = customerMapper.selectOne(query);
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Customer> findVisibleByIdForUpdate(
            TenantId tenantId, CustomerId customerId, CustomerVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) return Optional.empty();
        CustomerPO po = customerMapper.selectOne(visibleRootQuery(tenantId, visibility)
                .eq("id", customerId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public PageResult<Customer> findCustomers(TenantId tenantId, CustomerQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("customer query must not be null");
        }
        QueryWrapper<CustomerPO> wrapper = visibleRootQuery(tenantId, query.visibility());
        applyKeyword(wrapper, query.keyword(), query.keywordCandidates());
        if (query.name() != null) {
            applyNamePrefix(wrapper, query.name());
        }
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.subordinateOwnerMemberIds() != null) {
            List<Long> ownerIds = query.subordinateOwnerMemberIds().stream()
                    .map(value -> value.getValue()).toList();
            if (ownerIds.isEmpty()) wrapper.apply("1 = 0");
            else wrapper.in("owner_actor_id", ownerIds);
        }
        applyStarred(wrapper, query.visibility(), query.starred());
        for (FilterCondition condition : query.conditions()) {
            applyCondition(wrapper, condition);
        }
        boolean ascending = query.sort().direction() == CustomerSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<CustomerPO> result = customerMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()),
                hydrated(tenantId, result.getRecords()));
    }

    @Override
    public DashboardMetricSummary summarizeCreated(
            TenantId tenantId, CustomerVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        requireReportRange(range);
        long count = customerMapper.selectCount(visibleRootQuery(tenantId, visibility)
                .ge("created_time", range.from().atStartOfDay())
                .le("created_time", range.to().atTime(23, 59, 59, 999_999_000)));
        return new DashboardMetricSummary(Math.max(0L, count), null);
    }

    @Override
    public DashboardMetricSummary summarizeConverted(
            TenantId tenantId, CustomerVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        requireReportRange(range);
        long count = customerMapper.selectCount(visibleRootQuery(tenantId, visibility)
                .eq("deal_status", "WON")
                .ge("deal_time", range.from().atStartOfDay())
                .le("deal_time", range.to().atTime(23, 59, 59, 999_999_000)));
        return new DashboardMetricSummary(Math.max(0L, count), null);
    }

    @Override
    public CustomerContactReminderSummary summarizeContactReminders(
            TenantId tenantId, CustomerVisibility visibility, LocalDateTime asOf) {
        requireTenant(tenantId);
        if (asOf == null) {
            throw new IllegalArgumentException("customer contact reminder timestamp must not be null");
        }
        return new CustomerContactReminderSummary(
                countContactOlderThan(tenantId, visibility, asOf.minusDays(7)),
                countContactOlderThan(tenantId, visibility, asOf.minusDays(15)),
                countContactOlderThan(tenantId, visibility, asOf.minusDays(30)),
                countContactOlderThan(tenantId, visibility, asOf.minusMonths(3)),
                countContactOlderThan(tenantId, visibility, asOf.minusMonths(6)),
                countOverdueContacts(tenantId, visibility, asOf));
    }

    @Override
    public CustomerFollowUpSummary summarizeFollowUpCoverage(
            TenantId tenantId, CustomerVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        requireReportRange(range);
        long followedCount = customerMapper.selectCount(visibleRootQuery(tenantId, visibility).apply("""
                EXISTS (
                    SELECT 1
                    FROM crm_activity activity
                    WHERE activity.tenant_id = crm_customer.tenant_id
                      AND activity.subject_type = 'CUSTOMER'
                      AND activity.subject_id = crm_customer.id
                      AND activity.kind = 'FOLLOW_UP'
                      AND activity.status = 'ACTIVE'
                      AND activity.occurred_time >= {0}
                      AND activity.occurred_time <= {1}
                )
                """, range.from().atStartOfDay(), range.to().atTime(23, 59, 59, 999_999_000)));
        long newUnfollowedCount = customerMapper.selectCount(visibleRootQuery(tenantId, visibility)
                .ge("created_time", range.from().atStartOfDay())
                .le("created_time", range.to().atTime(23, 59, 59, 999_999_000))
                .apply("""
                        NOT EXISTS (
                            SELECT 1
                            FROM crm_activity activity
                            WHERE activity.tenant_id = crm_customer.tenant_id
                              AND activity.subject_type = 'CUSTOMER'
                              AND activity.subject_id = crm_customer.id
                              AND activity.kind = 'FOLLOW_UP'
                              AND activity.status = 'ACTIVE'
                        )
                        """));
        return new CustomerFollowUpSummary(Math.max(0L, followedCount),
                Math.max(0L, newUnfollowedCount));
    }

    @Override
    public List<CustomerId> findExportCustomerIds(
            TenantId tenantId, CustomerExportQuery query, int limit) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("customer export query must not be null");
        }
        int boundedLimit = requireExportIdLimit(limit);
        QueryWrapper<CustomerPO> wrapper = visibleRootQuery(tenantId, query.readVisibility());
        applyVisibility(wrapper, query.exportVisibility());
        applyKeyword(wrapper, query.keyword(), query.keywordCandidates());
        if (query.name() != null) {
            applyNamePrefix(wrapper, query.name());
        }
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.subordinateOwnerMemberIds() != null) {
            List<Long> ownerIds = query.subordinateOwnerMemberIds().stream()
                    .map(value -> value.getValue()).toList();
            if (ownerIds.isEmpty()) wrapper.apply("1 = 0");
            else wrapper.in("owner_actor_id", ownerIds);
        }
        applyStarred(wrapper, query.readVisibility(), query.starred());
        for (FilterCondition condition : query.conditions()) {
            applyCondition(wrapper, condition);
        }
        return customerMapper.selectList(wrapper.select("id").orderByAsc("id")
                        .last("LIMIT " + boundedLimit))
                .stream().map(value -> new CustomerId(value.getId())).toList();
    }

    @Override
    public List<CustomerExportRow> findExportRows(
            TenantId tenantId, Collection<CustomerId> customerIds,
            List<FieldCode> fieldCodes) {
        requireTenant(tenantId);
        List<Long> ids = ids(customerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_EXPORT_BATCH_LIMIT || fieldCodes == null || fieldCodes.isEmpty()
                || fieldCodes.size() > 50) {
            throw new IllegalArgumentException("customer export batch is invalid");
        }
        List<FieldCode> fields = List.copyOf(fieldCodes);
        if (fields.stream().anyMatch(Objects::isNull) || fields.stream().distinct().count() != fields.size()) {
            throw new IllegalArgumentException("customer export fields are invalid");
        }
        List<CustomerExportRootPO> roots = customerMapper.selectExportRoots(tenantId.getValue(), ids);
        if (roots.size() != ids.size()) {
            throw new IllegalStateException("customer export root projection is incomplete");
        }
        List<String> customCodes = fields.stream()
                .filter(code -> !BUILTIN_EXPORT_VALUES.containsKey(code))
                .map(FieldCode::getValue).toList();
        Map<Long, Map<FieldCode, String>> customValues = customCodes.isEmpty() ? Map.of()
                : customerMapper.selectExportValues(tenantId.getValue(), ids, customCodes).stream()
                .collect(Collectors.groupingBy(CustomerExportValuePO::getCustomerId,
                        LinkedHashMap::new, Collectors.toMap(value -> new FieldCode(value.getFieldCode()),
                                CustomerExportValuePO::getScalarValue, (left, right) -> {
                                    throw new IllegalStateException(
                                            "customer export custom projection is duplicated");
                                }, LinkedHashMap::new)));
        return roots.stream().map(root -> {
            Map<FieldCode, String> custom = customValues.getOrDefault(root.getCustomerId(), Map.of());
            List<CustomerExportCell> cells = fields.stream().map(code -> {
                Function<CustomerExportRootPO, String> builtin = BUILTIN_EXPORT_VALUES.get(code);
                return new CustomerExportCell(code,
                        builtin == null ? custom.get(code) : builtin.apply(root));
            }).toList();
            return new CustomerExportRow(new CustomerId(root.getCustomerId()), cells);
        }).toList();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<CustomerId> ids,
            CustomerVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("customer visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(CustomerId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return customerMapper.selectList(visibleRootQuery(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(CustomerPO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Customer> findVisibleByIds(
            TenantId tenantId, Collection<CustomerId> customerIds,
            CustomerVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        List<Long> ids = ids(customerIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<CustomerPO> query = visibleRootQuery(tenantId, visibility)
                .in("id", ids).orderByAsc("id").last("LIMIT " + boundedLimit);
        return hydrated(tenantId, customerMapper.selectList(query));
    }

    @Override
    public List<Customer> findDescendants(
            TenantId tenantId, CustomerId customerId, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (customerId == null) {
            return List.of();
        }
        List<CustomerPO> descendants = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(customerId.getValue());
        visited.add(customerId.getValue());
        while (!pending.isEmpty() && descendants.size() < boundedLimit) {
            List<Long> parents = new ArrayList<>();
            while (!pending.isEmpty() && parents.size() < 200) {
                parents.add(pending.removeFirst());
            }
            List<CustomerPO> children = customerMapper.selectList(tenantRootFilter(tenantId)
                    .in(CustomerPO::getParentCustomerId, parents)
                    .ne(CustomerPO::getStatus, CustomerStatus.DELETED.name())
                    .orderByAsc(CustomerPO::getId)
                    .last("LIMIT " + (boundedLimit - descendants.size())));
            for (CustomerPO child : children) {
                if (visited.add(child.getId())) {
                    descendants.add(child);
                    pending.addLast(child.getId());
                }
            }
        }
        return hydrated(tenantId, descendants);
    }

    @Override
    public Optional<CustomerHierarchyStats> hierarchyStats(
            TenantId tenantId, CustomerId customerId, int nodeLimit, int depthLimit) {
        requireTenant(tenantId);
        int boundedNodeLimit = requireLimit(nodeLimit);
        if (customerId == null) {
            return Optional.empty();
        }
        if (depthLimit < 1 || depthLimit > MAX_INTERNAL_LIMIT) {
            throw new IllegalArgumentException("customer hierarchy depth limit is invalid");
        }
        List<Long> path = customerMapper.selectHierarchyPath(
                tenantId.getValue(), customerId.getValue(), depthLimit);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        CustomerId rootId = new CustomerId(path.getLast());
        CustomerHierarchyStatsPO subtree = customerMapper.selectHierarchySubtreeStats(
                tenantId.getValue(), customerId.getValue(), boundedNodeLimit, depthLimit);
        CustomerHierarchyStatsPO tree = rootId.sameValueAs(customerId)
                ? subtree : customerMapper.selectHierarchySubtreeStats(
                tenantId.getValue(), rootId.getValue(), boundedNodeLimit, depthLimit);
        return Optional.of(new CustomerHierarchyStats(
                rootId, path.size() - 1,
                subtree.getNodeCount(), subtree.getHeight(),
                tree.getNodeCount(), tree.getHeight()));
    }

    @Override
    public List<Customer> findVisibleDescendants(
            TenantId tenantId, CustomerId customerId,
            CustomerVisibility visibility, int limit) {
        return visibleHierarchyCustomers(tenantId, customerId, visibility, limit, true);
    }

    @Override
    public List<Customer> findVisibleAncestors(
            TenantId tenantId, CustomerId customerId,
            CustomerVisibility visibility, int limit) {
        return visibleHierarchyCustomers(tenantId, customerId, visibility, limit, false);
    }

    @Override
    public boolean existsActiveChild(TenantId tenantId, CustomerId customerId) {
        requireTenant(tenantId);
        return customerId != null && customerMapper.selectCount(tenantRootFilter(tenantId)
                .eq(CustomerPO::getParentCustomerId, customerId.getValue())
                .ne(CustomerPO::getStatus, CustomerStatus.DELETED.name())) > 0;
    }

    @Override
    public boolean existsDuplicate(
            TenantId tenantId, CustomerVisibility visibility, CustomerId excludedCustomerId,
            String name, String phoneNumber, Map<FieldCode, String> uniqueFieldValues) {
        requireTenant(tenantId);
        if (name != null && rootDuplicateCount(tenantId, visibility, excludedCustomerId, "name", name) > 0) {
            return true;
        }
        if (phoneNumber != null
                && rootDuplicateCount(tenantId, visibility, excludedCustomerId, "phone_number", phoneNumber) > 0) {
            return true;
        }
        if (uniqueFieldValues == null || uniqueFieldValues.isEmpty()) {
            return false;
        }
        Map<String, CustomFieldDefinitionPO> definitions = fieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.CUSTOMER.name())
                                .in(CustomFieldDefinitionPO::getFieldCode,
                                        uniqueFieldValues.keySet().stream().map(FieldCode::getValue).toList())
                                .eq(CustomFieldDefinitionPO::getUniqueValue, true))
                .stream().collect(Collectors.toMap(CustomFieldDefinitionPO::getFieldCode,
                        Function.identity()));
        for (Map.Entry<FieldCode, String> entry : uniqueFieldValues.entrySet()) {
            CustomFieldDefinitionPO definition = definitions.get(entry.getKey().getValue());
            if (definition == null) {
                continue;
            }
            LambdaQueryWrapper<CustomerUniqueFieldValuePO> query =
                    new LambdaQueryWrapper<CustomerUniqueFieldValuePO>()
                            .eq(CustomerUniqueFieldValuePO::getTenantId, tenantId.getValue())
                            .eq(CustomerUniqueFieldValuePO::getFieldId, definition.getId())
                            .eq(CustomerUniqueFieldValuePO::getNormalizedHash,
                                    hash(normalizeUnique(entry.getValue())));
            if (excludedCustomerId != null) {
                query.ne(CustomerUniqueFieldValuePO::getCustomerId, excludedCustomerId.getValue());
            }
            List<CustomerId> candidates = uniqueFieldValueMapper.selectList(query).stream()
                    .map(value -> new CustomerId(value.getCustomerId())).toList();
            if (!findVisibleByIds(tenantId, candidates, visibility, MAX_INTERNAL_LIMIT).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> findVisibleTags(
            TenantId tenantId, CustomerVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        return customerMapper.selectVisibleTags(
                tenantId.getValue(), visibility.callerMemberId().getValue(),
                visibility.dataScope().name(), visibility.accessMode().name(),
                departmentValues(visibility), boundedLimit);
    }

    @Override
    public PageResult<CustomerDistance> findNearby(
            TenantId tenantId, CustomerNearbyQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("nearby customer query must not be null");
        }
        CustomerVisibility visibility = query.visibility();
        GeoBoundingBox bounds = GeoBoundingBox.around(
                query.longitude(), query.latitude(), query.radiusMeters());
        IPage<CustomerDistancePO> result = customerMapper.selectNearbyPage(
                new Page<>(query.page(), query.size()), tenantId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility),
                query.longitude(), query.latitude(),
                bounds.minLongitude(), bounds.maxLongitude(),
                bounds.minLatitude(), bounds.maxLatitude(),
                bounds.longitudeUnbounded(), bounds.crossesAntimeridian(),
                query.radiusMeters());
        List<Customer> customers = hydrated(tenantId,
                new ArrayList<CustomerPO>(result.getRecords()));
        Map<Long, Customer> byId = customers.stream().collect(Collectors.toMap(
                customer -> customer.getId().getValue(), Function.identity()));
        List<CustomerDistance> values = result.getRecords().stream()
                .map(po -> new CustomerDistance(byId.get(po.getId()), po.getDistanceMeters()))
                .filter(value -> value.customer() != null)
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()), values);
    }

    private void synchronizeChildren(
            TenantId tenantId, CustomerId customerId, Customer customer,
            Map<FieldCode, FieldDefinition> fieldsByCode) {
        List<CustomerAddressPO> addresses = customer.getAddresses().stream()
                .map(value -> converter.toPO(tenantId, customerId, value)).toList();
        List<CustomerTagPO> tags = customer.getTags().stream()
                .map(value -> converter.toPO(tenantId, customerId, value)).toList();
        List<CustomerCollaboratorPO> collaborators = customer.getCollaborators().stream()
                .map(value -> converter.toPO(tenantId, customerId, value)).toList();
        List<CustomerFieldValuePO> fieldValues = new ArrayList<>();
        List<CustomerUniqueFieldValuePO> uniqueValues = new ArrayList<>();
        for (CustomerFieldValue value : customer.getCustomFieldValues()) {
            FieldDefinition field = fieldsByCode.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("customer field definition is missing");
            }
            fieldValues.add(converter.toPO(tenantId, customerId, value, field));
            if (customer.isActive() && field.isUnique()) {
                uniqueValues.add(uniqueValue(
                        tenantId, customerId, field, value.values().getFirst()));
            }
        }

        List<CustomerAddressPO> storedAddresses = addressMapper.selectList(childFilter(
                tenantId, customerId, CustomerAddressPO::getTenantId,
                CustomerAddressPO::getCustomerId));
        clearPreviousPrimaryAddress(tenantId, customerId, storedAddresses, addresses);
        ChildCollectionSynchronizer.synchronize(storedAddresses, addresses,
                CustomerAddressPO::getAddressKey, this::sameAddress,
                addressMapper::insert,
                stored -> addressMapper.delete(childFilter(tenantId, customerId,
                        CustomerAddressPO::getTenantId, CustomerAddressPO::getCustomerId)
                        .eq(CustomerAddressPO::getAddressKey, stored.getAddressKey())),
                (stored, desired) -> updateAddress(tenantId, customerId, desired));
        ChildCollectionSynchronizer.synchronize(tagMapper.selectList(childFilter(
                        tenantId, customerId, CustomerTagPO::getTenantId,
                        CustomerTagPO::getCustomerId)),
                tags, CustomerTagPO::getTagKey,
                (stored, desired) -> Objects.equals(stored.getTagName(), desired.getTagName()),
                tagMapper::insert,
                stored -> tagMapper.delete(childFilter(tenantId, customerId,
                        CustomerTagPO::getTenantId, CustomerTagPO::getCustomerId)
                        .eq(CustomerTagPO::getTagKey, stored.getTagKey())),
                (stored, desired) -> tagMapper.update(null,
                        new LambdaUpdateWrapper<CustomerTagPO>()
                                .eq(CustomerTagPO::getTenantId, tenantId.getValue())
                                .eq(CustomerTagPO::getCustomerId, customerId.getValue())
                                .eq(CustomerTagPO::getTagKey, desired.getTagKey())
                                .set(CustomerTagPO::getTagName, desired.getTagName())));
        ChildCollectionSynchronizer.synchronize(collaboratorMapper.selectList(childFilter(
                        tenantId, customerId, CustomerCollaboratorPO::getTenantId,
                        CustomerCollaboratorPO::getCustomerId)),
                collaborators, CustomerCollaboratorPO::getActorId,
                (stored, desired) -> Objects.equals(
                        stored.getAccessLevel(), desired.getAccessLevel()),
                collaboratorMapper::insert,
                stored -> collaboratorMapper.delete(childFilter(tenantId, customerId,
                        CustomerCollaboratorPO::getTenantId,
                        CustomerCollaboratorPO::getCustomerId)
                        .eq(CustomerCollaboratorPO::getActorId, stored.getActorId())),
                (stored, desired) -> collaboratorMapper.update(null,
                        new LambdaUpdateWrapper<CustomerCollaboratorPO>()
                                .eq(CustomerCollaboratorPO::getTenantId, tenantId.getValue())
                                .eq(CustomerCollaboratorPO::getCustomerId, customerId.getValue())
                                .eq(CustomerCollaboratorPO::getActorId, desired.getActorId())
                                .set(CustomerCollaboratorPO::getAccessLevel, desired.getAccessLevel())
                                .set(CustomerCollaboratorPO::getUpdatedTime, LocalDateTime.now())));
        ChildCollectionSynchronizer.synchronize(fieldValueMapper.selectList(childFilter(
                        tenantId, customerId, CustomerFieldValuePO::getTenantId,
                        CustomerFieldValuePO::getCustomerId)),
                fieldValues, CustomerFieldValuePO::getFieldId,
                (stored, desired) -> JsonSemanticEquality.equal(
                        objectMapper, stored.getValuesJson(), desired.getValuesJson())
                        && Objects.equals(stored.getScalarValue(), desired.getScalarValue()),
                fieldValueMapper::insert,
                stored -> fieldValueMapper.delete(childFilter(tenantId, customerId,
                        CustomerFieldValuePO::getTenantId, CustomerFieldValuePO::getCustomerId)
                        .eq(CustomerFieldValuePO::getFieldId, stored.getFieldId())),
                (stored, desired) -> fieldValueMapper.update(null,
                        new LambdaUpdateWrapper<CustomerFieldValuePO>()
                                .eq(CustomerFieldValuePO::getTenantId, tenantId.getValue())
                                .eq(CustomerFieldValuePO::getCustomerId, customerId.getValue())
                                .eq(CustomerFieldValuePO::getFieldId, desired.getFieldId())
                                .set(CustomerFieldValuePO::getValuesJson, desired.getValuesJson())
                                .set(CustomerFieldValuePO::getScalarValue, desired.getScalarValue())
                                .set(CustomerFieldValuePO::getUpdatedTime, LocalDateTime.now())));
        ChildCollectionSynchronizer.synchronize(uniqueFieldValueMapper.selectList(childFilter(
                        tenantId, customerId, CustomerUniqueFieldValuePO::getTenantId,
                        CustomerUniqueFieldValuePO::getCustomerId)),
                uniqueValues, CustomerUniqueFieldValuePO::getFieldId,
                (stored, desired) -> Objects.equals(
                        stored.getNormalizedHash(), desired.getNormalizedHash())
                        && Objects.equals(stored.getNormalizedValue(), desired.getNormalizedValue()),
                uniqueFieldValueMapper::insert,
                stored -> deleteUniqueValue(tenantId, customerId, stored.getFieldId()),
                (stored, desired) -> {
                    deleteUniqueValue(tenantId, customerId, stored.getFieldId());
                    uniqueFieldValueMapper.insert(desired);
                });
    }

    private void clearPreviousPrimaryAddress(
            TenantId tenantId, CustomerId customerId,
            List<CustomerAddressPO> stored, List<CustomerAddressPO> desired) {
        String storedPrimary = stored.stream().filter(value -> Boolean.TRUE.equals(
                        value.getPrimaryAddress())).map(CustomerAddressPO::getAddressKey)
                .findFirst().orElse(null);
        String desiredPrimary = desired.stream().filter(value -> Boolean.TRUE.equals(
                        value.getPrimaryAddress())).map(CustomerAddressPO::getAddressKey)
                .findFirst().orElse(null);
        if (storedPrimary != null && !Objects.equals(storedPrimary, desiredPrimary)) {
            addressMapper.update(null, new LambdaUpdateWrapper<CustomerAddressPO>()
                    .eq(CustomerAddressPO::getTenantId, tenantId.getValue())
                    .eq(CustomerAddressPO::getCustomerId, customerId.getValue())
                    .eq(CustomerAddressPO::getAddressKey, storedPrimary)
                    .set(CustomerAddressPO::getPrimaryAddress, false)
                    .set(CustomerAddressPO::getUpdatedTime, LocalDateTime.now()));
        }
    }

    private boolean sameAddress(CustomerAddressPO stored, CustomerAddressPO desired) {
        return Objects.equals(stored.getLabel(), desired.getLabel())
                && Objects.equals(stored.getCountry(), desired.getCountry())
                && Objects.equals(stored.getRegion(), desired.getRegion())
                && Objects.equals(stored.getCity(), desired.getCity())
                && Objects.equals(stored.getPostalCode(), desired.getPostalCode())
                && Objects.equals(stored.getAddressLine(), desired.getAddressLine())
                && sameCoordinate(stored.getLongitude(), desired.getLongitude())
                && sameCoordinate(stored.getLatitude(), desired.getLatitude())
                && Objects.equals(stored.getPrimaryAddress(), desired.getPrimaryAddress());
    }

    private boolean sameCoordinate(java.math.BigDecimal stored, java.math.BigDecimal desired) {
        return stored == null ? desired == null : desired != null && stored.compareTo(desired) == 0;
    }

    private void updateAddress(
            TenantId tenantId, CustomerId customerId, CustomerAddressPO desired) {
        addressMapper.update(null, new LambdaUpdateWrapper<CustomerAddressPO>()
                .eq(CustomerAddressPO::getTenantId, tenantId.getValue())
                .eq(CustomerAddressPO::getCustomerId, customerId.getValue())
                .eq(CustomerAddressPO::getAddressKey, desired.getAddressKey())
                .set(CustomerAddressPO::getLabel, desired.getLabel())
                .set(CustomerAddressPO::getCountry, desired.getCountry())
                .set(CustomerAddressPO::getRegion, desired.getRegion())
                .set(CustomerAddressPO::getCity, desired.getCity())
                .set(CustomerAddressPO::getPostalCode, desired.getPostalCode())
                .set(CustomerAddressPO::getAddressLine, desired.getAddressLine())
                .set(CustomerAddressPO::getLongitude, desired.getLongitude())
                .set(CustomerAddressPO::getLatitude, desired.getLatitude())
                .set(CustomerAddressPO::getPrimaryAddress, desired.getPrimaryAddress())
                .set(CustomerAddressPO::getUpdatedTime, LocalDateTime.now()));
    }

    private void deleteUniqueValue(
            TenantId tenantId, CustomerId customerId, Long fieldId) {
        uniqueFieldValueMapper.delete(childFilter(tenantId, customerId,
                CustomerUniqueFieldValuePO::getTenantId,
                CustomerUniqueFieldValuePO::getCustomerId)
                .eq(CustomerUniqueFieldValuePO::getFieldId, fieldId));
    }

    private <T> LambdaQueryWrapper<T> childFilter(
            TenantId tenantId, CustomerId customerId,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> customerColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .eq(customerColumn, customerId.getValue());
    }

    private CustomerUniqueFieldValuePO uniqueValue(
            TenantId tenantId, CustomerId customerId,
            FieldDefinition field, String value) {
        String normalized = normalizeUnique(value);
        CustomerUniqueFieldValuePO po = new CustomerUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setCustomerId(customerId.getValue());
        po.setObjectType(BusinessObjectType.CUSTOMER.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedHash(hash(normalized));
        po.setNormalizedValue(normalized);
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private List<Customer> hydrated(TenantId tenantId, List<CustomerPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> customerIds = roots.stream().map(CustomerPO::getId).distinct().toList();
        List<CustomerAddressPO> addresses = addressMapper.selectList(
                childListFilter(tenantId, customerIds, CustomerAddressPO::getTenantId,
                        CustomerAddressPO::getCustomerId)
                        .orderByAsc(CustomerAddressPO::getId));
        List<CustomerTagPO> tags = tagMapper.selectList(
                childListFilter(tenantId, customerIds, CustomerTagPO::getTenantId,
                        CustomerTagPO::getCustomerId)
                        .orderByAsc(CustomerTagPO::getTagName));
        List<CustomerCollaboratorPO> collaborators = collaboratorMapper.selectList(
                childListFilter(tenantId, customerIds, CustomerCollaboratorPO::getTenantId,
                        CustomerCollaboratorPO::getCustomerId)
                        .orderByAsc(CustomerCollaboratorPO::getActorId));
        List<CustomerFieldValuePO> values = fieldValueMapper.selectList(
                childListFilter(tenantId, customerIds, CustomerFieldValuePO::getTenantId,
                        CustomerFieldValuePO::getCustomerId)
                        .orderByAsc(CustomerFieldValuePO::getFieldId));
        Set<Long> fieldIds = values.stream().map(CustomerFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fieldsById = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                        .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                        .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.CUSTOMER.name())
                        .in(CustomFieldDefinitionPO::getId, fieldIds))
                .stream().map(fieldDefinitionConverter::toDomain)
                .collect(Collectors.toMap(field -> field.getId().getValue(), Function.identity()));
        Map<Long, List<CustomerAddressPO>> addressMap = group(addresses, CustomerAddressPO::getCustomerId);
        Map<Long, List<CustomerTagPO>> tagMap = group(tags, CustomerTagPO::getCustomerId);
        Map<Long, List<CustomerCollaboratorPO>> collaboratorMap = group(
                collaborators, CustomerCollaboratorPO::getCustomerId);
        Map<Long, List<CustomerFieldValuePO>> valueMap = group(values, CustomerFieldValuePO::getCustomerId);
        return roots.stream().map(po -> converter.toDomain(po,
                addressMap.getOrDefault(po.getId(), List.of()),
                tagMap.getOrDefault(po.getId(), List.of()),
                collaboratorMap.getOrDefault(po.getId(), List.of()),
                valueMap.getOrDefault(po.getId(), List.of()), fieldsById)).toList();
    }

    private <T> LambdaQueryWrapper<T> childListFilter(
            TenantId tenantId, List<Long> customerIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> customerColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .in(customerColumn, customerIds);
    }

    private <T> Map<Long, List<T>> group(List<T> values, Function<T, Long> customerId) {
        return values.stream().collect(Collectors.groupingBy(customerId,
                LinkedHashMap::new, Collectors.toList()));
    }

    private QueryWrapper<CustomerPO> visibleRootQuery(
            TenantId tenantId, CustomerVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("customer visibility must not be null");
        }
        QueryWrapper<CustomerPO> query = new QueryWrapper<CustomerPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", CustomerStatus.ACTIVE.name());
        applyVisibility(query, visibility);
        return query;
    }

    private long countContactOlderThan(
            TenantId tenantId, CustomerVisibility visibility, LocalDateTime cutoff) {
        long count = customerMapper.selectCount(visibleRootQuery(tenantId, visibility).apply("""
                COALESCE((
                    SELECT MAX(activity.occurred_time)
                    FROM crm_activity activity
                    WHERE activity.tenant_id = crm_customer.tenant_id
                      AND activity.subject_type = 'CUSTOMER'
                      AND activity.subject_id = crm_customer.id
                      AND activity.kind = 'FOLLOW_UP'
                      AND activity.status = 'ACTIVE'
                ), crm_customer.created_time) < {0}
                """, cutoff));
        return Math.max(0L, count);
    }

    private long countOverdueContacts(
            TenantId tenantId, CustomerVisibility visibility, LocalDateTime asOf) {
        long count = customerMapper.selectCount(visibleRootQuery(tenantId, visibility).apply("""
                EXISTS (
                    SELECT 1
                    FROM crm_activity activity
                    WHERE activity.tenant_id = crm_customer.tenant_id
                      AND activity.subject_type = 'CUSTOMER'
                      AND activity.subject_id = crm_customer.id
                      AND activity.kind = 'FOLLOW_UP'
                      AND activity.status = 'ACTIVE'
                      AND activity.next_contact_time < {0}
                      AND NOT EXISTS (
                          SELECT 1
                          FROM crm_activity newer
                          WHERE newer.tenant_id = activity.tenant_id
                            AND newer.subject_type = 'CUSTOMER'
                            AND newer.subject_id = activity.subject_id
                            AND newer.kind = 'FOLLOW_UP'
                            AND newer.status = 'ACTIVE'
                            AND (newer.occurred_time > activity.occurred_time
                                OR (newer.occurred_time = activity.occurred_time
                                    AND newer.id > activity.id))
                      )
                )
                """, asOf));
        return Math.max(0L, count);
    }

    private void requireReportRange(ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("customer dashboard range must not be null");
        }
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.CustomerSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("customer custom sort is invalid");
        }
        String scalar = """
                (SELECT customer_field_sort.scalar_value
                FROM crm_customer_field_value customer_field_sort
                WHERE customer_field_sort.tenant_id = crm_customer.tenant_id
                  AND customer_field_sort.customer_id = crm_customer.id
                  AND customer_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private QueryWrapper<CustomerPO> poolRootQuery(TenantId tenantId, CustomerPoolId poolId) {
        return new QueryWrapper<CustomerPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", CustomerStatus.IN_POOL.name())
                .apply("""
                        EXISTS (
                            SELECT 1
                            FROM crm_customer_pool_membership pool_membership
                            WHERE pool_membership.tenant_id = crm_customer.tenant_id
                              AND pool_membership.customer_id = crm_customer.id
                              AND pool_membership.pool_id = {0}
                        )
                        """, poolId.getValue());
    }

    private List<Customer> visibleHierarchyCustomers(
            TenantId tenantId, CustomerId customerId,
            CustomerVisibility visibility, int limit, boolean descendants) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (visibility == null) {
            throw new IllegalArgumentException("customer visibility must not be null");
        }
        if (customerId == null) {
            return List.of();
        }
        List<Long> hierarchyIds = descendants
                ? customerMapper.selectVisibleDescendantIds(
                tenantId.getValue(), customerId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility), boundedLimit,
                MAX_HIERARCHY_DEPTH, MAX_HIERARCHY_TRAVERSAL)
                : customerMapper.selectVisibleAncestorIds(
                tenantId.getValue(), customerId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility), boundedLimit);
        if (hierarchyIds.isEmpty()) {
            return List.of();
        }
        return findVisibleByIds(tenantId, hierarchyIds.stream()
                        .map(CustomerId::new).toList(), visibility, boundedLimit);
    }

    private void applyVisibility(
            QueryWrapper<CustomerPO> query, CustomerVisibility visibility) {
        if (visibility.dataScope() == DataScope.TENANT) {
            return;
        }
        query.and(nested -> {
            if (visibility.dataScope() == DataScope.SELF) {
                nested.eq("owner_actor_id", visibility.callerMemberId().getValue());
            } else {
                List<Long> departments = departmentValues(visibility);
                String placeholders = placeholders(departments.size(), 0);
                List<Object> arguments = new ArrayList<>();
                arguments.addAll(departments);
                nested.apply("""
                        EXISTS (
                            SELECT 1 FROM organization_membership owner_membership
                            WHERE owner_membership.tenant_id = crm_customer.tenant_id
                              AND owner_membership.actor_id = crm_customer.owner_actor_id
                              AND owner_membership.status = 'ACTIVE'
                              AND owner_membership.department_id IN (%s)
                        )
                        """.formatted(placeholders), arguments.toArray());
            }
            if (visibility.accessMode() != CustomerAccessMode.OWNER_SCOPE) {
                String access = visibility.accessMode() == CustomerAccessMode.WRITE
                        ? " AND collaborator.access_level = 'WRITE'" : "";
                nested.or().apply("""
                        EXISTS (
                            SELECT 1 FROM crm_customer_collaborator collaborator
                            WHERE collaborator.tenant_id = crm_customer.tenant_id
                              AND collaborator.customer_id = crm_customer.id
                              AND collaborator.actor_id = {0}%s
                        )
                        """.formatted(access), visibility.callerMemberId().getValue());
            }
        });
    }

    private void applyStarred(
            QueryWrapper<CustomerPO> query, CustomerVisibility visibility, Boolean starred) {
        if (starred == null) {
            return;
        }
        String exists = """
                EXISTS (
                    SELECT 1 FROM crm_customer_star star
                    WHERE star.tenant_id = crm_customer.tenant_id
                      AND star.customer_id = crm_customer.id
                      AND star.actor_id = {0}
                )
                """;
        query.apply(Boolean.TRUE.equals(starred) ? exists : "NOT " + exists,
                visibility.callerMemberId().getValue());
    }

    private void applyNamePrefix(QueryWrapper<CustomerPO> query, String name) {
        query.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", SqlLikePattern.literal(name));
    }

    private void applyKeyword(
            QueryWrapper<CustomerPO> query, String keyword,
            Map<FieldCode, io.github.archetom.crm.domain.model.SearchMatchMode> candidates) {
        if (keyword == null) {
            return;
        }
        if (candidates.isEmpty()) {
            query.apply("1 = 0");
            return;
        }
        String literal = SqlLikePattern.literal(keyword);
        query.and(nested -> {
            boolean previous = false;
            if (candidates.containsKey(new FieldCode(CustomerFieldCatalog.NAME))) {
                nested.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(CustomerFieldCatalog.PHONE_NUMBER))) {
                if (previous) nested.or();
                nested.apply("phone_number LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(CustomerFieldCatalog.TELEPHONE))) {
                if (previous) nested.or();
                nested.apply("telephone LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(CustomerFieldCatalog.EMAIL))) {
                if (previous) nested.or();
                nested.apply("email LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
            }
        });
    }

    private void applyCondition(QueryWrapper<CustomerPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("customer filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            applyBuiltinCondition(query, column, condition);
        } else {
            applyCustomCondition(query, condition);
        }
    }

    private void applyBuiltinCondition(
            QueryWrapper<CustomerPO> query, String column, FilterCondition condition) {
        List<Object> values = condition.values().stream()
                .map(value -> builtinValue(condition.fieldCode(), value)).toList();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.EQ) {
            query.eq(column, values.getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.ne(column, values.getFirst());
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) > 0", values.getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("LOCATE({0}, COALESCE(" + column + ", '')) = 0", values.getFirst());
        } else if (operator == FilterOperator.IS_EMPTY) {
            query.and(value -> value.isNull(column).or().eq(column, ""));
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.isNotNull(column).ne(column, "");
        } else if (operator == FilterOperator.GT) {
            query.gt(column, values.getFirst());
        } else if (operator == FilterOperator.GTE) {
            query.ge(column, values.getFirst());
        } else if (operator == FilterOperator.LT) {
            query.lt(column, values.getFirst());
        } else if (operator == FilterOperator.LTE) {
            query.le(column, values.getFirst());
        } else if (operator == FilterOperator.IN) {
            query.in(column, values);
        } else if (operator == FilterOperator.BETWEEN) {
            query.between(column, values.get(0), values.get(1));
        }
    }

    private void applyCustomCondition(
            QueryWrapper<CustomerPO> query, FilterCondition condition) {
        String base = """
                SELECT 1
                FROM crm_customer_field_value field_value
                JOIN custom_field_definition field_definition
                  ON field_definition.tenant_id = field_value.tenant_id
                 AND field_definition.object_type = field_value.object_type
                 AND field_definition.id = field_value.field_id
                WHERE field_value.tenant_id = crm_customer.tenant_id
                  AND field_value.customer_id = crm_customer.id
                  AND field_definition.field_code = {0}
                """;
        String fieldCode = condition.fieldCode().getValue();
        FilterOperator operator = condition.operator();
        if (operator == FilterOperator.IS_EMPTY) {
            query.apply("NOT EXISTS (" + base + ")", fieldCode);
        } else if (operator == FilterOperator.IS_NOT_EMPTY) {
            query.apply("EXISTS (" + base + ")", fieldCode);
        } else if (operator == FilterOperator.EQ) {
            query.apply("EXISTS (" + base
                            + " AND JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_EQ) {
            query.apply("EXISTS (" + base
                            + " AND NOT JSON_CONTAINS(field_value.values_json, JSON_QUOTE({1})))",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.IN) {
            query.apply("EXISTS (" + base
                            + " AND JSON_OVERLAPS(field_value.values_json, CAST({1} AS JSON)))",
                    fieldCode, json(condition.values()));
        } else if (operator == FilterOperator.CONTAINS) {
            query.apply("EXISTS (" + base
                            + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.NOT_CONTAINS) {
            query.apply("NOT EXISTS (" + base
                            + " AND LOCATE({1}, COALESCE(field_value.scalar_value, '')) > 0)",
                    fieldCode, condition.values().getFirst());
        } else if (operator == FilterOperator.GT || operator == FilterOperator.GTE
                || operator == FilterOperator.LT || operator == FilterOperator.LTE) {
            applyOrderedCustom(query, base, fieldCode, operator, condition.values().getFirst(), 1);
        } else if (operator == FilterOperator.BETWEEN) {
            query.apply("EXISTS (" + base + " AND "
                            + orderedExpression("field_definition.field_type", "field_value.scalar_value", "{1}", ">=")
                            + " AND "
                            + orderedExpression("field_definition.field_type", "field_value.scalar_value", "{2}", "<=")
                            + ")", fieldCode, condition.values().get(0), condition.values().get(1));
        }
    }

    private void applyOrderedCustom(
            QueryWrapper<CustomerPO> query, String base, String fieldCode,
            FilterOperator operator, String value, int parameterIndex) {
        String symbol;
        if (operator == FilterOperator.GT) {
            symbol = ">";
        } else if (operator == FilterOperator.GTE) {
            symbol = ">=";
        } else if (operator == FilterOperator.LT) {
            symbol = "<";
        } else if (operator == FilterOperator.LTE) {
            symbol = "<=";
        } else {
            throw new IllegalArgumentException("ordered operator is invalid");
        }
        query.apply("EXISTS (" + base + " AND "
                        + orderedExpression("field_definition.field_type", "field_value.scalar_value",
                        "{" + parameterIndex + "}", symbol) + ")",
                fieldCode, value);
    }

    private String orderedExpression(
            String typeColumn, String valueColumn, String parameter, String operator) {
        return "((" + typeColumn + " IN ('INTEGER','DECIMAL') AND CAST(" + valueColumn
                + " AS DECIMAL(30,10)) " + operator + " CAST(" + parameter
                + " AS DECIMAL(30,10))) OR (" + typeColumn + " NOT IN ('INTEGER','DECIMAL') AND "
                + valueColumn + " " + operator + " " + parameter + "))";
    }

    private Object builtinValue(FieldCode fieldCode, String value) {
        if (new FieldCode(CustomerFieldCatalog.LOCKED).equals(fieldCode)) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private long rootDuplicateCount(
            TenantId tenantId, CustomerVisibility visibility, CustomerId excludedCustomerId,
            String column, String value) {
        QueryWrapper<CustomerPO> query = visibleRootQuery(tenantId, visibility).eq(column, value);
        if (excludedCustomerId != null) {
            query.ne("id", excludedCustomerId.getValue());
        }
        return customerMapper.selectCount(query);
    }

    private LambdaQueryWrapper<CustomerPO> tenantRootFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<CustomerPO>()
                .eq(CustomerPO::getTenantId, tenantId.getValue());
    }

    private Map<FieldCode, FieldDefinition> definitionsByCode(
            Collection<FieldDefinition> definitions) {
        if (definitions == null) {
            return Map.of();
        }
        return definitions.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private List<Long> ids(Collection<CustomerId> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull)
                .map(CustomerId::getValue).distinct().toList();
    }

    private List<Long> departmentValues(CustomerVisibility visibility) {
        return visibility.ownerDepartmentIds().stream().map(DepartmentId::getValue).sorted().toList();
    }

    private String placeholders(int count, int start) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add("{" + (start + index) + "}");
        }
        return String.join(",", values);
    }

    private int requireLimit(int limit) {
        if (limit < 1 || limit > MAX_INTERNAL_LIMIT) {
            throw new IllegalArgumentException("customer query limit is invalid");
        }
        return limit;
    }

    private int requireExportIdLimit(int limit) {
        if (limit < 1 || limit > MAX_EXPORT_ID_LIMIT) {
            throw new IllegalArgumentException("customer export ID limit is invalid");
        }
        return limit;
    }

    private void requireTenantMatch(TenantId tenantId, Customer customer) {
        requireTenant(tenantId);
        if (customer == null || customer.getTenantId() == null
                || !tenantId.sameValueAs(customer.getTenantId())) {
            throw new IllegalArgumentException("customer tenant does not match repository tenant");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant ID must not be null");
        }
    }

    private String normalizeUnique(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Customer query value cannot be serialized", exception);
        }
    }

    private static Map<FieldCode, String> builtinColumns() {
        Map<FieldCode, String> values = new HashMap<>();
        values.put(new FieldCode(CustomerFieldCatalog.NAME), "name");
        values.put(new FieldCode(CustomerFieldCatalog.PHONE_NUMBER), "phone_number");
        values.put(new FieldCode(CustomerFieldCatalog.TELEPHONE), "telephone");
        values.put(new FieldCode(CustomerFieldCatalog.EMAIL), "email");
        values.put(new FieldCode(CustomerFieldCatalog.WEBSITE), "website");
        values.put(new FieldCode(CustomerFieldCatalog.DESCRIPTION), "description");
        values.put(new FieldCode(CustomerFieldCatalog.OWNER_ACTOR), "owner_actor_id");
        values.put(new FieldCode(CustomerFieldCatalog.PARENT_CUSTOMER), "parent_customer_id");
        values.put(new FieldCode(CustomerFieldCatalog.LOCKED), "is_locked");
        values.put(new FieldCode(CustomerFieldCatalog.DEAL_STATUS), "deal_status");
        values.put(new FieldCode(CustomerFieldCatalog.STATUS), "status");
        values.put(new FieldCode(CustomerFieldCatalog.CREATED_TIME), "created_time");
        values.put(new FieldCode(CustomerFieldCatalog.UPDATED_TIME), "updated_time");
        return Map.copyOf(values);
    }

    private static Map<FieldCode, Function<CustomerExportRootPO, String>> builtinExportValues() {
        Map<FieldCode, Function<CustomerExportRootPO, String>> values = new HashMap<>();
        values.put(new FieldCode(CustomerFieldCatalog.NAME), CustomerExportRootPO::getName);
        values.put(new FieldCode(CustomerFieldCatalog.PHONE_NUMBER), CustomerExportRootPO::getPhoneNumber);
        values.put(new FieldCode(CustomerFieldCatalog.TELEPHONE), CustomerExportRootPO::getTelephone);
        values.put(new FieldCode(CustomerFieldCatalog.EMAIL), CustomerExportRootPO::getEmail);
        values.put(new FieldCode(CustomerFieldCatalog.WEBSITE), CustomerExportRootPO::getWebsite);
        values.put(new FieldCode(CustomerFieldCatalog.DESCRIPTION), CustomerExportRootPO::getDescription);
        values.put(new FieldCode(CustomerFieldCatalog.LOCKED), value -> value.getLocked() == null
                ? null : Boolean.toString(value.getLocked()));
        values.put(new FieldCode(CustomerFieldCatalog.DEAL_STATUS), CustomerExportRootPO::getDealStatus);
        values.put(new FieldCode(CustomerFieldCatalog.STATUS), CustomerExportRootPO::getStatus);
        values.put(new FieldCode(CustomerFieldCatalog.CREATED_TIME), value -> value.getCreatedTime() == null
                ? null : value.getCreatedTime().toString());
        values.put(new FieldCode(CustomerFieldCatalog.UPDATED_TIME), value -> value.getUpdatedTime() == null
                ? null : value.getUpdatedTime().toString());
        return Map.copyOf(values);
    }
}
