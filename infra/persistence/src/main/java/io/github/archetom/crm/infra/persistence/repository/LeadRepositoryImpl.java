package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.LeadAlreadyExistsException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadDistance;
import io.github.archetom.crm.domain.model.LeadExportCell;
import io.github.archetom.crm.domain.model.LeadExportQuery;
import io.github.archetom.crm.domain.model.LeadExportRow;
import io.github.archetom.crm.domain.model.LeadHierarchyStats;
import io.github.archetom.crm.domain.model.LeadNearbyQuery;
import io.github.archetom.crm.domain.model.LeadQuery;
import io.github.archetom.crm.domain.model.LeadSortDirection;
import io.github.archetom.crm.domain.model.LeadStatus;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.DashboardMetricSummary;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReportDateRange;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.LeadPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadAddressMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadCollaboratorMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadTagMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadAddressPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadCollaboratorPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadDistancePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadExportRootPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadExportValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadHierarchyStatsPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadTagPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadUniqueFieldValuePO;
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

/** Lead repository with explicit tenant predicates on every root and child query. */
@Repository
@RequiredArgsConstructor
public class LeadRepositoryImpl implements LeadRepository {

    private static final int MAX_INTERNAL_LIMIT = 2_001;
    private static final int MAX_EXPORT_ID_LIMIT = 5_001;
    private static final int MAX_EXPORT_BATCH_LIMIT = 200;
    private static final int MAX_HIERARCHY_DEPTH = LeadHierarchyStats.MAX_DEPTH;
    private static final int MAX_HIERARCHY_TRAVERSAL = LeadHierarchyStats.MAX_TREE_NODES;
    private static final Map<FieldCode, String> BUILTIN_COLUMNS = builtinColumns();
    private static final Map<FieldCode, Function<LeadExportRootPO, String>>
            BUILTIN_EXPORT_VALUES = builtinExportValues();

    private final LeadMapper leadMapper;
    private final LeadAddressMapper addressMapper;
    private final LeadTagMapper tagMapper;
    private final LeadCollaboratorMapper collaboratorMapper;
    private final LeadFieldValueMapper fieldValueMapper;
    private final LeadUniqueFieldValueMapper uniqueFieldValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final LeadPOConverter converter;
    private final CustomFieldDefinitionPOConverter fieldDefinitionConverter;
    private final ObjectMapper objectMapper;

    @Override
    public Lead save(
            TenantId tenantId, Lead lead,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, lead);
        Map<FieldCode, FieldDefinition> fieldsByCode = definitionsByCode(fieldDefinitions);
        LeadPO po = converter.toPO(lead);
        try {
            if (po.getId() == null) {
                if (leadMapper.insert(po) != 1) {
                    throw new IllegalStateException("Lead insert did not affect exactly one row");
                }
            } else {
                LambdaUpdateWrapper<LeadPO> update = new LambdaUpdateWrapper<LeadPO>()
                        .eq(LeadPO::getTenantId, tenantId.getValue())
                        .eq(LeadPO::getId, po.getId());
                if (leadMapper.update(po, update) != 1) {
                    throw new AggregateVersionConflictException();
                }
            }

            LeadId leadId = new LeadId(po.getId());
            synchronizeChildren(tenantId, leadId, lead, fieldsByCode);
            lead.onPersisted(leadId, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return lead;
        } catch (DuplicateKeyException exception) {
            throw new LeadAlreadyExistsException();
        }
    }

    @Override
    public Optional<Lead> findById(TenantId tenantId, LeadId leadId) {
        requireTenant(tenantId);
        if (leadId == null) {
            return Optional.empty();
        }
        LeadPO po = leadMapper.selectOne(tenantRootFilter(tenantId)
                .eq(LeadPO::getId, leadId.getValue()));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Lead> findByIdForAttachmentMutation(TenantId tenantId, LeadId leadId) {
        requireTenant(tenantId);
        if (leadId == null) return Optional.empty();
        LeadPO po = leadMapper.selectOne(tenantRootFilter(tenantId)
                .eq(LeadPO::getId, leadId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Lead> findVisibleById(
            TenantId tenantId, LeadId leadId, LeadVisibility visibility) {
        requireTenant(tenantId);
        if (leadId == null) {
            return Optional.empty();
        }
        QueryWrapper<LeadPO> query = visibleRootQuery(tenantId, visibility)
                .eq("id", leadId.getValue());
        LeadPO po = leadMapper.selectOne(query);
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public Optional<Lead> findVisibleByIdForUpdate(
            TenantId tenantId, LeadId leadId, LeadVisibility visibility) {
        requireTenant(tenantId);
        if (leadId == null) return Optional.empty();
        LeadPO po = leadMapper.selectOne(visibleRootQuery(tenantId, visibility)
                .eq("id", leadId.getValue()).last("FOR UPDATE"));
        return hydrated(tenantId, po == null ? List.of() : List.of(po)).stream().findFirst();
    }

    @Override
    public PageResult<Lead> findLeads(TenantId tenantId, LeadQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("lead query must not be null");
        }
        QueryWrapper<LeadPO> wrapper = visibleRootQuery(tenantId, query.visibility());
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
        if (query.converted() != null) {
            wrapper.eq("status", query.converted()
                    ? LeadStatus.CONVERTED.name() : LeadStatus.ACTIVE.name());
        }
        for (FilterCondition condition : query.conditions()) {
            applyCondition(wrapper, condition);
        }
        boolean ascending = query.sort().direction() == LeadSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<LeadPO> result = leadMapper.selectPage(
                new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()),
                hydrated(tenantId, result.getRecords()));
    }

    @Override
    public DashboardMetricSummary summarizeCreated(
            TenantId tenantId, LeadVisibility visibility, ReportDateRange range) {
        requireTenant(tenantId);
        requireReportRange(range);
        long count = leadMapper.selectCount(visibleRootQuery(tenantId, visibility)
                .ge("created_time", range.from().atStartOfDay())
                .le("created_time", range.to().atTime(23, 59, 59, 999_999_000)));
        return new DashboardMetricSummary(Math.max(0L, count), null);
    }

    @Override
    public List<LeadId> findExportLeadIds(
            TenantId tenantId, LeadExportQuery query, int limit) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("lead export query must not be null");
        }
        int boundedLimit = requireExportIdLimit(limit);
        QueryWrapper<LeadPO> wrapper = visibleRootQuery(tenantId, query.readVisibility());
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
        if (query.converted() != null) {
            wrapper.eq("status", query.converted()
                    ? LeadStatus.CONVERTED.name() : LeadStatus.ACTIVE.name());
        }
        for (FilterCondition condition : query.conditions()) {
            applyCondition(wrapper, condition);
        }
        return leadMapper.selectList(wrapper.select("id").orderByAsc("id")
                        .last("LIMIT " + boundedLimit))
                .stream().map(value -> new LeadId(value.getId())).toList();
    }

    @Override
    public List<LeadExportRow> findExportRows(
            TenantId tenantId, Collection<LeadId> leadIds,
            List<FieldCode> fieldCodes) {
        requireTenant(tenantId);
        List<Long> ids = ids(leadIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_EXPORT_BATCH_LIMIT || fieldCodes == null || fieldCodes.isEmpty()
                || fieldCodes.size() > 50) {
            throw new IllegalArgumentException("lead export batch is invalid");
        }
        List<FieldCode> fields = List.copyOf(fieldCodes);
        if (fields.stream().anyMatch(Objects::isNull)
                || fields.stream().distinct().count() != fields.size()) {
            throw new IllegalArgumentException("lead export fields are invalid");
        }
        List<LeadExportRootPO> roots = leadMapper.selectExportRoots(tenantId.getValue(), ids);
        if (roots.size() != ids.size()) {
            throw new IllegalStateException("lead export root projection is incomplete");
        }
        List<String> customCodes = fields.stream()
                .filter(code -> !BUILTIN_EXPORT_VALUES.containsKey(code))
                .map(FieldCode::getValue).toList();
        Map<Long, Map<FieldCode, String>> customValues = customCodes.isEmpty() ? Map.of()
                : leadMapper.selectExportValues(tenantId.getValue(), ids, customCodes).stream()
                .collect(Collectors.groupingBy(LeadExportValuePO::getLeadId,
                        LinkedHashMap::new, Collectors.toMap(value -> new FieldCode(value.getFieldCode()),
                                LeadExportValuePO::getScalarValue, (left, right) -> {
                                    throw new IllegalStateException(
                                            "lead export custom projection is duplicated");
                                }, LinkedHashMap::new)));
        return roots.stream().map(root -> {
            Map<FieldCode, String> custom = customValues.getOrDefault(root.getLeadId(), Map.of());
            List<LeadExportCell> cells = fields.stream().map(code -> {
                Function<LeadExportRootPO, String> builtin = BUILTIN_EXPORT_VALUES.get(code);
                return new LeadExportCell(code,
                        builtin == null ? custom.get(code) : builtin.apply(root));
            }).toList();
            return new LeadExportRow(new LeadId(root.getLeadId()), cells);
        }).toList();
    }

    @Override
    public Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<LeadId> ids,
            LeadVisibility visibility, int limit) {
        requireTenant(tenantId);
        if (ids == null || limit < 1 || limit > 2_000) {
            throw new IllegalArgumentException("lead visibility batch is invalid");
        }
        List<Long> values = ids.stream().filter(java.util.Objects::nonNull)
                .map(LeadId::getValue).distinct().toList();
        if (values.isEmpty()) {
            return Set.of();
        }
        // Same predicate as findVisibleByIds, projecting the id column only.
        return leadMapper.selectList(visibleRootQuery(tenantId, visibility).select("id")
                        .in("id", values).orderByAsc("id").last("LIMIT " + limit))
                .stream().map(LeadPO::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @Override
    public List<Lead> findVisibleByIds(
            TenantId tenantId, Collection<LeadId> leadIds,
            LeadVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        List<Long> ids = ids(leadIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<LeadPO> query = visibleRootQuery(tenantId, visibility)
                .in("id", ids).orderByAsc("id").last("LIMIT " + boundedLimit);
        return hydrated(tenantId, leadMapper.selectList(query));
    }

    @Override
    public List<Lead> findDescendants(
            TenantId tenantId, LeadId leadId, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (leadId == null) {
            return List.of();
        }
        List<LeadPO> descendants = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(leadId.getValue());
        visited.add(leadId.getValue());
        while (!pending.isEmpty() && descendants.size() < boundedLimit) {
            List<Long> parents = new ArrayList<>();
            while (!pending.isEmpty() && parents.size() < 200) {
                parents.add(pending.removeFirst());
            }
            List<LeadPO> children = leadMapper.selectList(tenantRootFilter(tenantId)
                    .in(LeadPO::getParentLeadId, parents)
                    .ne(LeadPO::getStatus, LeadStatus.DELETED.name())
                    .orderByAsc(LeadPO::getId)
                    .last("LIMIT " + (boundedLimit - descendants.size())));
            for (LeadPO child : children) {
                if (visited.add(child.getId())) {
                    descendants.add(child);
                    pending.addLast(child.getId());
                }
            }
        }
        return hydrated(tenantId, descendants);
    }

    @Override
    public Optional<LeadHierarchyStats> hierarchyStats(
            TenantId tenantId, LeadId leadId, int nodeLimit, int depthLimit) {
        requireTenant(tenantId);
        int boundedNodeLimit = requireLimit(nodeLimit);
        if (leadId == null) {
            return Optional.empty();
        }
        if (depthLimit < 1 || depthLimit > MAX_INTERNAL_LIMIT) {
            throw new IllegalArgumentException("lead hierarchy depth limit is invalid");
        }
        List<Long> path = leadMapper.selectHierarchyPath(
                tenantId.getValue(), leadId.getValue(), depthLimit);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        LeadId rootId = new LeadId(path.getLast());
        LeadHierarchyStatsPO subtree = leadMapper.selectHierarchySubtreeStats(
                tenantId.getValue(), leadId.getValue(), boundedNodeLimit, depthLimit);
        LeadHierarchyStatsPO tree = rootId.sameValueAs(leadId)
                ? subtree : leadMapper.selectHierarchySubtreeStats(
                tenantId.getValue(), rootId.getValue(), boundedNodeLimit, depthLimit);
        return Optional.of(new LeadHierarchyStats(
                rootId, path.size() - 1,
                subtree.getNodeCount(), subtree.getHeight(),
                tree.getNodeCount(), tree.getHeight()));
    }

    @Override
    public List<Lead> findVisibleDescendants(
            TenantId tenantId, LeadId leadId,
            LeadVisibility visibility, int limit) {
        return visibleHierarchyLeads(tenantId, leadId, visibility, limit, true);
    }

    @Override
    public List<Lead> findVisibleAncestors(
            TenantId tenantId, LeadId leadId,
            LeadVisibility visibility, int limit) {
        return visibleHierarchyLeads(tenantId, leadId, visibility, limit, false);
    }

    @Override
    public boolean existsActiveChild(TenantId tenantId, LeadId leadId) {
        requireTenant(tenantId);
        return leadId != null && leadMapper.selectCount(tenantRootFilter(tenantId)
                .eq(LeadPO::getParentLeadId, leadId.getValue())
                .ne(LeadPO::getStatus, LeadStatus.DELETED.name())) > 0;
    }

    @Override
    public boolean existsDuplicate(
            TenantId tenantId, LeadVisibility visibility, LeadId excludedLeadId,
            String name, String phoneNumber, Map<FieldCode, String> uniqueFieldValues) {
        requireTenant(tenantId);
        if (name != null && rootDuplicateCount(tenantId, visibility, excludedLeadId, "name", name) > 0) {
            return true;
        }
        if (phoneNumber != null
                && rootDuplicateCount(tenantId, visibility, excludedLeadId, "phone_number", phoneNumber) > 0) {
            return true;
        }
        if (uniqueFieldValues == null || uniqueFieldValues.isEmpty()) {
            return false;
        }
        Map<String, CustomFieldDefinitionPO> definitions = fieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.LEAD.name())
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
            LambdaQueryWrapper<LeadUniqueFieldValuePO> query =
                    new LambdaQueryWrapper<LeadUniqueFieldValuePO>()
                            .eq(LeadUniqueFieldValuePO::getTenantId, tenantId.getValue())
                            .eq(LeadUniqueFieldValuePO::getFieldId, definition.getId())
                            .eq(LeadUniqueFieldValuePO::getNormalizedHash,
                                    hash(normalizeUnique(entry.getValue())));
            if (excludedLeadId != null) {
                query.ne(LeadUniqueFieldValuePO::getLeadId, excludedLeadId.getValue());
            }
            List<LeadId> candidates = uniqueFieldValueMapper.selectList(query).stream()
                    .map(value -> new LeadId(value.getLeadId())).toList();
            if (!findVisibleByIds(tenantId, candidates, visibility, MAX_INTERNAL_LIMIT).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> findVisibleTags(
            TenantId tenantId, LeadVisibility visibility, int limit) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        return leadMapper.selectVisibleTags(
                tenantId.getValue(), visibility.callerMemberId().getValue(),
                visibility.dataScope().name(), visibility.accessMode().name(),
                departmentValues(visibility), boundedLimit);
    }

    @Override
    public PageResult<LeadDistance> findNearby(
            TenantId tenantId, LeadNearbyQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("nearby lead query must not be null");
        }
        LeadVisibility visibility = query.visibility();
        GeoBoundingBox bounds = GeoBoundingBox.around(
                query.longitude(), query.latitude(), query.radiusMeters());
        IPage<LeadDistancePO> result = leadMapper.selectNearbyPage(
                new Page<>(query.page(), query.size()), tenantId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility),
                query.longitude(), query.latitude(),
                bounds.minLongitude(), bounds.maxLongitude(),
                bounds.minLatitude(), bounds.maxLatitude(),
                bounds.longitudeUnbounded(), bounds.crossesAntimeridian(),
                query.radiusMeters());
        List<Lead> leads = hydrated(tenantId,
                new ArrayList<LeadPO>(result.getRecords()));
        Map<Long, Lead> byId = leads.stream().collect(Collectors.toMap(
                lead -> lead.getId().getValue(), Function.identity()));
        List<LeadDistance> values = result.getRecords().stream()
                .map(po -> new LeadDistance(byId.get(po.getId()), po.getDistanceMeters()))
                .filter(value -> value.lead() != null)
                .toList();
        return new PageResult<>(result.getCurrent(), result.getSize(), Math.max(0L, result.getTotal()), values);
    }

    private void synchronizeChildren(
            TenantId tenantId, LeadId leadId, Lead lead,
            Map<FieldCode, FieldDefinition> fieldsByCode) {
        List<LeadAddressPO> addresses = lead.getAddresses().stream()
                .map(value -> converter.toPO(tenantId, leadId, value)).toList();
        List<LeadTagPO> tags = lead.getTags().stream()
                .map(value -> converter.toPO(tenantId, leadId, value)).toList();
        List<LeadCollaboratorPO> collaborators = lead.getCollaborators().stream()
                .map(value -> converter.toPO(tenantId, leadId, value)).toList();
        List<LeadFieldValuePO> fieldValues = new ArrayList<>();
        List<LeadUniqueFieldValuePO> uniqueValues = new ArrayList<>();
        for (LeadFieldValue value : lead.getCustomFieldValues()) {
            FieldDefinition field = fieldsByCode.get(value.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("lead field definition is missing");
            }
            fieldValues.add(converter.toPO(tenantId, leadId, value, field));
            if (lead.isActive() && field.isUnique()) {
                uniqueValues.add(uniqueValue(tenantId, leadId, field, value.values().getFirst()));
            }
        }

        List<LeadAddressPO> storedAddresses = addressMapper.selectList(childFilter(
                tenantId, leadId, LeadAddressPO::getTenantId, LeadAddressPO::getLeadId));
        clearPreviousPrimaryAddress(tenantId, leadId, storedAddresses, addresses);
        ChildCollectionSynchronizer.synchronize(storedAddresses, addresses,
                LeadAddressPO::getAddressKey, this::sameAddress,
                addressMapper::insert,
                stored -> addressMapper.delete(childFilter(tenantId, leadId,
                        LeadAddressPO::getTenantId, LeadAddressPO::getLeadId)
                        .eq(LeadAddressPO::getAddressKey, stored.getAddressKey())),
                (stored, desired) -> updateAddress(tenantId, leadId, desired));
        ChildCollectionSynchronizer.synchronize(tagMapper.selectList(childFilter(
                        tenantId, leadId, LeadTagPO::getTenantId, LeadTagPO::getLeadId)),
                tags, LeadTagPO::getTagKey,
                (stored, desired) -> Objects.equals(stored.getTagName(), desired.getTagName()),
                tagMapper::insert,
                stored -> tagMapper.delete(childFilter(tenantId, leadId,
                        LeadTagPO::getTenantId, LeadTagPO::getLeadId)
                        .eq(LeadTagPO::getTagKey, stored.getTagKey())),
                (stored, desired) -> tagMapper.update(null,
                        new LambdaUpdateWrapper<LeadTagPO>()
                                .eq(LeadTagPO::getTenantId, tenantId.getValue())
                                .eq(LeadTagPO::getLeadId, leadId.getValue())
                                .eq(LeadTagPO::getTagKey, desired.getTagKey())
                                .set(LeadTagPO::getTagName, desired.getTagName())));
        ChildCollectionSynchronizer.synchronize(collaboratorMapper.selectList(childFilter(
                        tenantId, leadId, LeadCollaboratorPO::getTenantId,
                        LeadCollaboratorPO::getLeadId)),
                collaborators, LeadCollaboratorPO::getActorId,
                (stored, desired) -> Objects.equals(
                        stored.getAccessLevel(), desired.getAccessLevel()),
                collaboratorMapper::insert,
                stored -> collaboratorMapper.delete(childFilter(tenantId, leadId,
                        LeadCollaboratorPO::getTenantId, LeadCollaboratorPO::getLeadId)
                        .eq(LeadCollaboratorPO::getActorId, stored.getActorId())),
                (stored, desired) -> collaboratorMapper.update(null,
                        new LambdaUpdateWrapper<LeadCollaboratorPO>()
                                .eq(LeadCollaboratorPO::getTenantId, tenantId.getValue())
                                .eq(LeadCollaboratorPO::getLeadId, leadId.getValue())
                                .eq(LeadCollaboratorPO::getActorId, desired.getActorId())
                                .set(LeadCollaboratorPO::getAccessLevel, desired.getAccessLevel())
                                .set(LeadCollaboratorPO::getUpdatedTime, LocalDateTime.now())));
        ChildCollectionSynchronizer.synchronize(fieldValueMapper.selectList(childFilter(
                        tenantId, leadId, LeadFieldValuePO::getTenantId,
                        LeadFieldValuePO::getLeadId)),
                fieldValues, LeadFieldValuePO::getFieldId,
                (stored, desired) -> JsonSemanticEquality.equal(
                        objectMapper, stored.getValuesJson(), desired.getValuesJson())
                        && Objects.equals(stored.getScalarValue(), desired.getScalarValue()),
                fieldValueMapper::insert,
                stored -> fieldValueMapper.delete(childFilter(tenantId, leadId,
                        LeadFieldValuePO::getTenantId, LeadFieldValuePO::getLeadId)
                        .eq(LeadFieldValuePO::getFieldId, stored.getFieldId())),
                (stored, desired) -> fieldValueMapper.update(null,
                        new LambdaUpdateWrapper<LeadFieldValuePO>()
                                .eq(LeadFieldValuePO::getTenantId, tenantId.getValue())
                                .eq(LeadFieldValuePO::getLeadId, leadId.getValue())
                                .eq(LeadFieldValuePO::getFieldId, desired.getFieldId())
                                .set(LeadFieldValuePO::getValuesJson, desired.getValuesJson())
                                .set(LeadFieldValuePO::getScalarValue, desired.getScalarValue())
                                .set(LeadFieldValuePO::getUpdatedTime, LocalDateTime.now())));
        ChildCollectionSynchronizer.synchronize(uniqueFieldValueMapper.selectList(childFilter(
                        tenantId, leadId, LeadUniqueFieldValuePO::getTenantId,
                        LeadUniqueFieldValuePO::getLeadId)),
                uniqueValues, LeadUniqueFieldValuePO::getFieldId,
                (stored, desired) -> Objects.equals(
                        stored.getNormalizedHash(), desired.getNormalizedHash())
                        && Objects.equals(stored.getNormalizedValue(), desired.getNormalizedValue()),
                uniqueFieldValueMapper::insert,
                stored -> deleteUniqueValue(tenantId, leadId, stored.getFieldId()),
                (stored, desired) -> {
                    deleteUniqueValue(tenantId, leadId, stored.getFieldId());
                    uniqueFieldValueMapper.insert(desired);
                });
    }

    private void clearPreviousPrimaryAddress(
            TenantId tenantId, LeadId leadId,
            List<LeadAddressPO> stored, List<LeadAddressPO> desired) {
        String storedPrimary = stored.stream().filter(value -> Boolean.TRUE.equals(
                        value.getPrimaryAddress())).map(LeadAddressPO::getAddressKey)
                .findFirst().orElse(null);
        String desiredPrimary = desired.stream().filter(value -> Boolean.TRUE.equals(
                        value.getPrimaryAddress())).map(LeadAddressPO::getAddressKey)
                .findFirst().orElse(null);
        if (storedPrimary != null && !Objects.equals(storedPrimary, desiredPrimary)) {
            addressMapper.update(null, new LambdaUpdateWrapper<LeadAddressPO>()
                    .eq(LeadAddressPO::getTenantId, tenantId.getValue())
                    .eq(LeadAddressPO::getLeadId, leadId.getValue())
                    .eq(LeadAddressPO::getAddressKey, storedPrimary)
                    .set(LeadAddressPO::getPrimaryAddress, false)
                    .set(LeadAddressPO::getUpdatedTime, LocalDateTime.now()));
        }
    }

    private boolean sameAddress(LeadAddressPO stored, LeadAddressPO desired) {
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

    private void updateAddress(TenantId tenantId, LeadId leadId, LeadAddressPO desired) {
        addressMapper.update(null, new LambdaUpdateWrapper<LeadAddressPO>()
                .eq(LeadAddressPO::getTenantId, tenantId.getValue())
                .eq(LeadAddressPO::getLeadId, leadId.getValue())
                .eq(LeadAddressPO::getAddressKey, desired.getAddressKey())
                .set(LeadAddressPO::getLabel, desired.getLabel())
                .set(LeadAddressPO::getCountry, desired.getCountry())
                .set(LeadAddressPO::getRegion, desired.getRegion())
                .set(LeadAddressPO::getCity, desired.getCity())
                .set(LeadAddressPO::getPostalCode, desired.getPostalCode())
                .set(LeadAddressPO::getAddressLine, desired.getAddressLine())
                .set(LeadAddressPO::getLongitude, desired.getLongitude())
                .set(LeadAddressPO::getLatitude, desired.getLatitude())
                .set(LeadAddressPO::getPrimaryAddress, desired.getPrimaryAddress())
                .set(LeadAddressPO::getUpdatedTime, LocalDateTime.now()));
    }

    private void deleteUniqueValue(TenantId tenantId, LeadId leadId, Long fieldId) {
        uniqueFieldValueMapper.delete(childFilter(tenantId, leadId,
                LeadUniqueFieldValuePO::getTenantId, LeadUniqueFieldValuePO::getLeadId)
                .eq(LeadUniqueFieldValuePO::getFieldId, fieldId));
    }

    private <T> LambdaQueryWrapper<T> childFilter(
            TenantId tenantId, LeadId leadId,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> leadColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .eq(leadColumn, leadId.getValue());
    }

    private LeadUniqueFieldValuePO uniqueValue(
            TenantId tenantId, LeadId leadId,
            FieldDefinition field, String value) {
        String normalized = normalizeUnique(value);
        LeadUniqueFieldValuePO po = new LeadUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setLeadId(leadId.getValue());
        po.setObjectType(BusinessObjectType.LEAD.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedHash(hash(normalized));
        po.setNormalizedValue(normalized);
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private List<Lead> hydrated(TenantId tenantId, List<LeadPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> leadIds = roots.stream().map(LeadPO::getId).distinct().toList();
        List<LeadAddressPO> addresses = addressMapper.selectList(
                childListFilter(tenantId, leadIds, LeadAddressPO::getTenantId,
                        LeadAddressPO::getLeadId)
                        .orderByAsc(LeadAddressPO::getId));
        List<LeadTagPO> tags = tagMapper.selectList(
                childListFilter(tenantId, leadIds, LeadTagPO::getTenantId,
                        LeadTagPO::getLeadId)
                        .orderByAsc(LeadTagPO::getTagName));
        List<LeadCollaboratorPO> collaborators = collaboratorMapper.selectList(
                childListFilter(tenantId, leadIds, LeadCollaboratorPO::getTenantId,
                        LeadCollaboratorPO::getLeadId)
                        .orderByAsc(LeadCollaboratorPO::getActorId));
        List<LeadFieldValuePO> values = fieldValueMapper.selectList(
                childListFilter(tenantId, leadIds, LeadFieldValuePO::getTenantId,
                        LeadFieldValuePO::getLeadId)
                        .orderByAsc(LeadFieldValuePO::getFieldId));
        Set<Long> fieldIds = values.stream().map(LeadFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, FieldDefinition> fieldsById = fieldIds.isEmpty() ? Map.of()
                : fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                        .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                        .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.LEAD.name())
                        .in(CustomFieldDefinitionPO::getId, fieldIds))
                .stream().map(fieldDefinitionConverter::toDomain)
                .collect(Collectors.toMap(field -> field.getId().getValue(), Function.identity()));
        Map<Long, List<LeadAddressPO>> addressMap = group(addresses, LeadAddressPO::getLeadId);
        Map<Long, List<LeadTagPO>> tagMap = group(tags, LeadTagPO::getLeadId);
        Map<Long, List<LeadCollaboratorPO>> collaboratorMap = group(
                collaborators, LeadCollaboratorPO::getLeadId);
        Map<Long, List<LeadFieldValuePO>> valueMap = group(values, LeadFieldValuePO::getLeadId);
        return roots.stream().map(po -> converter.toDomain(po,
                addressMap.getOrDefault(po.getId(), List.of()),
                tagMap.getOrDefault(po.getId(), List.of()),
                collaboratorMap.getOrDefault(po.getId(), List.of()),
                valueMap.getOrDefault(po.getId(), List.of()), fieldsById)).toList();
    }

    private <T> LambdaQueryWrapper<T> childListFilter(
            TenantId tenantId, List<Long> leadIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> tenantColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> leadColumn) {
        return new LambdaQueryWrapper<T>().eq(tenantColumn, tenantId.getValue())
                .in(leadColumn, leadIds);
    }

    private <T> Map<Long, List<T>> group(List<T> values, Function<T, Long> leadId) {
        return values.stream().collect(Collectors.groupingBy(leadId,
                LinkedHashMap::new, Collectors.toList()));
    }

    private QueryWrapper<LeadPO> visibleRootQuery(
            TenantId tenantId, LeadVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("lead visibility must not be null");
        }
        QueryWrapper<LeadPO> query = new QueryWrapper<LeadPO>()
                .eq("tenant_id", tenantId.getValue())
                .in("status", List.of(LeadStatus.ACTIVE.name(), LeadStatus.CONVERTED.name()));
        applyVisibility(query, visibility);
        return query;
    }

    private void requireReportRange(ReportDateRange range) {
        if (range == null) {
            throw new IllegalArgumentException("lead dashboard range must not be null");
        }
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.LeadSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("lead custom sort is invalid");
        }
        String scalar = """
                (SELECT lead_field_sort.scalar_value
                FROM crm_lead_field_value lead_field_sort
                WHERE lead_field_sort.tenant_id = crm_lead.tenant_id
                  AND lead_field_sort.lead_id = crm_lead.id
                  AND lead_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private List<Lead> visibleHierarchyLeads(
            TenantId tenantId, LeadId leadId,
            LeadVisibility visibility, int limit, boolean descendants) {
        requireTenant(tenantId);
        int boundedLimit = requireLimit(limit);
        if (visibility == null) {
            throw new IllegalArgumentException("lead visibility must not be null");
        }
        if (leadId == null) {
            return List.of();
        }
        List<Long> hierarchyIds = descendants
                ? leadMapper.selectVisibleDescendantIds(
                tenantId.getValue(), leadId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility), boundedLimit,
                MAX_HIERARCHY_DEPTH, MAX_HIERARCHY_TRAVERSAL)
                : leadMapper.selectVisibleAncestorIds(
                tenantId.getValue(), leadId.getValue(),
                visibility.callerMemberId().getValue(), visibility.dataScope().name(),
                visibility.accessMode().name(), departmentValues(visibility), boundedLimit);
        if (hierarchyIds.isEmpty()) {
            return List.of();
        }
        return findVisibleByIds(tenantId, hierarchyIds.stream()
                        .map(LeadId::new).toList(), visibility, boundedLimit);
    }

    private void applyVisibility(
            QueryWrapper<LeadPO> query, LeadVisibility visibility) {
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
                            WHERE owner_membership.tenant_id = crm_lead.tenant_id
                              AND owner_membership.actor_id = crm_lead.owner_actor_id
                              AND owner_membership.status = 'ACTIVE'
                              AND owner_membership.department_id IN (%s)
                        )
                        """.formatted(placeholders), arguments.toArray());
            }
            if (visibility.accessMode() != LeadAccessMode.OWNER_SCOPE) {
                String access = visibility.accessMode() == LeadAccessMode.WRITE
                        ? " AND collaborator.access_level = 'WRITE'" : "";
                nested.or().apply("""
                        EXISTS (
                            SELECT 1 FROM crm_lead_collaborator collaborator
                            WHERE collaborator.tenant_id = crm_lead.tenant_id
                              AND collaborator.lead_id = crm_lead.id
                              AND collaborator.actor_id = {0}%s
                        )
                        """.formatted(access), visibility.callerMemberId().getValue());
            }
        });
    }

    private void applyStarred(
            QueryWrapper<LeadPO> query, LeadVisibility visibility, Boolean starred) {
        if (starred == null) {
            return;
        }
        String exists = """
                EXISTS (
                    SELECT 1 FROM crm_lead_star star
                    WHERE star.tenant_id = crm_lead.tenant_id
                      AND star.lead_id = crm_lead.id
                      AND star.actor_id = {0}
                )
                """;
        query.apply(Boolean.TRUE.equals(starred) ? exists : "NOT " + exists,
                visibility.callerMemberId().getValue());
    }

    private void applyNamePrefix(QueryWrapper<LeadPO> query, String name) {
        query.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", SqlLikePattern.literal(name));
    }

    private void applyKeyword(
            QueryWrapper<LeadPO> query, String keyword,
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
            if (candidates.containsKey(new FieldCode(LeadFieldCatalog.NAME))) {
                nested.apply("name LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(LeadFieldCatalog.PHONE_NUMBER))) {
                if (previous) nested.or();
                nested.apply("phone_number LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(LeadFieldCatalog.TELEPHONE))) {
                if (previous) nested.or();
                nested.apply("telephone LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
                previous = true;
            }
            if (candidates.containsKey(new FieldCode(LeadFieldCatalog.EMAIL))) {
                if (previous) nested.or();
                nested.apply("email LIKE CONCAT({0}, '%') ESCAPE '!'", literal);
            }
        });
    }

    private void applyCondition(QueryWrapper<LeadPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("lead filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            applyBuiltinCondition(query, column, condition);
        } else {
            applyCustomCondition(query, condition);
        }
    }

    private void applyBuiltinCondition(
            QueryWrapper<LeadPO> query, String column, FilterCondition condition) {
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
            QueryWrapper<LeadPO> query, FilterCondition condition) {
        String base = """
                SELECT 1
                FROM crm_lead_field_value field_value
                JOIN custom_field_definition field_definition
                  ON field_definition.tenant_id = field_value.tenant_id
                 AND field_definition.object_type = field_value.object_type
                 AND field_definition.id = field_value.field_id
                WHERE field_value.tenant_id = crm_lead.tenant_id
                  AND field_value.lead_id = crm_lead.id
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
            QueryWrapper<LeadPO> query, String base, String fieldCode,
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
        if (new FieldCode(LeadFieldCatalog.LOCKED).equals(fieldCode)) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private long rootDuplicateCount(
            TenantId tenantId, LeadVisibility visibility, LeadId excludedLeadId,
            String column, String value) {
        QueryWrapper<LeadPO> query = visibleRootQuery(tenantId, visibility).eq(column, value);
        if (excludedLeadId != null) {
            query.ne("id", excludedLeadId.getValue());
        }
        return leadMapper.selectCount(query);
    }

    private LambdaQueryWrapper<LeadPO> tenantRootFilter(TenantId tenantId) {
        requireTenant(tenantId);
        return new LambdaQueryWrapper<LeadPO>()
                .eq(LeadPO::getTenantId, tenantId.getValue());
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

    private List<Long> ids(Collection<LeadId> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(java.util.Objects::nonNull)
                .map(LeadId::getValue).distinct().toList();
    }

    private List<Long> departmentValues(LeadVisibility visibility) {
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
            throw new IllegalArgumentException("lead query limit is invalid");
        }
        return limit;
    }

    private int requireExportIdLimit(int limit) {
        if (limit < 1 || limit > MAX_EXPORT_ID_LIMIT) {
            throw new IllegalArgumentException("lead export ID limit is invalid");
        }
        return limit;
    }

    private void requireTenantMatch(TenantId tenantId, Lead lead) {
        requireTenant(tenantId);
        if (lead == null || lead.getTenantId() == null
                || !tenantId.sameValueAs(lead.getTenantId())) {
            throw new IllegalArgumentException("lead tenant does not match repository tenant");
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
            throw new IllegalStateException("Lead query value cannot be serialized", exception);
        }
    }

    private static Map<FieldCode, String> builtinColumns() {
        Map<FieldCode, String> values = new HashMap<>();
        values.put(new FieldCode(LeadFieldCatalog.NAME), "name");
        values.put(new FieldCode(LeadFieldCatalog.PHONE_NUMBER), "phone_number");
        values.put(new FieldCode(LeadFieldCatalog.TELEPHONE), "telephone");
        values.put(new FieldCode(LeadFieldCatalog.JOB_TITLE), "job_title");
        values.put(new FieldCode(LeadFieldCatalog.EMAIL), "email");
        values.put(new FieldCode(LeadFieldCatalog.WEBSITE), "website");
        values.put(new FieldCode(LeadFieldCatalog.DESCRIPTION), "description");
        values.put(new FieldCode(LeadFieldCatalog.NEXT_CONTACT_TIME), "next_contact_time");
        values.put(new FieldCode(LeadFieldCatalog.OWNER_ACTOR), "owner_actor_id");
        values.put(new FieldCode(LeadFieldCatalog.CUSTOMER), "customer_id");
        values.put(new FieldCode(LeadFieldCatalog.PARENT_LEAD), "parent_lead_id");
        values.put(new FieldCode(LeadFieldCatalog.LOCKED), "is_locked");
        values.put(new FieldCode(LeadFieldCatalog.DEAL_STATUS), "deal_status");
        values.put(new FieldCode(LeadFieldCatalog.STATUS), "status");
        values.put(new FieldCode(LeadFieldCatalog.CREATED_TIME), "created_time");
        values.put(new FieldCode(LeadFieldCatalog.UPDATED_TIME), "updated_time");
        return Map.copyOf(values);
    }

    private static Map<FieldCode, Function<LeadExportRootPO, String>> builtinExportValues() {
        Map<FieldCode, Function<LeadExportRootPO, String>> values = new HashMap<>();
        values.put(new FieldCode(LeadFieldCatalog.NAME), LeadExportRootPO::getName);
        values.put(new FieldCode(LeadFieldCatalog.PHONE_NUMBER), LeadExportRootPO::getPhoneNumber);
        values.put(new FieldCode(LeadFieldCatalog.TELEPHONE), LeadExportRootPO::getTelephone);
        values.put(new FieldCode(LeadFieldCatalog.JOB_TITLE), LeadExportRootPO::getJobTitle);
        values.put(new FieldCode(LeadFieldCatalog.EMAIL), LeadExportRootPO::getEmail);
        values.put(new FieldCode(LeadFieldCatalog.WEBSITE), LeadExportRootPO::getWebsite);
        values.put(new FieldCode(LeadFieldCatalog.DESCRIPTION), LeadExportRootPO::getDescription);
        values.put(new FieldCode(LeadFieldCatalog.NEXT_CONTACT_TIME), value ->
                value.getNextContactTime() == null ? null : value.getNextContactTime().toString());
        values.put(new FieldCode(LeadFieldCatalog.LOCKED), value -> value.getLocked() == null
                ? null : Boolean.toString(value.getLocked()));
        values.put(new FieldCode(LeadFieldCatalog.DEAL_STATUS), LeadExportRootPO::getDealStatus);
        values.put(new FieldCode(LeadFieldCatalog.STATUS), LeadExportRootPO::getStatus);
        values.put(new FieldCode(LeadFieldCatalog.CREATED_TIME), value -> value.getCreatedTime() == null
                ? null : value.getCreatedTime().toString());
        values.put(new FieldCode(LeadFieldCatalog.UPDATED_TIME), value -> value.getUpdatedTime() == null
                ? null : value.getUpdatedTime().toString());
        return Map.copyOf(values);
    }
}
