package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.SavedViewCreateRequest;
import io.github.archetom.crm.api.dto.request.SavedViewConditionRequest;
import io.github.archetom.crm.api.dto.request.SavedViewUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.SavedViewService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.SavedViewConditionVO;
import io.github.archetom.crm.application.vo.SavedViewVO;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.entity.SavedViewCondition;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.FieldDefinitionRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavedViewServiceImpl implements SavedViewService {

    private final SavedViewRepository viewRepository;
    private final FieldDefinitionRepository fieldRepository;
    private final CustomizationObjectLock objectLock;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<List<SavedViewVO>> list(
            AuthenticatedCaller caller, String objectType, boolean includeHidden) {
        return queryTemplate.execute(UseCaseOperation.SAVED_VIEW_LIST, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.SAVED_VIEWS_READ);
                memberId = new MemberId(caller.actorId());
                type = parse(BusinessObjectType.class, objectType, "Business object type");
            }

            @Override
            public List<SavedViewVO> execute() {
                Map<FieldCode, CustomFieldType> searchable = searchableFields(tenantId, type);
                return viewRepository.findAll(tenantId, memberId, type, includeHidden)
                        .stream().filter(view -> view.isApplicableTo(searchable))
                        .map(SavedViewServiceImpl.this::toVO).toList();
            }
        });
    }

    @Override
    public Result<SavedViewVO> create(AuthenticatedCaller caller, SavedViewCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.SAVED_VIEW_CREATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.SAVED_VIEWS_WRITE);
                memberId = new MemberId(caller.actorId());
                require(request, "Saved view create request");
                type = parse(BusinessObjectType.class, request.getObjectType(), "Business object type");
            }

            @Override
            public SavedViewVO execute() {
                lockObject(tenantId, type);
                Map<FieldCode, CustomFieldType> searchable = searchableFields(tenantId, type);
                SavedView view = SavedView.createWithSavedConditions(
                        tenantId, memberId, type, request.getName(),
                        conditions(request.getConditions()), value(request.getSortOrder()),
                        Boolean.TRUE.equals(request.getHidden()), searchable);
                return toVO(viewRepository.save(tenantId, view));
            }
        });
    }

    @Override
    public Result<SavedViewVO> update(
            AuthenticatedCaller caller, Long viewId, SavedViewUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.SAVED_VIEW_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private SavedViewId id;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.SAVED_VIEWS_WRITE);
                memberId = new MemberId(caller.actorId());
                id = new SavedViewId(viewId);
                require(request, "Saved view update request");
                type = requireView(tenantId, memberId, id).getObjectType();
            }

            @Override
            public SavedViewVO execute() {
                lockObject(tenantId, type);
                SavedView view = requireView(tenantId, memberId, id);
                requireVersion(view, request.getVersion());
                view.updateSavedConditions(request.getName(), conditions(request.getConditions()),
                        value(request.getSortOrder()), Boolean.TRUE.equals(request.getHidden()),
                        searchableFields(tenantId, view.getObjectType()));
                return toVO(viewRepository.save(tenantId, view));
            }
        });
    }

    @Override
    public Result<SavedViewVO> setDefault(
            AuthenticatedCaller caller, Long viewId, Long version) {
        return commandTemplate.execute(UseCaseOperation.SAVED_VIEW_DEFAULT_SET, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private SavedViewId id;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.SAVED_VIEWS_WRITE);
                memberId = new MemberId(caller.actorId());
                id = new SavedViewId(viewId);
                type = requireView(tenantId, memberId, id).getObjectType();
            }

            @Override
            public SavedViewVO execute() {
                lockObject(tenantId, type);
                SavedView view = requireView(tenantId, memberId, id);
                requireVersion(view, version);
                view.assertApplicableTo(searchableFields(tenantId, view.getObjectType()));
                view.markDefault();
                return toVO(viewRepository.saveAsDefault(tenantId, view));
            }
        });
    }

    @Override
    public Result<Void> delete(AuthenticatedCaller caller, Long viewId, Long version) {
        return commandTemplate.execute(UseCaseOperation.SAVED_VIEW_DELETE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;
            private SavedViewId id;
            private BusinessObjectType type;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.SAVED_VIEWS_WRITE);
                memberId = new MemberId(caller.actorId());
                id = new SavedViewId(viewId);
                type = requireView(tenantId, memberId, id).getObjectType();
            }

            @Override
            public Void execute() {
                lockObject(tenantId, type);
                SavedView view = requireView(tenantId, memberId, id);
                requireVersion(view, version);
                viewRepository.delete(tenantId, view);
                return null;
            }
        });
    }

    private SavedView requireView(TenantId tenantId, MemberId memberId, SavedViewId id) {
        return viewRepository.findById(tenantId, memberId, id)
                .orElseThrow(() -> error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                        "Saved view was not found"));
    }

    private void lockObject(TenantId tenantId, BusinessObjectType objectType) {
        objectLock.acquire(tenantId, objectType);
    }

    private Map<FieldCode, CustomFieldType> searchableFields(
            TenantId tenantId, BusinessObjectType objectType) {
        return fieldRepository.findAll(tenantId, objectType, false).stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        FieldDefinition::getFieldType, (left, right) -> left));
    }

    private List<SavedViewCondition> conditions(List<SavedViewConditionRequest> requests) {
        return (requests == null ? List.<SavedViewConditionRequest>of() : requests).stream()
                .map(request -> {
                    if (request == null) {
                        throw error(ApplicationErrorCode.PARAMETER_INVALID,
                                "Saved view condition is invalid");
                    }
                    return new SavedViewCondition(new FilterCondition(
                            new FieldCode(request.getFieldCode()),
                            parse(FilterOperator.class, request.getOperator(), "Filter operator"),
                            request.getValues()), Boolean.TRUE.equals(request.getExposed()));
                }).toList();
    }

    private SavedViewVO toVO(SavedView view) {
        return new SavedViewVO(view.getId().getValue(), view.getObjectType().name(), view.getName(),
                view.getSavedConditions().stream().map(condition -> new SavedViewConditionVO(
                        condition.filterCondition().fieldCode().getValue(),
                        condition.filterCondition().operator().name(),
                        condition.filterCondition().values(), condition.exposed()))
                        .toList(),
                view.getSortOrder(), view.isHidden(), view.isDefaultView(), view.getVersion(),
                view.getCreatedTime(), view.getUpdatedTime());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireVersion(SavedView view, Long version) {
        if (version == null || !version.equals(view.getVersion())) {
            throw error(ApplicationErrorCode.VERSION_CONFLICT, "Saved view version is stale");
        }
    }

    private void require(Object value, String description) {
        if (value == null) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " must not be empty");
        }
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, String description) {
        if (value == null || value.isBlank()) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error(ApplicationErrorCode.PARAMETER_INVALID, description + " is invalid");
        }
    }

    private NonRetryableApplicationException error(ApplicationErrorCode code, String message) {
        return new NonRetryableApplicationException(code, message);
    }
}
