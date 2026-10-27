package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.crm.application.vo.StageFieldCandidateVO;
import io.github.archetom.crm.domain.entity.ApprovalDefinition;
import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.entity.StageWorkflowDefinition;
import io.github.archetom.crm.domain.model.ApprovalDefinitionStatus;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.StageDefinition;
import io.github.archetom.crm.domain.policy.StageApprovalFactPolicy;
import io.github.archetom.crm.domain.repository.ApprovalDefinitionRepository;
import io.github.archetom.crm.domain.repository.DepartmentRepository;
import io.github.archetom.crm.domain.repository.MemberProfileRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.StageWorkflowDefinitionRepository;
import io.github.archetom.crm.domain.valueobject.ApprovalDefinitionId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.StageWorkflowId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The checks and lookups that both halves of the stage-workflow service need.
 *
 * <p>Workflow definitions and workflow records are separate concerns, and the REST layer already
 * treats them that way. They still share a small core: resolving a workflow or an approval
 * definition, refusing a cross-tenant or stale write, and shaping a candidate page. Holding that
 * core here is what lets the two halves live in their own classes without either one reaching into
 * the other, and without the shared checks being duplicated into two copies that drift.</p>
 *
 * <p>These are deliberately only the shared ones. Anything reachable from a single side stays with
 * that side, so this does not become a second home for the logic it was extracted from.</p>
 */
@Component
@RequiredArgsConstructor
public class StageWorkflowSharedSupport {

    private final StageWorkflowDefinitionRepository workflowRepository;
    private final ApprovalDefinitionRepository approvalDefinitionRepository;
    private final ApprovalApplicationSupport support;
    private final MemberProfileRepository memberProfileRepository;
    private final DepartmentRepository departmentRepository;

    public Pager<StageFieldCandidateVO> actorCandidates(
            TenantId tenantId, String keyword, int page, int size) {
        PageResult<MemberProfile> profiles = memberProfileRepository.findActiveByTenant(
                tenantId, blankToNull(keyword), page, size);
        return candidatePager(profiles.page(), profiles.size(), profiles.total(), profiles.items().stream()
                .filter(MemberProfile::isActive)
                .map(profile -> new StageFieldCandidateVO(profile.getId().getValue(),
                        profile.getDisplayName(), "ACTOR_ID")).toList());
    }

    public Pager<StageFieldCandidateVO> departmentCandidates(
            TenantId tenantId, String keyword, int page, int size) {
        String normalized = blankToNull(keyword);
        List<Department> all = departmentRepository.findAll(tenantId).stream()
                .filter(Department::isActive)
                .filter(value -> normalized == null || value.getName().toLowerCase(Locale.ROOT)
                        .contains(normalized.toLowerCase(Locale.ROOT)))
                .sorted(java.util.Comparator.comparing(Department::getName)
                        .thenComparing(value -> value.getId().getValue()))
                .toList();
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return candidatePager((long) page, (long) size, (long) all.size(), all.subList(from, to).stream()
                .map(value -> new StageFieldCandidateVO(value.getId().getValue(), value.getName(),
                        "DEPARTMENT_ID")).toList());
    }

    public Pager<StageFieldCandidateVO> candidatePager(
            Long page, Long size, Long total, List<StageFieldCandidateVO> items) {
        Pager<StageFieldCandidateVO> result = new Pager<>();
        result.setPageNum(page); result.setPageSize(size); result.setTotalNum(total);
        result.setObjectList(items == null ? List.of() : items);
        return result;
    }

    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public void validateApprovalReferences(TenantId tenantId,
                                            BusinessObjectType objectType,
                                            List<StageDefinition> stages,
                                            boolean requireActive) {
        for (StageDefinition stage : stages) {
            if (stage.approvalDefinitionId() == null) {
                continue;
            }
            ApprovalDefinition definition = requireApprovalDefinition(
                    tenantId, stage.approvalDefinitionId());
            if (definition.getObjectType() != objectType
                    || definition.getStatus() == ApprovalDefinitionStatus.DELETED
                    || requireActive && !definition.isActive()) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage approval definition is unavailable");
            }
            if (!StageApprovalFactPolicy.isWithinLimit(stage, definition)) {
                throw error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Stage approval fact fields exceed the supported limit");
            }
        }
    }

    public StageWorkflowDefinition requireWorkflow(TenantId tenantId, StageWorkflowId id) {
        return workflowRepository.findById(tenantId, id).orElseThrow(() ->
                error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Stage workflow was not found"));
    }

    public ApprovalDefinition requireApprovalDefinition(
            TenantId tenantId, ApprovalDefinitionId id) {
        return approvalDefinitionRepository.findById(tenantId, id).orElseThrow(() ->
                error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Stage approval definition was not found"));
    }

    public StageWorkflowId workflowId(Long value) {
        try {
            return new StageWorkflowId(value);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Stage workflow ID is invalid");
        }
    }

    public void requireVersion(Long actual, Long expected, String message) {
        if (expected == null || !expected.equals(actual)) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, message);
        }
    }

    public void requireSameTenant(TenantId expected, TenantId actual) {
        if (!expected.sameValueAs(actual)) {
            throw error(ApplicationErrorCode.ACCESS_DENIED,
                    "Business record tenant does not match");
        }
    }

    public void require(Object value, String description) {
        if (value == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " is required");
        }
    }

    public io.github.archetom.crm.shared.exception.NonRetryableApplicationException error(
            ApplicationErrorCode code, String message) {
        return support.error(code, message);
    }

    public BusinessObjectType relationTarget(BusinessObjectType source, FieldCode code) {
        String value = code == null ? null : code.getValue();
        if (value == null) {
            return null;
        }
        return switch (source) {
            case CUSTOMER -> "parent_customer".equals(value) ? BusinessObjectType.CUSTOMER : null;
            case CONTACT -> switch (value) {
                case "customer" -> BusinessObjectType.CUSTOMER;
                case "parent_contact" -> BusinessObjectType.CONTACT;
                default -> null;
            };
            case LEAD -> switch (value) {
                case "customer" -> BusinessObjectType.CUSTOMER;
                case "parent_lead" -> BusinessObjectType.LEAD;
                default -> null;
            };
            case OPPORTUNITY -> switch (value) {
                case "customer" -> BusinessObjectType.CUSTOMER;
                case "primary_contact", "contacts" -> BusinessObjectType.CONTACT;
                default -> null;
            };
            default -> null;
        };
    }

    public <S, T> Pager<T> pager(PageResult<S> page, Function<S, T> converter) {
        Pager<T> result = new Pager<>();
        result.setPageNum(page.page());
        result.setPageSize(page.size());
        result.setTotalNum(page.total());
        result.setObjectList(page.items().stream().map(converter).toList());
        return result;
    }

    public FieldCode stageFieldCode(String raw) {
        try {
            return new FieldCode(raw);
        } catch (RuntimeException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, "Stage field code is invalid");
        }
    }

    public int page(Integer value) {
        return value == null ? 1 : value;
    }

    public int size(Integer value) {
        return value == null ? 20 : value;
    }
}
