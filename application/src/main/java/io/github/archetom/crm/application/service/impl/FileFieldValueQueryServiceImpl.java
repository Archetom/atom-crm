package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OaRequestAccessResolver;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccess;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.service.FileFieldValueQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.AttachmentVO;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.FileFieldRepository;
import io.github.archetom.crm.domain.repository.OaRequestRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies the files + target/OA visibility + unmasked field READ intersection at assembly time. */
@Service
@RequiredArgsConstructor
public class FileFieldValueQueryServiceImpl implements FileFieldValueQueryService {
    private final FileFieldRepository fileFieldRepository;
    private final OaRequestRepository oaRequestRepository;
    private final OrganizationAccessGuard accessGuard;
    private final OaRequestAccessResolver oaAccessResolver;
    private final AttachmentTargetAccessRegistry targetAccessRegistry;
    private final BusinessRecordAccessGuard businessRecordAccessGuard;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final AttachmentApplicationSupport attachmentSupport;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Map<String, List<AttachmentVO>>> crmValues(AuthenticatedCaller caller,
                                                               String rawTargetType, Long rawTargetId) {
        return queryTemplate.execute(UseCaseOperation.ATTACHMENT_QUERY, new ServiceOperation<>() {
            private TenantId tenantId; private AttachmentTarget target;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                target = target(rawTargetType, rawTargetId);
            }
            @Override public Map<String, List<AttachmentVO>> execute() {
                TenantId targetTenant = targetAccessRegistry.require(target.type()).authorizeRead(caller, target);
                if (!tenantId.sameValueAs(targetTenant)) throw attachmentSupport.notFound();
                return assemble(fileFieldRepository.findCrmValues(tenantId, target),
                        fieldAccessResolver.resolve(tenantId,
                                new io.github.archetom.crm.domain.valueobject.MemberId(caller.actorId()),
                                businessType(target.type())));
            }
        });
    }

    @Override
    public Result<Map<Long, Map<String, List<AttachmentVO>>>> crmValues(
            AuthenticatedCaller caller, String rawTargetType, List<Long> rawTargetIds) {
        return queryTemplate.execute(UseCaseOperation.ATTACHMENT_QUERY, new ServiceOperation<>() {
            private TenantId tenantId;
            private AttachmentTargetType targetType;
            private List<Long> targetIds;

            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                targetIds = rawTargetIds == null ? List.of() : rawTargetIds.stream()
                        .filter(id -> id != null && id > 0).distinct().toList();
                // The type is validated even for an empty page so a bad caller cannot probe it.
                targetType = target(rawTargetType, 1L).type();
            }

            @Override public Map<Long, Map<String, List<AttachmentVO>>> execute() {
                if (targetIds.isEmpty()) return Map.of();
                Set<Long> visible = visibleTargets(caller, tenantId, targetType, targetIds);
                if (visible.isEmpty()) return Map.of();
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = fieldAccessResolver.resolve(
                        tenantId, new io.github.archetom.crm.domain.valueobject.MemberId(caller.actorId()),
                        businessType(targetType));
                Map<Long, Map<FieldCode, List<FileFieldRepository.BoundFile>>> values =
                        fileFieldRepository.findCrmValues(tenantId, targetType, visible);
                LinkedHashMap<Long, Map<String, List<AttachmentVO>>> result = new LinkedHashMap<>();
                values.forEach((targetId, byField) ->
                        result.put(targetId, assemble(byField, fieldAccess)));
                return Map.copyOf(result);
            }
        });
    }

    /**
     * Resolves which of the requested targets the caller may still read.
     *
     * <p>The batch guard applies the same authority and data scope the per-target path applies, in
     * one query. Product and return visit have no batch resolver, so they keep the per-target
     * check; an invisible target is dropped rather than failing the whole page, which matches the
     * per-row behaviour where one hidden row simply carried no FILE values.</p>
     */
    private Set<Long> visibleTargets(AuthenticatedCaller caller, TenantId tenantId,
                                      AttachmentTargetType targetType, List<Long> targetIds) {
        if (targetType == AttachmentTargetType.PRODUCT
                || targetType == AttachmentTargetType.RETURN_VISIT) {
            LinkedHashSet<Long> visible = new LinkedHashSet<>();
            AttachmentTargetAccess access = targetAccessRegistry.require(targetType);
            for (Long targetId : targetIds) {
                try {
                    TenantId targetTenant = access.authorizeRead(
                            caller, new AttachmentTarget(targetType, targetId));
                    if (tenantId.sameValueAs(targetTenant)) visible.add(targetId);
                } catch (RuntimeException hiddenTarget) {
                    // Not visible to this caller; it contributes no FILE values.
                }
            }
            return visible;
        }
        return businessRecordAccessGuard.visibleSubjectIds(
                caller, tenantId, businessType(targetType), targetIds);
    }

    @Override
    public Result<Map<String, List<AttachmentVO>>> oaValues(AuthenticatedCaller caller, Long rawRequestId) {
        return queryTemplate.execute(UseCaseOperation.OA_REQUEST_GET, new ServiceOperation<>() {
            private TenantId tenantId; private OaRequestId requestId;
            private OaRequestAccessResolver.OaRequestAccess access;
            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                access = oaAccessResolver.access(caller);
                try { requestId = new OaRequestId(rawRequestId); }
                catch (RuntimeException exception) { throw attachmentSupport.notFound(); }
                if (!tenantId.sameValueAs(access.tenantId())) throw attachmentSupport.notFound();
            }
            @Override public Map<String, List<AttachmentVO>> execute() {
                oaRequestRepository.findVisibleById(tenantId, requestId, access.memberId(), access.tenantManager())
                        .orElseThrow(attachmentSupport::notFound);
                return assemble(fileFieldRepository.findOaValues(tenantId, requestId),
                        fieldAccessResolver.resolve(tenantId, access.memberId(), BusinessObjectType.OA_REQUEST));
            }
        });
    }

    @Override
    public Result<Map<Long, Map<String, List<AttachmentVO>>>> oaValues(
            AuthenticatedCaller caller, List<Long> rawRequestIds) {
        return queryTemplate.execute(UseCaseOperation.OA_REQUEST_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private OaRequestAccessResolver.OaRequestAccess access;
            private List<Long> requestIds;

            @Override public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.FILES_READ);
                access = oaAccessResolver.access(caller);
                if (!tenantId.sameValueAs(access.tenantId())) throw attachmentSupport.notFound();
                requestIds = rawRequestIds == null ? List.of() : rawRequestIds.stream()
                        .filter(id -> id != null && id > 0).distinct().toList();
            }

            @Override public Map<Long, Map<String, List<AttachmentVO>>> execute() {
                if (requestIds.isEmpty()) return Map.of();
                Set<Long> visible = businessRecordAccessGuard.visibleSubjectIds(
                        caller, tenantId, BusinessObjectType.OA_REQUEST, requestIds);
                if (visible.isEmpty()) return Map.of();
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = fieldAccessResolver.resolve(
                        tenantId, access.memberId(), BusinessObjectType.OA_REQUEST);
                Map<Long, Map<FieldCode, List<FileFieldRepository.BoundFile>>> values =
                        fileFieldRepository.findOaValues(tenantId,
                                visible.stream().map(OaRequestId::new).toList());
                LinkedHashMap<Long, Map<String, List<AttachmentVO>>> result = new LinkedHashMap<>();
                values.forEach((requestId, byField) ->
                        result.put(requestId, assemble(byField, fieldAccess)));
                return Map.copyOf(result);
            }
        });
    }

    private Map<String, List<AttachmentVO>> assemble(
            Map<FieldCode, List<FileFieldRepository.BoundFile>> values,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        LinkedHashMap<String, List<AttachmentVO>> result = new LinkedHashMap<>();
        values.forEach((field, files) -> {
            EffectiveFieldAccess access = accesses.get(field);
            if (access == null || !access.accessLevel().covers(FieldAccessLevel.READ)
                    || access.maskType() != FieldMaskType.NONE) return;
            result.put(field.getValue(), files.stream().map(FileFieldRepository.BoundFile::file)
                    .map(attachmentSupport::toVO).toList());
        });
        return Map.copyOf(result);
    }

    private static AttachmentTarget target(String raw, Long id) {
        if (raw == null || id == null || id <= 0) throw unavailable();
        AttachmentTargetType type = switch (raw.trim()) {
            case "customer" -> AttachmentTargetType.CUSTOMER;
            case "contact" -> AttachmentTargetType.CONTACT;
            case "lead" -> AttachmentTargetType.LEAD;
            case "opportunity" -> AttachmentTargetType.OPPORTUNITY;
            case "contract" -> AttachmentTargetType.CONTRACT;
            case "invoice" -> AttachmentTargetType.INVOICE;
            case "receivable" -> AttachmentTargetType.RECEIVABLE;
            case "receivable-plan" -> AttachmentTargetType.RECEIVABLE_PLAN;
            case "product" -> AttachmentTargetType.PRODUCT;
            case "return-visit" -> AttachmentTargetType.RETURN_VISIT;
            default -> throw unavailable();
        };
        return new AttachmentTarget(type, id);
    }

    private static BusinessObjectType businessType(AttachmentTargetType type) {
        return switch (type) {
            case CUSTOMER -> BusinessObjectType.CUSTOMER;
            case CONTACT -> BusinessObjectType.CONTACT;
            case LEAD -> BusinessObjectType.LEAD;
            case OPPORTUNITY -> BusinessObjectType.OPPORTUNITY;
            case CONTRACT -> BusinessObjectType.CONTRACT;
            case INVOICE -> BusinessObjectType.INVOICE;
            case RECEIVABLE -> BusinessObjectType.RECEIVABLE;
            case RECEIVABLE_PLAN -> BusinessObjectType.RECEIVABLE_PLAN;
            case PRODUCT -> BusinessObjectType.PRODUCT;
            case RETURN_VISIT -> BusinessObjectType.RETURN_VISIT;
            case WORK_TASK, MARKETING_ACTIVITY, OA_ANNOUNCEMENT, OA_REQUEST, IMPORT_DRAFT -> throw unavailable();
        };
    }

    private static NonRetryableApplicationException unavailable() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "file field is unavailable");
    }
}
