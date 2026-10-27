package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.support.ConstraintViolations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ContactAlreadyExistsException;
import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactDistance;
import io.github.archetom.crm.domain.model.ContactExportCell;
import io.github.archetom.crm.domain.model.ContactExportQuery;
import io.github.archetom.crm.domain.model.ContactExportRow;
import io.github.archetom.crm.domain.model.ContactCustomerSummary;
import io.github.archetom.crm.domain.model.ContactHierarchyStats;
import io.github.archetom.crm.domain.model.ContactNearbyQuery;
import io.github.archetom.crm.domain.model.ContactQuery;
import io.github.archetom.crm.domain.model.ContactSortDirection;
import io.github.archetom.crm.domain.model.ContactStatus;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.ContactPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactAddressMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactCollaboratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactTagMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactDistancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactExportRootPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactExportValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactHierarchyStatsPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactTagPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.support.ChildCollectionSynchronizer;
import io.github.archetom.crm.infra.persistence.support.GeoBoundingBox;
import io.github.archetom.crm.infra.persistence.support.JsonSemanticEquality;
import io.github.archetom.crm.infra.persistence.support.SqlLikePattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

/** Contact repository with explicit tenant predicates on every root and child query. */
@Repository
@RequiredArgsConstructor
public class ContactRepositoryImpl implements ContactRepository {

    private static final String CONTRACT_CUSTOMER_FK =
            "fk_crm_contract_contact_customer";
    private static final String RETURN_VISIT_CONTACT_CUSTOMER_FK =
            "fk_crm_return_visit_contact_customer";
    private static final String OPPORTUNITY_CONTACT_CUSTOMER_FK =
            "fk_crm_opportunity_contact_target_customer";
    private static final int MAX_INTERNAL_LIMIT = 2_001;
    private static final int MAX_EXPORT_ID_LIMIT = 5_001;
    private static final int MAX_EXPORT_BATCH_LIMIT = 200;
    private static final int MAX_HIERARCHY_DEPTH = ContactHierarchyStats.MAX_DEPTH;
    private static final int MAX_HIERARCHY_TRAVERSAL = ContactHierarchyStats.MAX_TREE_NODES;
    private static final Map<FieldCode, String> BUILTIN_COLUMNS = builtinColumns();
    private static final Map<FieldCode, Function<ContactExportRootPO, String>>
            BUILTIN_EXPORT_VALUES = builtinExportValues();

    private final ContactMapper contactMapper;
    private final ContactAddressMapper addressMapper;
    private final ContactTagMapper tagMapper;
    private final ContactCollaboratorMapper collaboratorMapper;
    private final ContactFieldValueMapper fieldValueMapper;
    private final ContactUniqueFieldValueMapper uniqueFieldValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final ContactPOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final ObjectMapper objectMapper;

    @Override
    public Contact save(
            TenantId tenantId, Contact contact,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, contact);
        Map<FieldCode, FieldDefinition> fieldsByCode = definitionsByCode(fieldDefinitions);
        ContactPO po = converter.toPO(contact);
        try {
            if (po.getId() == null) {
                if (contactMapper.insert(po) != 1) {
                    throw new IllegalStateException("Contact insert did not affect exactly one row");
                }
            } else {
                LambdaUpdateWrapper<ContactPO> update = new LambdaUpdateWrapper<ContactPO>()
                        .eq(ContactPO::getTenantId, tenantId.getValue())
                        .eq(ContactPO::getId, po.getId());
                if (contactMapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }

            ContactId contactId = new ContactId(po.getId());
            synchronizeChildren(tenantId, contactId, contact, fieldsByCode);
            contact.onPersisted(contactId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return contact;
        } catch (DuplicateKeyException exception) {
            throw new ContactAlreadyExistsException();
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.causeContains(exception, CONTRACT_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, RETURN_VISIT_CONTACT_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, OPPORTUNITY_CONTACT_CUSTOMER_FK)) {
                throw new ContactDomainException(
                        "contact customer cannot change while referenced by a contract, return visit, or opportunity");
            }
            throw exception;
        }
    }


    @Override
    public Optional<Contact> findById(TenantId tenantId, ContactId contactId) {
        requireTenant(tenantId);
        if (contactId == null) {
            return Optional.empty();
        }
        ContactPO po = contactMapper.selectOne(tenantRootFilter(tenantId)
                .eq(ContactPO::getId, contactId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contact> findByIdForAttachmentMutation(TenantId tenantId, ContactId contactId) {
        requireTenant(tenantId);
        if (contactId == null) return Optional.empty();
        ContactPO po = contactMapper.selectOne(tenantRootFilter(tenantId)
                .eq(ContactPO::getId, contactId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contact> findPoolContact(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId) {
        requireTenant(tenantId);
        if (poolId == null || contactId == null) {
            return Optional.empty();
        }
        QueryWrapper<ContactPO> query = poolRootQuery(tenantId, poolId)
                .eq("id", contactId.getValue());
        ContactPO po = contactMapper.selectOne(query);
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contact> findPoolContactForUpdate(
            TenantId tenantId, ContactPoolId poolId, ContactId contactId) {
        requireTenant(tenantId);
        if (poolId == null || contactId == null) return Optional.empty();
        ContactPO po = contactMapper.selectOne(poolRootQuery(tenantId, poolId)
                .eq("id", contactId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public List<Contact> findPoolContactsByIds(
            TenantId tenantId, ContactPoolId poolId,
            Collection<ContactId> contactIds, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (poolId == null) {
            throw new IllegalArgumentException("contact pool ID must not be null");
        }
        List<Long> ids = ids(contactIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ContactPO> query = poolRootQuery(tenantId, poolId)
                .in("id", ids).orderByAsc("id").last("LIMIT " + boundedLimit);
        return hydrated(tenantId, contactMapper.selectList(query));
    }

    @Override
    public PageResult<Contact> findPoolContacts(
            TenantId tenantId, ContactPoolId poolId, String name,
            List<FilterCondition> conditions, int page, int size) {
        requireTenant(tenantId);
        if (poolId == null || page < 1 || size < 1 || size > 200) {
            throw new IllegalArgumentException("contact pool query is invalid");
        }
        QueryWrapper<ContactPO> query = poolRootQuery(tenantId, poolId);
        if (name != null && !name.isBlank()) {
            applyNamePrefix(query, name.trim());
        }
        if (conditions != null) {
            conditions.forEach(condition -> applyCondition(query, condition));
        }
        query.orderByDesc("updated_time").orderByDesc("id");
        IPage<ContactPO> result = contactMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()),
                hydrated(tenantId, result.getRecords()));
    }

    @Override
    public ContactCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ContactVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        long count = contactMapper.selectCount(visibleRootQuery(tenantId, visibility)
                .eq("customer_id", customerId.getValue()));
        return new ContactCustomerSummary(Math.max(0L, count));
    }

    @Override
    public Optional<Contact> findVisibleById(
            TenantId tenantId, ContactId contactId, ContactVisibility visibility) {
        requireTenant(tenantId);
        if (contactId == null) {
            return Optional.empty();
        }
        QueryWrapper<ContactPO> query = visibleRootQuery(tenantId, visibility)
                .eq("id", contactId.getValue());
        ContactPO po = contactMapper.selectOne(query);
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Contact> findVisibleByIdForUpdate(
            TenantId tenantId, ContactId contactId, ContactVisibility visibility) {
        requireTenant(tenantId);
        if (contactId == null) return Optional.empty();
        ContactPO po = contactMapper.selectOne(visibleRootQuery(tenantId, visibility)
                .eq("id", contactId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public PageResult<Contact> findContacts(TenantId tenantId, ContactQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("contact query must not be null");
        }
        QueryWrapper<ContactPO> wrapper = visibleRootQuery(tenantId, query.visibility());
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
        if (query.customerId() != null) {
            wrapper.eq("customer_id", query.customerId().getValue());
        }
        applyStarred(wrapper, query.visibility(), query.starred());
        for (FilterCondition condition : query.conditions()) {
            applyCondition(wrapper, condition);
        }
        boolean ascending = query.sort().direction() == ContactSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<ContactPO> result = contactMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()),
                hydrated(tenantId, result.getRecords()));
    }

    @Override
    public DashboardMetricSummary summarizeCreated(
            TenantId tenantId, ContactVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        requireReportRange(range);
        long count = contactMapper.selectCount(visibleRootQuery(tenantId, visibility)
                .ge("created_time", range.from().atStartOfDay())
                .le("created_time", range.to().atTime(23, 59, 59, 999_999_000)));
        return new DashboardMetricSummary(Math.max(0L, count), null);
    }

    @Override
    public List<ContactId> findExportContactIds(
            TenantId tenantId, ContactExportQuery query, int limit) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("contact export query must not be null");
        }
        int boundedLimit = requireExportIdLimit(limit);
        QueryWrapper<ContactPO> wrapper = visibleRootQuery(tenantId, query.readVisibility());
        applyVisibility(wrapper, query.exportVisibility());
        applyKeyword(wrapper, query.keyword(), query.keywordCandidates());
        if (query.name() != null) {
            applyNamePrefix(wrapper, query.name());
        }
        if (query.ownerMemberId() != null) {
            wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        }
        if (query.customerId() != null) {
            wrapper.eq("customer_id", query.customerId().getValue());
        }
        applyStarred(wrapper, query.readVisibility(), query.starred());
        for (FilterCondition condition : query.conditions()) {
            applyCondition(wrapper, condition);
        }
        return contactMapper.selectList(wrapper.select("id").orderByAsc("id")
                        .last("LIMIT " + boundedLimit))
                .stream().map(value -> new ContactId(value.getId())).toList();
    }

    @Override
    public List<ContactExportRow> findExportRows(
            TenantId tenantId, Collection<ContactId> contactIds,
            List<FieldCode> fieldCodes) {
        requireTenant(tenantId);
        List<Long> ids = ids(contactIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_EXPORT_BATCH_LIMIT || fieldCodes == null || fieldCodes.isEmpty()
                || fieldCodes.size() > 50) {
            throw new IllegalArgumentException("contact export batch is invalid");
        }
        List<FieldCode> fields = List.copyOf(fieldCodes);
        if (fields.stream().anyMatch(Objects::isNull)
                || fields.stream().distinct().count() != fields.size()) {
            throw new IllegalArgumentException("contact export fields are invalid");
        }
        List<ContactExportRootPO> roots = contactMapper.selectExportRoots(tenantId.getValue(), ids);
        if (roots.size() != ids.size()) {
            throw new IllegalStateException("contact export root projection is incomplete");
        }
        List<String> customCodes = fields.stream()
                .filter(code -> !BUILTIN_EXPORT_VALUES.containsKey(code))
                .map(FieldCode::getValue).toList();
        Map<Long, Map<FieldCode, String>> customValues = customCodes.isEmpty() ? Map.of()
                : contactMapper.selectExportValues(tenantId.getValue(), ids, customCodes).stream()
                .collect(Collectors.groupingBy(ContactExportValuePO::getContactId,
                        LinkedHashMap::new, Collectors.toMap(value -> new FieldCode(value.getFieldCode()),
                                ContactExportValuePO::getScalarValue, (left, right) -> {
                                    throw new IllegalStateException(
                                            "contact export custom projection is duplicated");
                                }, LinkedHashMap::new)));
        return roots.stream().map(root -> {
            Map<FieldCode, String> custom = customValues.getOrDefault(root.getContactId(), Map.of());
            List<ContactExportCell> cells = fields.stream().map(code -> {
                Function<ContactExportRootPO, String> builtin = BUILTIN_EXPORT_VALUES.get(code);
                return new ContactExportCell(code,
                        builtin == null ? custom.get(code) : builtin.apply(root));
            }).toList();
            return new ContactExportRow(new ContactId(root.getContactId()), cells);
        }).toList();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<ContactId> ids,
            ContactVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("contact visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(ContactId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return contactMapper.selectList(visibleRootQuery(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(ContactPO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Contact> findVisibleByIds(
            TenantId tenantId, Collection<ContactId> contactIds,
            ContactVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        List<Long> ids = ids(contactIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ContactPO> query = visibleRootQuery(tenantId, visibility)
                .in("id", ids).orderByAsc("id").last("LIMIT " + boundedLimit);
        return hydrated(tenantId, contactMapper.selectList(query));
    }

    @Override
    public List<Contact> findDescendants(
            TenantId tenantId, ContactId contactId, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (contactId == null) {
            return List.of();
        }
        List<ContactPO> descendants = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(contactId.getValue());
        visited.add(contactId.getValue());
        while (!pending.isEmpty() && descendants.size() < boundedLimit) {
            List<Long> parents = new ArrayList<>();
            while (!pending.isEmpty() && parents.size() < 200) {
                parents.add(pending.removeFirst());
            }
            List<ContactPO> children = contactMapper.selectList(tenantRootFilter(tenantId)
                    .in(ContactPO::getParentContactId, parents)
                    .ne(ContactPO::getStatus, ContactStatus.DELETED.name())
                    .orderByAsc(ContactPO::getId)
                    .last("LIMIT " + (boundedLimit - descendants.size())));
            for (ContactPO child : children) {
                if (visited.add(child.getId())) {
                    descendants.add(child);
                    pending.addLast(child.getId());
                }
            }
        }
        return hydrated(tenantId, descendants);
    }

    @Override
    public Optional<ContactHierarchyStats> hierarchyStats(
            TenantId tenantId, ContactId contactId, int nodeLimit, int depthLimit) {
        requireTenant(tenantId);
        int boundedNodeLimit = requireLimit(nodeLimit);
        if (contactId == null) {
            return Optional.empty();
        }
        if (depthLimit < 1 || depthLimit > MAX_INTERNAL_LIMIT) {
            throw new IllegalArgumentException("contact hierarchy depth limit is invalid");
        }
        List<Long> path = contactMapper.selectHierarchyPath(
                tenantId.getValue(), contactId.getValue(), depthLimit);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        ContactId rootId = new ContactId(path.getLast());
        ContactHierarchyStatsPO subtree = contactMapper.selectHierarchySubtreeStats(
                tenantId.getValue(), contactId.getValue(), boundedNodeLimit, depthLimit);
        ContactHierarchyStatsPO tree = rootId.sameValueAs(contactId)
                ? subtree : contactMapper.selectHierarchySubtreeStats(
                tenantId.getValue(), rootId.getValue(), boundedNodeLimit, depthLimit);
        return Optional.of(new ContactHierarchyStats(
                rootId, path.size() - 1,
                subtree.getNodeCount(), subtree.getHeight(),
                tree.getNodeCount(), tree.getHeight()));
    }

    @Override
    public List<Contact> findVisibleDescendants(
            TenantId tenantId, ContactId contactId,
            ContactVisibility visibility, int limit) {
        return visibleHierarchyContacts(tenantId, contactId, visibility, limit, true);
    }

    @Override
    public List<Contact> findVisibleAncestors(
            TenantId tenantId, ContactId contactId,
            ContactVisibility visibility, int limit) {
        return visibleHierarchyContacts(tenantId, contactId, visibility, limit, false);
    }

    @Override
    public boolean existsActiveChild(TenantId tenantId, ContactId contactId) {
        requireTenant(tenantId);
        return contactId != null && contactMapper.selectCount(tenantRootFilter(tenantId)
                .eq(ContactPO::getParentContactId, contactId.getValue())
                .ne(ContactPO::getStatus, ContactStatus.DELETED.name())) > 0;
    }

    @Override
    public boolean existsDuplicate(
            TenantId tenantId, ContactVisibility visibility, ContactId excludedContactId,
            String name, String phoneNumber, Map<FieldCode, String> uniqueFieldValues) {
        requireTenant(tenantId);
        if (name != null && rootDuplicateCount(tenantId, visibility, excludedContactId, "name", name) > 0) {
            return true;
        }
        if (phoneNumber != null
                && rootDuplicateCount(tenantId, visibility, excludedContactId, "phone_number", phoneNumber) > 0) {
            return true;
        }
        if (uniqueFieldValues == null || uniqueFieldValues.isEmpty()) {
            return false;
        }
        Map<String, CustomFieldDefinitionPO> definitions = fieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.CONTACT.name())
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
            LambdaQueryWrapper<ContactUniqueFieldValuePO> query =
                    new LambdaQueryWrapper<ContactUniqueFieldValuePO>()
                            .eq(ContactUniqueFieldValuePO::getTenantId, tenantId.getValue())
                            .eq(ContactUniqueFieldValuePO::getFieldId, definition.getId())
                            .eq(ContactUniqueFieldValuePO::getNormalizedHash,
                                    hash(normalizeUnique(entry.getValue())));
            if (excludedContactId != null) {
                query.ne(ContactUniqueFieldValuePO::getContactId, excludedContactId.getValue());
            }
            List<ContactId> candidates = uniqueFieldValueMapper.selectList(query).stream()
                    .map(value -> new ContactId(value.getContactId())).toList();
            if (!findVisibleByIds(tenantId, candidates, visibility, MAX_INTERNAL_LIMIT).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> findVisibleTags(
            TenantId tenantId, ContactVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        return contactMapper.selectVisibleTags(
                tenantId.getValue(), visibility.callerMemberId().getValue(),
                visibility.dataScope().name(), visibility.accessMode().name(),
                departmentValues(visibility), boundedLimit);
    }

    @Override
    public PageResult<ContactDistance> findNearby(
            TenantId tenantId, ContactNearbyQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("nearby contact query must not be null");
        }
        ContactVisibility visibility = query.visibility();
        GeoBoundingBox bounds = GeoBoundingBox.around(
                query.longitude(), query.latitude(), query.radiusMeters());
        IPage<ContactDistancePO> result = contactMapper.selectNearbyPage(
                new Page<>(query.page(), query.size()), tenantId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility),
                query.longitude(), query.latitude(),
                bounds.minLongitude(), bounds.maxLongitude(),
                bounds.minLatitude(), bounds.maxLatitude(),
                bounds.longitudeUnbounded(), bounds.crossesAntimeridian(),
                query.radiusMeters());
        List<Contact> contacts = hydrated(tenantId,
                new ArrayList<ContactPO>(result.getRecords()));
        Map<Long, Contact> byId = contacts.stream().collect(Collectors.toMap(
                contact -> contact.getId().getValue(), Function.identity()));
        List<ContactDistance> values = result.getRecords().stream()
                .map(po -> new ContactDistance(byId.get(po.getId()), po.getDistanceMeters()))
                .filter(value -> value.contact() != null)
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()), values);
    }

    private void synchronizeChildren(
            TenantId tenantId, ContactId contactId, Contact contact,
            Map<FieldCode, FieldDefinition> fieldsByCode) {
        List<ContactAddressPO> addresses = contact.getAddresses().stream()
                .map(value -> converter.toPO(tenantId, contactId, value)).toList();
        List<ContactTagPO> tags = contact.getTags().stream()
                .map(value -> converter.toPO(tenantId, contactId, value)).toList();
        List<ContactCollaboratorPO> collaborators = contact.getCollaborators().stream()
                .map(value -> converter.toPO(tenantId, contactId, value)).toList();
        List<ContactFieldValuePO> fieldValues = new ArrayList<>();
        List<ContactUniqueFieldValuePO> uniqueValues = new ArrayList<>();
        for (ContactFieldValue value : contact.getCustomFieldValues()) {
            FieldDefinition field = fieldsByCode.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("contact field definition is missing");
            }
            fieldValues.add(converter.toPO(tenantId, contactId, value, field));
            if (contact.isActive() && field.isUnique()) {
                uniqueValues.add(uniqueValue(
                        tenantId, contactId, field, value.values().getFirst()));
            }
        }

        List<ContactAddressPO> storedAddresses = addressMapper.selectList(childFilter(
                tenantId, contactId, ContactAddressPO::getTenantId,
                ContactAddressPO::getContactId));
        clearPreviousPrimaryAddress(tenantId, contactId, storedAddresses, addresses);
        ChildCollectionSynchronizer.synchronize(storedAddresses, addresses,
                ContactAddressPO::getAddressKey, this::sameAddress,
                addressMapper::insert,
                stored -> addressMapper.delete(childFilter(tenantId, contactId,
                        ContactAddressPO::getTenantId, ContactAddressPO::getContactId)
                        .eq(ContactAddressPO::getAddressKey, stored.getAddressKey())),
                (stored, desired) -> updateAddress(tenantId, contactId, desired));
        ChildCollectionSynchronizer.synchronize(tagMapper.selectList(childFilter(
                        tenantId, contactId, ContactTagPO::getTenantId,
                        ContactTagPO::getContactId)),
                tags, ContactTagPO::getTagKey,
                (stored, desired) -> Objects.equals(stored.getTagName(), desired.getTagName()),
                tagMapper::insert,
                stored -> tagMapper.delete(childFilter(tenantId, contactId,
                        ContactTagPO::getTenantId, ContactTagPO::getContactId)
                        .eq(ContactTagPO::getTagKey, stored.getTagKey())),
                (stored, desired) -> tagMapper.update(null,
                        new LambdaUpdateWrapper<ContactTagPO>()
                                .eq(ContactTagPO::getTenantId, tenantId.getValue())
                                .eq(ContactTagPO::getContactId, contactId.getValue())
                                .eq(ContactTagPO::getTagKey, desired.getTagKey())
                                .set(ContactTagPO::getTagName, desired.getTagName())));
        ChildCollectionSynchronizer.synchronize(collaboratorMapper.selectList(childFilter(
                        tenantId, contactId, ContactCollaboratorPO::getTenantId,
                        ContactCollaboratorPO::getContactId)),
                collaborators, ContactCollaboratorPO::getActorId,
                (stored, desired) -> Objects.equals(
                        stored.getAccessLevel(), desired.getAccessLevel()),
                collaboratorMapper::insert,
                stored -> collaboratorMapper.delete(childFilter(tenantId, contactId,
                        ContactCollaboratorPO::getTenantId,
                        ContactCollaboratorPO::getContactId)
                        .eq(ContactCollaboratorPO::getActorId, stored.getActorId())),
                (stored, desired) -> collaboratorMapper.update(null,
                        new LambdaUpdateWrapper<ContactCollaboratorPO>()
                                .eq(ContactCollaboratorPO::getTenantId, tenantId.getValue())
                                .eq(ContactCollaboratorPO::getContactId, contactId.getValue())
                                .eq(ContactCollaboratorPO::getActorId, desired.getActorId())
                                .set(ContactCollaboratorPO::getAccessLevel, desired.getAccessLevel())
                                .set(ContactCollaboratorPO::getUpdatedTime, LocalDateTime.now())));
        ChildCollectionSynchronizer.synchronize(fieldValueMapper.selectList(childFilter(
                        tenantId, contactId, ContactFieldValuePO::getTenantId,
                        ContactFieldValuePO::getContactId)),
                fieldValues, ContactFieldValuePO::getFieldId,
                (stored, desired) -> JsonSemanticEquality.equal(
                        objectMapper, stored.getValuesJson(), desired.getValuesJson())
                        && Objects.equals(stored.getScalarValue(), desired.getScalarValue()),
                fieldValueMapper::insert,
                stored -> fieldValueMapper.delete(childFilter(tenantId, contactId,
                        ContactFieldValuePO::getTenantId, ContactFieldValuePO::getContactId)
                        .eq(ContactFieldValuePO::getFieldId, stored.getFieldId())),
                (stored, desired) -> fieldValueMapper.update(null,
                        new LambdaUpdateWrapper<ContactFieldValuePO>()
                                .eq(ContactFieldValuePO::getTenantId, tenantId.getValue())
                                .eq(ContactFieldValuePO::getContactId, contactId.getValue())
                                .eq(ContactFieldValuePO::getFieldId, desired.getFieldId())
                                .set(ContactFieldValuePO::getValuesJson, desired.getValuesJson())
                                .set(ContactFieldValuePO::getScalarValue, desired.getScalarValue())
                                .set(ContactFieldValuePO::getUpdatedTime, LocalDateTime.now())));
        ChildCollectionSynchronizer.synchronize(uniqueFieldValueMapper.selectList(childFilter(
                        tenantId, contactId, ContactUniqueFieldValuePO::getTenantId,
                        ContactUniqueFieldValuePO::getContactId)),
                uniqueValues, ContactUniqueFieldValuePO::getFieldId,
                (stored, desired) -> Objects.equals(
                        stored.getNormalizedHash(), desired.getNormalizedHash())
                        && Objects.equals(stored.getNormalizedValue(), desired.getNormalizedValue()),
                uniqueFieldValueMapper::insert,
                stored -> deleteUniqueValue(tenantId, contactId, stored.getFieldId()),
                (stored, desired) -> {
                    deleteUniqueValue(tenantId, contactId, stored.getFieldId());
                    uniqueFieldValueMapper.insert(desired);
                });
    }

    private void clearPreviousPrimaryAddress(
            TenantId tenantId, ContactId contactId,
            List<ContactAddressPO> stored, List<ContactAddressPO> desired) {
        String storedPrimary = stored.stream().filter(value -> Boolean.TRUE.equals(
                        value.getPrimaryAddress())).map(ContactAddressPO::getAddressKey)
                .findFirst().orElse(null);
        String desiredPrimary = desired.stream().filter(value -> Boolean.TRUE.equals(
                        value.getPrimaryAddress())).map(ContactAddressPO::getAddressKey)
                .findFirst().orElse(null);
        if (storedPrimary != null && !Objects.equals(storedPrimary, desiredPrimary)) {
            addressMapper.update(null, new LambdaUpdateWrapper<ContactAddressPO>()
                    .eq(ContactAddressPO::getTenantId, tenantId.getValue())
                    .eq(ContactAddressPO::getContactId, contactId.getValue())
                    .eq(ContactAddressPO::getAddressKey, storedPrimary)
                    .set(ContactAddressPO::getPrimaryAddress, false)
                    .set(ContactAddressPO::getUpdatedTime, LocalDateTime.now()));
        }
    }

    private boolean sameAddress(ContactAddressPO stored, ContactAddressPO desired) {
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
            TenantId tenantId, ContactId contactId, ContactAddressPO desired) {
        addressMapper.update(null, new LambdaUpdateWrapper<ContactAddressPO>()
                .eq(ContactAddressPO::getTenantId, tenantId.getValue())
                .eq(ContactAddressPO::getContactId, contactId.getValue())
                .eq(ContactAddressPO::getAddressKey, desired.getAddressKey())
                .set(ContactAddressPO::getLabel, desired.getLabel())
                .set(ContactAddressPO::getCountry, desired.getCountry())
                .set(ContactAddressPO::getRegion, desired.getRegion())
                .set(ContactAddressPO::getCity, desired.getCity())
                .set(ContactAddressPO::getPostalCode, desired.getPostalCode())
                .set(ContactAddressPO::getAddressLine, desired.getAddressLine())
                .set(ContactAddressPO::getLongitude, desired.getLongitude())
                .set(ContactAddressPO::getLatitude, desired.getLatitude())
                .set(ContactAddressPO::getPrimaryAddress, desired.getPrimaryAddress())
                .set(ContactAddressPO::getUpdatedTime, LocalDateTime.now()));
    }

    private void deleteUniqueValue(
            TenantId tenantId, ContactId contactId, Long fieldId) {
        uniqueFieldValueMapper.delete(childFilter(tenantId, contactId,
                ContactUniqueFieldValuePO::getTenantId,
                ContactUniqueFieldValuePO::getContactId)
                .eq(ContactUniqueFieldValuePO::getFieldId, fieldId));
    }

    private <T> LambdaQueryWrapper<T> childFilter(
            TenantId tenantId, ContactId contactId,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> contactColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .eq(contactColumn, contactId.getValue());
    }

    private ContactUniqueFieldValuePO uniqueValue(
            TenantId tenantId, ContactId contactId,
            FieldDefinition field, String value) {
        String normalized = normalizeUnique(value);
        ContactUniqueFieldValuePO po = new ContactUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setContactId(contactId.getValue());
        po.setObjectType(BusinessObjectType.CONTACT.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedHash(hash(normalized));
        po.setNormalizedValue(normalized);
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private List<Contact> hydrated(TenantId tenantId, List<ContactPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> contactIds = roots.stream().map(ContactPO::getId).distinct().toList();
        List<ContactAddressPO> addresses = addressMapper.selectList(
                childListFilter(tenantId, contactIds, ContactAddressPO::getTenantId,
                        ContactAddressPO::getContactId)
                        .orderByAsc(ContactAddressPO::getId));
        List<ContactTagPO> tags = tagMapper.selectList(
                childListFilter(tenantId, contactIds, ContactTagPO::getTenantId,
                        ContactTagPO::getContactId)
                        .orderByAsc(ContactTagPO::getTagName));
        List<ContactCollaboratorPO> collaborators = collaboratorMapper.selectList(
                childListFilter(tenantId, contactIds, ContactCollaboratorPO::getTenantId,
                        ContactCollaboratorPO::getContactId)
                        .orderByAsc(ContactCollaboratorPO::getActorId));
        List<ContactFieldValuePO> values = fieldValueMapper.selectList(
                childListFilter(tenantId, contactIds, ContactFieldValuePO::getTenantId,
                        ContactFieldValuePO::getContactId)
                        .orderByAsc(ContactFieldValuePO::getFieldId));
        Set<Long> fieldIds = values.stream().map(ContactFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fieldsById = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                        .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                        .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.CONTACT.name())
                        .in(CustomFieldDefinitionPO::getId, fieldIds))
                .stream().map(fieldDefinitionConverter::toDomain)
                .collect(Collectors.toMap(field -> field.getId().getValue(), Function.identity()));
        Map<Long, List<ContactAddressPO>> addressMap = group(addresses, ContactAddressPO::getContactId);
        Map<Long, List<ContactTagPO>> tagMap = group(tags, ContactTagPO::getContactId);
        Map<Long, List<ContactCollaboratorPO>> collaboratorMap = group(
                collaborators, ContactCollaboratorPO::getContactId);
        Map<Long, List<ContactFieldValuePO>> valueMap = group(values, ContactFieldValuePO::getContactId);
        return roots.stream().map(po -> converter.toDomain(po,
                addressMap.getOrDefault(po.getId(), List.of()),
                tagMap.getOrDefault(po.getId(), List.of()),
                collaboratorMap.getOrDefault(po.getId(), List.of()),
                valueMap.getOrDefault(po.getId(), List.of()), fieldsById)).toList();
    }

    private <T> LambdaQueryWrapper<T> childListFilter(
            TenantId tenantId, List<Long> contactIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> contactColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .in(contactColumn, contactIds);
    }

    private <T> Map<Long, List<T>> group(List<T> values, Function<T, Long> contactId) {
        return values.stream().collect(Collectors.groupingBy(contactId,
                LinkedHashMap::new, Collectors.toList()));
    }

    private QueryWrapper<ContactPO> visibleRootQuery(
            TenantId tenantId, ContactVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("contact visibility must not be null");
        }
        QueryWrapper<ContactPO> query = new QueryWrapper<ContactPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", ContactStatus.ACTIVE.name());
        applyVisibility(query, visibility);
        return query;
    }

    private void requireReportRange(ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("contact dashboard range must not be null");
        }
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.ContactSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("contact custom sort is invalid");
        }
        String scalar = """
                (SELECT contact_field_sort.scalar_value
                FROM crm_contact_field_value contact_field_sort
                WHERE contact_field_sort.tenant_id = crm_contact.tenant_id
                  AND contact_field_sort.contact_id = crm_contact.id
                  AND contact_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private QueryWrapper<ContactPO> poolRootQuery(TenantId tenantId, ContactPoolId poolId) {
        return new QueryWrapper<ContactPO>()
                .eq("tenant_id", tenantId.getValue())
                .eq("status", ContactStatus.IN_POOL.name())
                .apply("""
                        EXISTS (
                            SELECT 1
                            FROM crm_contact_pool_membership pool_membership
                            WHERE pool_membership.tenant_id = crm_contact.tenant_id
                              AND pool_membership.contact_id = crm_contact.id
                              AND pool_membership.pool_id = {0}
                        )
                        """, poolId.getValue());
    }

    private List<Contact> visibleHierarchyContacts(
            TenantId tenantId, ContactId contactId,
            ContactVisibility visibility, int limit, boolean descendants) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (visibility == null) {
            throw new IllegalArgumentException("contact visibility must not be null");
        }
        if (contactId == null) {
            return List.of();
        }
        List<Long> hierarchyIds = descendants
                ? contactMapper.selectVisibleDescendantIds(
                tenantId.getValue(), contactId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility), boundedLimit,
                MAX_HIERARCHY_DEPTH, MAX_HIERARCHY_TRAVERSAL)
                : contactMapper.selectVisibleAncestorIds(
                tenantId.getValue(), contactId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility), boundedLimit);
        if (hierarchyIds.isEmpty()) {
            return List.of();
        }
        return findVisibleByIds(tenantId, hierarchyIds.stream()
                        .map(ContactId::new).toList(), visibility, boundedLimit);
    }

    private void applyVisibility(
            QueryWrapper<ContactPO> query, ContactVisibility visibility) {
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
                            WHERE owner_membership.tenant_id = crm_contact.tenant_id
                              AND owner_membership.actor_id = crm_contact.owner_actor_id
                              AND owner_membership.status = 'ACTIVE'
                              AND owner_membership.department_id IN (%s)
                        )
                        """.formatted(placeholders), arguments.toArray());
            }
            if (visibility.accessMode() != ContactAccessMode.OWNER_SCOPE) {
                String access = visibility.accessMode() == ContactAccessMode.WRITE
                        ? " AND collaborator.access_level = 'WRITE'" : "";
                nested.or().apply("""
                        EXISTS (
                            SELECT 1 FROM crm_contact_collaborator collaborator
                            WHERE collaborator.tenant_id = crm_contact.tenant_id
                              AND collaborator.contact_id = crm_contact.id
                              AND collaborator.actor_id = {0}%s
                        )
                        """.formatted(access), visibility.callerMemberId().getValue());
            }
        });
    }

    private void applyStarred(
            QueryWrapper<ContactPO> query, ContactVisibility visibility, Boolean starred) {
        if (starred == null) {
            return;
        }
        String exists = """
                EXISTS (
                    SELECT 1 FROM crm_contact_star star
                    WHERE star.tenant_id = crm_contact.tenant_id
                      AND star.contact_id = crm_contact.id
                      AND star.actor_id = {0}
                )
                """;
        query.apply(Boolean.TRUE.equals(starred) ? exists : "NOT " + exists,
                visibility.callerMemberId().getValue());
    }

    private void applyNamePrefix(QueryWrapper<ContactPO> query, String name) {
        query.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", SqlLikePattern.literal(name));
    }

    private void applyKeyword(
            QueryWrapper<ContactPO> query, String keyword,
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
            if (candidates.containsKey(new FieldCode(ContactFieldCatalog.NAME))) {
                nested.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(ContactFieldCatalog.PHONE_NUMBER))) {
                if (previous) nested.or();
                nested.apply("phone_number LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(ContactFieldCatalog.TELEPHONE))) {
                if (previous) nested.or();
                nested.apply("telephone LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(ContactFieldCatalog.EMAIL))) {
                if (previous) nested.or();
                nested.apply("email LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
            }
        });
    }

    private void applyCondition(QueryWrapper<ContactPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("contact filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            applyBuiltinCondition(query, column, condition);
        } else {
            applyCustomCondition(query, condition);
        }
    }

    private void applyBuiltinCondition(
            QueryWrapper<ContactPO> query, String column, FilterCondition condition) {
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
            QueryWrapper<ContactPO> query, FilterCondition condition) {
        String base = """
                SELECT 1
                FROM crm_contact_field_value field_value
                JOIN custom_field_definition field_definition
                  ON field_definition.tenant_id = field_value.tenant_id
                 AND field_definition.object_type = field_value.object_type
                 AND field_definition.id = field_value.field_id
                WHERE field_value.tenant_id = crm_contact.tenant_id
                  AND field_value.contact_id = crm_contact.id
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
            QueryWrapper<ContactPO> query, String base, String fieldCode,
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
        if (new FieldCode(ContactFieldCatalog.LOCKED).equals(fieldCode)) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private long rootDuplicateCount(
            TenantId tenantId, ContactVisibility visibility, ContactId excludedContactId,
            String column, String value) {
        QueryWrapper<ContactPO> query = visibleRootQuery(tenantId, visibility).eq(column, value);
        if (excludedContactId != null) {
            query.ne("id", excludedContactId.getValue());
        }
        return contactMapper.selectCount(query);
    }

    private LambdaQueryWrapper<ContactPO> tenantRootFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<ContactPO>()
                .eq(ContactPO::getTenantId, tenantId.getValue());
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

    private List<Long> ids(Collection<ContactId> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull)
                .map(ContactId::getValue).distinct().toList();
    }

    private List<Long> departmentValues(ContactVisibility visibility) {
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
            throw new IllegalArgumentException("contact query limit is invalid");
        }
        return limit;
    }

    private int requireExportIdLimit(int limit) {
        if (limit < 1 || limit > MAX_EXPORT_ID_LIMIT) {
            throw new IllegalArgumentException("contact export ID limit is invalid");
        }
        return limit;
    }

    private void requireTenantMatch(TenantId tenantId, Contact contact) {
        requireTenant(tenantId);
        if (contact == null || contact.getTenantId() == null
                || !tenantId.sameValueAs(contact.getTenantId())) {
            throw new IllegalArgumentException("contact tenant does not match repository tenant");
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
            throw new IllegalStateException("Contact query value cannot be serialized", exception);
        }
    }

    private static Map<FieldCode, String> builtinColumns() {
        Map<FieldCode, String> values = new HashMap<>();
        values.put(new FieldCode(ContactFieldCatalog.NAME), "name");
        values.put(new FieldCode(ContactFieldCatalog.PHONE_NUMBER), "phone_number");
        values.put(new FieldCode(ContactFieldCatalog.TELEPHONE), "telephone");
        values.put(new FieldCode(ContactFieldCatalog.JOB_TITLE), "job_title");
        values.put(new FieldCode(ContactFieldCatalog.EMAIL), "email");
        values.put(new FieldCode(ContactFieldCatalog.WEBSITE), "website");
        values.put(new FieldCode(ContactFieldCatalog.DESCRIPTION), "description");
        values.put(new FieldCode(ContactFieldCatalog.NEXT_CONTACT_TIME), "next_contact_time");
        values.put(new FieldCode(ContactFieldCatalog.OWNER_ACTOR), "owner_actor_id");
        values.put(new FieldCode(ContactFieldCatalog.CUSTOMER), "customer_id");
        values.put(new FieldCode(ContactFieldCatalog.PARENT_CONTACT), "parent_contact_id");
        values.put(new FieldCode(ContactFieldCatalog.LOCKED), "is_locked");
        values.put(new FieldCode(ContactFieldCatalog.DEAL_STATUS), "deal_status");
        values.put(new FieldCode(ContactFieldCatalog.STATUS), "status");
        values.put(new FieldCode(ContactFieldCatalog.CREATED_TIME), "created_time");
        values.put(new FieldCode(ContactFieldCatalog.UPDATED_TIME), "updated_time");
        return Map.copyOf(values);
    }

    private static Map<FieldCode, Function<ContactExportRootPO, String>> builtinExportValues() {
        Map<FieldCode, Function<ContactExportRootPO, String>> values = new HashMap<>();
        values.put(new FieldCode(ContactFieldCatalog.NAME), ContactExportRootPO::getName);
        values.put(new FieldCode(ContactFieldCatalog.PHONE_NUMBER), ContactExportRootPO::getPhoneNumber);
        values.put(new FieldCode(ContactFieldCatalog.TELEPHONE), ContactExportRootPO::getTelephone);
        values.put(new FieldCode(ContactFieldCatalog.JOB_TITLE), ContactExportRootPO::getJobTitle);
        values.put(new FieldCode(ContactFieldCatalog.EMAIL), ContactExportRootPO::getEmail);
        values.put(new FieldCode(ContactFieldCatalog.WEBSITE), ContactExportRootPO::getWebsite);
        values.put(new FieldCode(ContactFieldCatalog.DESCRIPTION), ContactExportRootPO::getDescription);
        values.put(new FieldCode(ContactFieldCatalog.NEXT_CONTACT_TIME), value ->
                value.getNextContactTime() == null ? null : value.getNextContactTime().toString());
        values.put(new FieldCode(ContactFieldCatalog.LOCKED), value -> value.getLocked() == null
                ? null : Boolean.toString(value.getLocked()));
        values.put(new FieldCode(ContactFieldCatalog.DEAL_STATUS), ContactExportRootPO::getDealStatus);
        values.put(new FieldCode(ContactFieldCatalog.STATUS), ContactExportRootPO::getStatus);
        values.put(new FieldCode(ContactFieldCatalog.CREATED_TIME), value -> value.getCreatedTime() == null
                ? null : value.getCreatedTime().toString());
        values.put(new FieldCode(ContactFieldCatalog.UPDATED_TIME), value -> value.getUpdatedTime() == null
                ? null : value.getUpdatedTime().toString());
        return Map.copyOf(values);
    }
}
