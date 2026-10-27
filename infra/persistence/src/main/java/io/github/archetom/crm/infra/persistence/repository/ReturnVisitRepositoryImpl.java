package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.infra.persistence.support.ConstraintViolations;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.ReturnVisitFieldValue;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.ReturnVisitQuery;
import io.github.archetom.crm.domain.model.ReturnVisitSortDirection;
import io.github.archetom.crm.domain.model.ReturnVisitCustomerSummary;
import io.github.archetom.crm.domain.model.ReturnVisitStatus;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.ReturnVisitFieldValuePOConverter;
import io.github.archetom.crm.infra.persistence.converter.ReturnVisitPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReturnVisitFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReturnVisitMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReturnVisitUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitUniqueFieldValuePO;
import io.github.archetom.crm.infra.persistence.support.QueryFilterConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReturnVisitRepositoryImpl implements ReturnVisitRepository {

    private static final Map<FieldCode, String> BUILTIN_COLUMNS = Map.ofEntries(
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.NUMBER), "visit_number"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.CUSTOMER), "customer_id"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.CONTACT), "contact_id"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.CONTRACT), "contract_id"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.VISIT_TIME), "visit_time"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.VISIT_FORM), "visit_form"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.SATISFACTION), "satisfaction"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.REMARK), "remark"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.OWNER_ACTOR), "owner_actor_id"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.STATUS), "status"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.CREATED_TIME), "created_time"),
            Map.entry(new FieldCode(ReturnVisitFieldCatalog.UPDATED_TIME), "updated_time"));
    private static final String CONTACT_CUSTOMER_FK =
            "fk_crm_return_visit_contact_customer";
    private static final String CONTRACT_CUSTOMER_FK =
            "fk_crm_return_visit_contract_customer";

    private final ReturnVisitMapper mapper;
    private final ReturnVisitFieldValueMapper fieldValueMapper;
    private final ReturnVisitUniqueFieldValueMapper uniqueValueMapper;
    private final CustomFieldDefinitionMapper fieldDefinitionMapper;
    private final ReturnVisitPOConverter converter;
    private final ReturnVisitFieldValuePOConverter fieldConverter;
    private final CustomFieldDefinitionPOConverter definitionConverter;

    @Override
    public ReturnVisit save(TenantId tenantId, ReturnVisit value,
                            Collection<FieldDefinition> fieldDefinitions) {
        requireTenant(tenantId);
        if (value == null || !tenantId.sameValueAs(value.getTenantId())) {
            throw new IllegalArgumentException("return visit tenant does not match");
        }
        ReturnVisitPO po = converter.toPO(value);
        try {
            if (po.getId() == null) {
                if (mapper.insert(po) != 1) throw new IllegalStateException("return visit insert failed");
            } else if (mapper.update(po, new LambdaUpdateWrapper<ReturnVisitPO>()
                    .eq(ReturnVisitPO::getTenantId, tenantId.getValue())
                    .eq(ReturnVisitPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            ReturnVisitId id = new ReturnVisitId(po.getId());
            synchronize(tenantId, id, value, definitions(fieldDefinitions));
            value.onPersisted(id, po.getVersion(), po.getCreatedTime(), po.getUpdatedTime());
            return value;
        } catch (DuplicateKeyException exception) {
            throw new ReturnVisitDomainException("return visit unique field value already exists");
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.causeContains(exception, CONTACT_CUSTOMER_FK)
                    || ConstraintViolations.causeContains(exception, CONTRACT_CUSTOMER_FK)) {
                throw new ReturnVisitDomainException(
                        "return visit relationship customer changed concurrently");
            }
            throw exception;
        }
    }


    @Override
    public Optional<ReturnVisit> findVisibleById(
            TenantId tenantId, ReturnVisitId id, ReturnVisitVisibility visibility) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReturnVisitPO root = mapper.selectOne(visible(tenantId, visibility).eq("id", id.getValue()));
        return root == null ? Optional.empty() : Optional.of(hydrate(tenantId, List.of(root)).getFirst());
    }

    @Override
    public Optional<ReturnVisit> findVisibleByIdForUpdate(
            TenantId tenantId, ReturnVisitId id, ReturnVisitVisibility visibility) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReturnVisitPO root = mapper.selectOne(visible(tenantId, visibility)
                .eq("id", id.getValue()).last("FOR UPDATE"));
        return root == null ? Optional.empty() : Optional.of(hydrate(tenantId, List.of(root)).getFirst());
    }

    @Override
    public Optional<ReturnVisit> findByIdForAttachmentMutation(
            TenantId tenantId, ReturnVisitId id) {
        requireTenant(tenantId);
        if (id == null) return Optional.empty();
        ReturnVisitPO root = mapper.selectOne(new QueryWrapper<ReturnVisitPO>()
                .eq("tenant_id", tenantId.getValue()).eq("id", id.getValue()).last("FOR UPDATE"));
        return root == null ? Optional.empty() : Optional.of(hydrate(tenantId, List.of(root)).getFirst());
    }

    @Override
    public PageResult<ReturnVisit> findReturnVisits(TenantId tenantId, ReturnVisitQuery query) {
        requireTenant(tenantId);
        if (query == null) throw new IllegalArgumentException("return visit query is required");
        QueryWrapper<ReturnVisitPO> wrapper = visible(tenantId, query.visibility());
        if (query.keyword() != null) {
            if (query.keywordCandidates().isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.apply("visit_number LIKE CONCAT({0}, '%') ESCAPE '!'",
                        io.github.archetom.crm.infra.persistence.support.SqlLikePattern
                                .literal(query.keyword()));
            }
        }
        if (query.ownerMemberId() != null) wrapper.eq("owner_actor_id", query.ownerMemberId().getValue());
        if (query.subordinateOwnerMemberIds() != null) {
            List<Long> ownerIds = query.subordinateOwnerMemberIds().stream()
                    .map(value -> value.getValue()).toList();
            if (ownerIds.isEmpty()) wrapper.apply("1 = 0");
            else wrapper.in("owner_actor_id", ownerIds);
        }
        if (query.customerId() != null) wrapper.eq("customer_id", query.customerId().getValue());
        if (query.contractId() != null) wrapper.eq("contract_id", query.contractId().getValue());
        query.conditions().forEach(condition -> applyCondition(wrapper, condition));
        boolean ascending = query.sort().direction() == ReturnVisitSortDirection.ASC;
        String sortExpression = query.sort().isCustom()
                ? customSortExpression(query.sort()) : query.sort().field().column();
        wrapper.orderBy(true, ascending, sortExpression)
                .orderBy(true, ascending, "id");
        IPage<ReturnVisitPO> page = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new PageResult<>(page.getCurrent(), page.getSize(), Math.max(0, page.getTotal()),
                hydrate(tenantId, page.getRecords()));
    }

    private String customSortExpression(io.github.archetom.crm.domain.model.ReturnVisitSort sort) {
        if (!sort.isCustom() || sort.customFieldDefinitionId() == null) {
            throw new IllegalArgumentException("return visit custom sort is invalid");
        }
        String scalar = """
                (SELECT return_visit_field_sort.scalar_value
                FROM crm_return_visit_field_value return_visit_field_sort
                WHERE return_visit_field_sort.tenant_id = crm_return_visit.tenant_id
                  AND return_visit_field_sort.return_visit_id = crm_return_visit.id
                  AND return_visit_field_sort.field_id = %d
                LIMIT 1)
                """.formatted(sort.customFieldDefinitionId());
        return sort.isCustomNumeric() ? "CAST(" + scalar + " AS DECIMAL(30, 10))" : scalar;
    }

    private void applyCondition(QueryWrapper<ReturnVisitPO> query, FilterCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("return visit filter condition must not be null");
        }
        String column = BUILTIN_COLUMNS.get(condition.fieldCode());
        if (column != null) {
            QueryFilterConditions.applyBuiltin(query, column, condition);
            return;
        }
        QueryFilterConditions.applyCustom(query, condition, "crm_return_visit",
                "crm_return_visit_field_value", "return_visit_id");
    }

    @Override
    public ReturnVisitCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, ReturnVisitVisibility visibility) {
        requireTenant(tenantId);
        if (customerId == null) {
            throw new IllegalArgumentException("customer ID is required");
        }
        long count = mapper.selectCount(visible(tenantId, visibility)
                .eq("customer_id", customerId.getValue())
                .eq("status", ReturnVisitStatus.ACTIVE.name()));
        return new ReturnVisitCustomerSummary(Math.max(0L, count));
    }

    @Override
    public List<ReturnVisit> findOptions(TenantId tenantId, ReturnVisitVisibility visibility,
                                         String keyword, int limit) {
        requireTenant(tenantId);
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (normalized != null && normalized.codePointCount(0, normalized.length()) > 64
                || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("return visit option query is invalid");
        }
        QueryWrapper<ReturnVisitPO> query = visible(tenantId, visibility);
        if (normalized != null) {
            query.apply("visit_number LIKE CONCAT('%', {0}, '%') ESCAPE '!'",
                    io.github.archetom.crm.infra.persistence.support.SqlLikePattern.literal(normalized));
        }
        return hydrate(tenantId, mapper.selectList(query.orderByDesc("visit_time")
                .orderByDesc("id").last("LIMIT " + limit)));
    }

    @Override
    public List<ReturnVisit> findScheduled(
            TenantId tenantId, ReturnVisitVisibility visibility,
            LocalDateTime fromInclusive, LocalDateTime untilExclusive,
            LocalDateTime afterTime, Long afterId, int limit) {
        requireTenant(tenantId);
        if (fromInclusive == null || untilExclusive == null || !fromInclusive.isBefore(untilExclusive)
                || (afterTime == null) != (afterId == null) || (afterId != null && afterId <= 0)
                || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("return visit scheduled query is invalid");
        }
        QueryWrapper<ReturnVisitPO> query = visible(tenantId, visibility)
                .ge("visit_time", fromInclusive).lt("visit_time", untilExclusive);
        if (afterTime != null) {
            query.and(wrapper -> wrapper.gt("visit_time", afterTime)
                    .or(next -> next.eq("visit_time", afterTime).gt("id", afterId)));
        }
        List<ReturnVisitPO> roots = mapper.selectList(query.orderByAsc("visit_time")
                .orderByAsc("id").last("LIMIT " + limit));
        return hydrate(tenantId, roots);
    }

    private void synchronize(TenantId tenantId, ReturnVisitId visitId, ReturnVisit value,
                             Map<FieldCode, FieldDefinition> fields) {
        fieldValueMapper.delete(new LambdaQueryWrapper<ReturnVisitFieldValuePO>()
                .eq(ReturnVisitFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ReturnVisitFieldValuePO::getReturnVisitId, visitId.getValue()));
        uniqueValueMapper.delete(new LambdaQueryWrapper<ReturnVisitUniqueFieldValuePO>()
                .eq(ReturnVisitUniqueFieldValuePO::getTenantId, tenantId.getValue())
                .eq(ReturnVisitUniqueFieldValuePO::getReturnVisitId, visitId.getValue()));
        for (ReturnVisitFieldValue fieldValue : value.getCustomFieldValues()) {
            FieldDefinition field = fields.get(fieldValue.fieldCode());
            if (field == null || field.getId() == null || field.isBuiltin()) {
                throw new IllegalArgumentException("return visit field definition is missing");
            }
            fieldValueMapper.insert(fieldConverter.toPO(tenantId, visitId, fieldValue, field));
            if (field.isUnique() && !value.getStatus().equals(ReturnVisitStatus.DELETED)) {
                uniqueValueMapper.insert(unique(tenantId, visitId, field, fieldValue.values().getFirst()));
            }
        }
    }

    private ReturnVisitUniqueFieldValuePO unique(TenantId tenantId, ReturnVisitId visitId,
                                                 FieldDefinition field, String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        ReturnVisitUniqueFieldValuePO po = new ReturnVisitUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setReturnVisitId(visitId.getValue());
        po.setObjectType(BusinessObjectType.RETURN_VISIT.name());
        po.setFieldId(field.getId().getValue());
        po.setNormalizedValue(normalized);
        po.setNormalizedHash(hash(normalized));
        po.setCreatedTime(LocalDateTime.now());
        return po;
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private List<ReturnVisit> hydrate(TenantId tenantId, List<ReturnVisitPO> roots) {
        if (roots == null || roots.isEmpty()) return List.of();
        List<Long> ids = roots.stream().map(ReturnVisitPO::getId).toList();
        List<ReturnVisitFieldValuePO> values = fieldValueMapper.selectList(
                new LambdaQueryWrapper<ReturnVisitFieldValuePO>().eq(ReturnVisitFieldValuePO::getTenantId,
                        tenantId.getValue()).in(ReturnVisitFieldValuePO::getReturnVisitId, ids)
                        .orderByAsc(ReturnVisitFieldValuePO::getFieldId));
        Map<Long, FieldDefinition> fields = fields(tenantId, values.stream()
                .map(ReturnVisitFieldValuePO::getFieldId).collect(Collectors.toSet()));
        Map<Long, List<ReturnVisitFieldValuePO>> byRoot = values.stream().collect(Collectors.groupingBy(
                ReturnVisitFieldValuePO::getReturnVisitId));
        return roots.stream().map(root -> converter.toDomain(root,
                byRoot.getOrDefault(root.getId(), List.of()), fields)).toList();
    }

    private Map<FieldCode, FieldDefinition> definitions(Collection<FieldDefinition> fields) {
        if (fields == null) return Map.of();
        return fields.stream().collect(Collectors.toMap(FieldDefinition::getCode, Function.identity()));
    }

    private Map<Long, FieldDefinition> fields(TenantId tenantId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return fieldDefinitionMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                .eq(CustomFieldDefinitionPO::getObjectType, BusinessObjectType.RETURN_VISIT.name())
                .in(CustomFieldDefinitionPO::getId, ids)).stream().map(definitionConverter::toDomain)
                .collect(Collectors.toMap(value -> value.getId().getValue(), Function.identity()));
    }

    private QueryWrapper<ReturnVisitPO> visible(TenantId tenantId, ReturnVisitVisibility visibility) {
        if (visibility == null) throw new IllegalArgumentException("return visit visibility is required");
        QueryWrapper<ReturnVisitPO> query = new QueryWrapper<ReturnVisitPO>()
                .eq("tenant_id", tenantId.getValue()).ne("status", ReturnVisitStatus.DELETED.name());
        if (visibility.dataScope() == DataScope.TENANT) return query;
        if (visibility.dataScope() == DataScope.SELF) {
            return query.eq("owner_actor_id", visibility.callerMemberId().getValue());
        }
        List<Long> departments = visibility.ownerDepartmentIds().stream()
                .map(DepartmentId::getValue).sorted().toList();
        return query.apply("""
                EXISTS (SELECT 1 FROM organization_membership owner_membership
                 WHERE owner_membership.tenant_id = crm_return_visit.tenant_id
                   AND owner_membership.actor_id = crm_return_visit.owner_actor_id
                   AND owner_membership.status = 'ACTIVE'
                   AND owner_membership.department_id IN (%s))
                """.formatted(placeholders(departments.size())), departments.toArray());
    }

    private String placeholders(int count) {
        if (count < 1) throw new IllegalArgumentException("return visit departments are required");
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> "{" + index + "}")
                .collect(Collectors.joining(","));
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("return visit tenant is required");
    }
}
