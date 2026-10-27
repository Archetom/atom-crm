package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.OaRequest;
import io.github.archetom.crm.domain.exception.AggregateVersionConflictException;
import io.github.archetom.crm.domain.exception.OaRequestDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.OaRequestQuery;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.CustomFieldDefinitionPOConverter;
import io.github.archetom.crm.infra.persistence.converter.OaRequestPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomFieldDefinitionMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaRequestFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaRequestMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OaRequestUniqueFieldValueMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomFieldDefinitionPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestFieldValuePO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OaRequestUniqueFieldValuePO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OaRequestRepositoryImpl implements OaRequestRepository {

    private final OaRequestMapper requestMapper;
    private final OaRequestFieldValueMapper valueMapper;
    private final OaRequestUniqueFieldValueMapper uniqueValueMapper;
    private final CustomFieldDefinitionMapper fieldMapper;
    private final CustomFieldDefinitionPOConverter fieldConverter;
    private final OaRequestPOConverter converter;

    @Override
    public OaRequest save(
            TenantId tenantId, OaRequest request,
            Collection<FieldDefinition> fieldDefinitions) {
        requireTenantMatch(tenantId, request);
        OaRequestPO po = converter.toPO(request);
        boolean create = po.getId() == null;
        try {
            if (create) {
                if (requestMapper.insert(po) != 1) {
                    throw new IllegalStateException(
                            "OA request insert did not affect exactly one row");
                }
            } else if (requestMapper.update(po, new LambdaQueryWrapper<OaRequestPO>()
                    .eq(OaRequestPO::getTenantId, tenantId.getValue())
                    .eq(OaRequestPO::getId, po.getId())) != 1) {
                throw new AggregateVersionConflictException();
            }
            OaRequestId requestId = new OaRequestId(po.getId());
            if (create) {
                synchronizeValues(tenantId, requestId, request,
                        definitions(fieldDefinitions));
            }
            request.onPersisted(requestId, po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime());
            return request;
        } catch (DuplicateKeyException exception) {
            throw new OaRequestDomainException("OA request conflicts with existing data");
        }
    }

    @Override
    public Optional<OaRequest> findById(TenantId tenantId, OaRequestId requestId) {
        requireTenant(tenantId);
        if (requestId == null) {
            return Optional.empty();
        }
        return hydrateOne(tenantId, requestMapper.selectOne(rootFilter(tenantId)
                .eq(OaRequestPO::getId, requestId.getValue())));
    }

    @Override
    public Optional<OaRequest> findByIdForUpdate(TenantId tenantId, OaRequestId requestId) {
        requireTenant(tenantId);
        if (requestId == null) {
            return Optional.empty();
        }
        return hydrateOne(tenantId, requestMapper.selectOne(rootFilter(tenantId)
                .eq(OaRequestPO::getId, requestId.getValue()).last("FOR UPDATE")));
    }

    @Override
    public Optional<OaRequest> findByApprovalInstanceForUpdate(
            TenantId tenantId, ApprovalInstanceId approvalInstanceId) {
        requireTenant(tenantId);
        if (approvalInstanceId == null) {
            return Optional.empty();
        }
        return hydrateOne(tenantId, requestMapper.selectOne(rootFilter(tenantId)
                .eq(OaRequestPO::getApprovalInstanceId, approvalInstanceId.getValue())
                .last("FOR UPDATE")));
    }

    @Override
    public Optional<OaRequest> findByIdempotencyKey(
            TenantId tenantId, MemberId applicantMemberId, String idempotencyKey) {
        requireTenant(tenantId);
        if (applicantMemberId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return hydrateOne(tenantId, requestMapper.selectOne(rootFilter(tenantId)
                .eq(OaRequestPO::getApplicantActorId, applicantMemberId.getValue())
                .eq(OaRequestPO::getIdempotencyKey, idempotencyKey)));
    }

    @Override
    public Optional<OaRequest> findVisibleById(
            TenantId tenantId, OaRequestId requestId,
            MemberId memberId, boolean tenantManager) {
        requireTenant(tenantId);
        if (requestId == null || memberId == null) {
            return Optional.empty();
        }
        QueryWrapper<OaRequestPO> query = visible(tenantId, memberId, tenantManager)
                .eq("id", requestId.getValue());
        return hydrateOne(tenantId, requestMapper.selectOne(query));
    }

    @Override
    public Set<Long> findVisibleIds(
            TenantId tenantId, Collection<OaRequestId> requestIds,
            MemberId memberId, boolean tenantManager) {
        requireTenant(tenantId);
        if (requestIds == null || requestIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> ids = requestIds.stream().map(id -> {
            if (id == null) {
                throw new IllegalArgumentException("OA request ID is required");
            }
            return id.getValue();
        }).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() != requestIds.size() || memberId == null) {
            throw new IllegalArgumentException("OA request visibility batch is invalid");
        }
        return requestMapper.selectObjs(visible(tenantId, memberId, tenantManager)
                        .select("id").in("id", ids)).stream()
                .map(value -> ((Number) value).longValue())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public PageResult<OaRequest> findVisiblePage(
            TenantId tenantId, OaRequestQuery query) {
        requireTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("OA request query is required");
        }
        QueryWrapper<OaRequestPO> filter = visible(
                tenantId, query.memberId(), query.tenantManager());
        if (query.formId() != null) {
            filter.eq("form_id", query.formId().getValue());
        }
        if (query.approvalStatus() != null) {
            filter.apply("""
                    EXISTS (SELECT 1 FROM approval_instance approval_status
                             WHERE approval_status.tenant_id = oa_request.tenant_id
                               AND approval_status.id = oa_request.approval_instance_id
                               AND approval_status.status = {0})
                    """, query.approvalStatus().name());
        }
        filter.orderByDesc("submitted_time").orderByDesc("id");
        var page = requestMapper.selectPage(new Page<>(query.page(), query.size()), filter);
        return new PageResult<>(page.getCurrent(), page.getSize(),
                Math.max(0, page.getTotal()), hydrate(tenantId, page.getRecords()));
    }

    @Override
    public boolean existsByForm(TenantId tenantId, OaFormId formId) {
        requireTenant(tenantId);
        if (formId == null) {
            throw new IllegalArgumentException("OA form ID is required");
        }
        return requestMapper.selectCount(rootFilter(tenantId)
                .eq(OaRequestPO::getFormId, formId.getValue())) > 0;
    }

    private QueryWrapper<OaRequestPO> visible(
            TenantId tenantId, MemberId memberId, boolean tenantManager) {
        if (memberId == null) {
            throw new IllegalArgumentException("OA request member is required");
        }
        QueryWrapper<OaRequestPO> query = new QueryWrapper<OaRequestPO>()
                .eq("tenant_id", tenantId.getValue());
        if (tenantManager) {
            return query;
        }
        return query.and(scope -> scope.eq("applicant_actor_id", memberId.getValue())
                .or().apply("""
                        EXISTS (SELECT 1 FROM approval_review_task task
                                 WHERE task.tenant_id = oa_request.tenant_id
                                   AND task.instance_id = oa_request.approval_instance_id
                                   AND task.assigned_actor_id = {0})
                        """, memberId.getValue())
                .or().apply("""
                        EXISTS (SELECT 1 FROM approval_instance_manager manager_snapshot
                                 WHERE manager_snapshot.tenant_id = oa_request.tenant_id
                                   AND manager_snapshot.instance_id = oa_request.approval_instance_id
                                   AND manager_snapshot.actor_id = {0})
                        """, memberId.getValue())
                .or().apply("""
                        EXISTS (SELECT 1 FROM approval_instance_watcher watcher
                                 WHERE watcher.tenant_id = oa_request.tenant_id
                                   AND watcher.instance_id = oa_request.approval_instance_id
                                   AND watcher.actor_id = {0})
                        """, memberId.getValue()));
    }

    private Optional<OaRequest> hydrateOne(TenantId tenantId, OaRequestPO root) {
        return root == null ? Optional.empty()
                : Optional.of(hydrate(tenantId, List.of(root)).getFirst());
    }

    private List<OaRequest> hydrate(TenantId tenantId, Collection<OaRequestPO> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Long> ids = roots.stream().map(OaRequestPO::getId).toList();
        List<OaRequestFieldValuePO> values = valueMapper.selectList(
                new LambdaQueryWrapper<OaRequestFieldValuePO>()
                        .eq(OaRequestFieldValuePO::getTenantId, tenantId.getValue())
                        .in(OaRequestFieldValuePO::getOaRequestId, ids)
                        .orderByAsc(OaRequestFieldValuePO::getOaRequestId,
                                OaRequestFieldValuePO::getId));
        Map<Long, FieldDefinition> fields = fields(tenantId, values.stream()
                .map(OaRequestFieldValuePO::getFieldId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, List<OaRequestFieldValuePO>> byRequest = values.stream()
                .collect(Collectors.groupingBy(OaRequestFieldValuePO::getOaRequestId,
                        LinkedHashMap::new, Collectors.toList()));
        return roots.stream().map(root -> converter.toDomain(root,
                byRequest.getOrDefault(root.getId(), List.of()), fields)).toList();
    }

    private void synchronizeValues(
            TenantId tenantId, OaRequestId requestId, OaRequest request,
            Map<FieldCode, FieldDefinition> fields) {
        for (Map.Entry<FieldCode, List<String>> entry : request.getFacts().entrySet()) {
            FieldDefinition field = fields.get(entry.getKey());
            if (field == null || field.getId() == null
                    || field.getObjectType() != BusinessObjectType.OA_REQUEST) {
                throw new IllegalArgumentException("OA request field definition is missing");
            }
            valueMapper.insert(converter.toValuePO(
                    tenantId, requestId, field, entry.getValue()));
            if (field.isUnique() && !entry.getValue().isEmpty()) {
                uniqueValueMapper.insert(unique(
                        tenantId, requestId, field, entry.getValue().getFirst()));
            }
        }
    }

    private OaRequestUniqueFieldValuePO unique(
            TenantId tenantId, OaRequestId requestId,
            FieldDefinition field, String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        OaRequestUniqueFieldValuePO po = new OaRequestUniqueFieldValuePO();
        po.setTenantId(tenantId.getValue());
        po.setOaRequestId(requestId.getValue());
        po.setObjectType("OA_REQUEST");
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

    private Map<FieldCode, FieldDefinition> definitions(
            Collection<FieldDefinition> fields) {
        if (fields == null) {
            return Map.of();
        }
        return fields.stream().collect(Collectors.toMap(
                FieldDefinition::getCode, Function.identity()));
    }

    private Map<Long, FieldDefinition> fields(TenantId tenantId, Set<Long> fieldIds) {
        if (fieldIds.isEmpty()) {
            return Map.of();
        }
        return fieldMapper.selectList(new LambdaQueryWrapper<CustomFieldDefinitionPO>()
                        .eq(CustomFieldDefinitionPO::getTenantId, tenantId.getValue())
                        .eq(CustomFieldDefinitionPO::getObjectType, "OA_REQUEST")
                        .in(CustomFieldDefinitionPO::getId, fieldIds))
                .stream().map(fieldConverter::toDomain)
                .collect(Collectors.toMap(value -> value.getId().getValue(),
                        Function.identity()));
    }

    private LambdaQueryWrapper<OaRequestPO> rootFilter(TenantId tenantId) {
        return new LambdaQueryWrapper<OaRequestPO>()
                .eq(OaRequestPO::getTenantId, tenantId.getValue());
    }

    private void requireTenantMatch(TenantId tenantId, OaRequest request) {
        requireTenant(tenantId);
        if (request == null || request.getTenantId() == null
                || !tenantId.sameValueAs(request.getTenantId())) {
            throw new IllegalArgumentException("OA request tenant does not match");
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("OA request tenant is required");
        }
    }
}
