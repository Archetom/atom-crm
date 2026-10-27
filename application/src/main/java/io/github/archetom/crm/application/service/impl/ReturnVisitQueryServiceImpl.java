package io.github.archetom.crm.application.service.impl;

import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.security.ReturnVisitAccessResolver;
import io.github.archetom.crm.application.security.BusinessRecordAccessGuard;
import io.github.archetom.crm.application.service.ReturnVisitQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ReturnVisitVO;
import io.github.archetom.crm.application.vo.ReturnVisitOptionVO;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.ReturnVisitNotFoundException;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitQuery;
import io.github.archetom.crm.domain.model.ReturnVisitSort;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldValuePolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ReturnVisitRepository;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.ReturnVisitId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReturnVisitQueryServiceImpl implements ReturnVisitQueryService {
    private final ReturnVisitRepository repository;
    private final ReturnVisitAccessResolver accessResolver;
    private final BusinessRecordAccessGuard recordAccessGuard;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final SavedViewRepository savedViewRepository;
    private final ReturnVisitApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ReturnVisitVO>> query(AuthenticatedCaller caller, ReturnVisitQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.RETURN_VISIT_QUERY, new ServiceOperation<>() {
            private ReturnVisitAccessResolver.ReturnVisitAccess access;
            private SearchKeyword keyword;
            @Override public void validate() {
                if (request == null || request.getPage() == null || request.getSize() == null) {
                    throw invalid("Return visit query is invalid");
                }
                access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                        ReturnVisitAccessMode.READ);
                keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
            }
            @Override public Pager<ReturnVisitVO> execute() {
                Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess> fieldAccess =
                        support.accesses(access.tenantId(), access.memberId());
                List<FieldDefinition> definitions = support.fields(access.tenantId());
                if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                        && request.getOwnerActorId() != null) {
                    throw invalid("Return visit owner scenes are mutually exclusive");
                }
                MemberId owner = member(request.getOwnerActorId());
                if (owner != null || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
                    support.requireReadableUnmasked(fieldAccess, ReturnVisitFieldCatalog.OWNER_ACTOR);
                }
                if (request.getCustomerId() != null) {
                    support.requireReadableUnmasked(fieldAccess, ReturnVisitFieldCatalog.CUSTOMER);
                    requireVisible(caller, access.tenantId(), BusinessObjectType.CUSTOMER,
                            request.getCustomerId());
                }
                if (request.getContractId() != null) {
                    support.requireReadableUnmasked(fieldAccess, ReturnVisitFieldCatalog.CONTRACT);
                    requireVisible(caller, access.tenantId(), BusinessObjectType.CONTRACT,
                            request.getContractId());
                }
                if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
                    throw invalid("Return visit static and custom sort fields are mutually exclusive");
                }
                ReturnVisitSort sort;
                if (request.getCustomSortFieldCode() == null) {
                    sort = returnVisitSort(request.getSortField(), request.getSortDirection());
                } else if (request.getSortDirection() == null) {
                    throw invalid("Return visit custom sort must include direction");
                } else {
                    sort = customReturnVisitSort(request.getCustomSortFieldCode(),
                            request.getSortDirection(), definitions, fieldAccess);
                }
                if (!sort.isCustom() && !sort.isDefault()) {
                    support.requireReadableUnmasked(fieldAccess, sort.field().fieldCode().getValue());
                }
                var subordinateOwnerMemberIds = Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                        ? subordinateOwnerResolver.resolve(access.tenantId(), access.memberId(),
                        access.visibility().dataScope()) : null;
                List<FilterCondition> conditions = conditions(request.getSavedViewId(),
                        request.getConditions(), caller, access, definitions, fieldAccess);
                var page = repository.findReturnVisits(access.tenantId(), new ReturnVisitQuery(
                        request.getPage(), request.getSize(),
                        keyword == null ? null : keyword.value(),
                        FieldAccessPolicy.searchableCandidates(definitions,
                                fieldAccess, ReturnVisitFieldCatalog.searchCandidates()),
                        owner,
                        subordinateOwnerMemberIds,
                        customer(request.getCustomerId()), contract(request.getContractId()),
                        sort, conditions, access.visibility()));
                Pager<ReturnVisitVO> result = new Pager<>();
                result.setPageNum(page.page()); result.setPageSize(page.size()); result.setTotalNum(page.total());
                result.setObjectList(support.toVOs(caller, access.tenantId(), page.items(), fieldAccess));
                return result;
            }
        });
    }

    private ReturnVisitSort returnVisitSort(String field, String direction) {
        try {
            return ReturnVisitSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw invalid("Return visit sort is invalid");
        }
    }

    private ReturnVisitSort customReturnVisitSort(
            String requestedCode, String direction, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> accesses) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw invalid("Return visit custom sort field is invalid");
        }
        FieldDefinition field = fields.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || !field.isActive() || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw invalid("Return visit custom sort field is unavailable");
        }
        support.requireReadableUnmasked(accesses, code.getValue());
        try {
            return ReturnVisitSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw invalid("Return visit sort is invalid");
        }
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ReturnVisitAccessResolver.ReturnVisitAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (viewId != null && requested != null && !requested.isEmpty()) {
            throw invalid("Saved view and temporary filters cannot be combined");
        }
        if (viewId == null && requested != null && !requested.isEmpty()) {
            return temporaryConditions(requested, caller, access, fields, fieldAccess);
        }
        if (viewId == null) {
            return List.of();
        }
        SavedView view;
        try {
            view = savedViewRepository.findById(access.tenantId(), access.memberId(),
                            new SavedViewId(viewId))
                    .orElseThrow(() -> invalid("Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw invalid("Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.RETURN_VISIT) {
            throw invalid("Saved view is not a return visit view");
        }
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(searchableTypes);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream().map(condition ->
                    ReturnVisitFieldValuePolicy.normalizeFilterCondition(
                            searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw invalid("Return visit saved view condition is invalid");
        }
        validateConditions(caller, access, fieldAccess, normalized);
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ReturnVisitAccessResolver.ReturnVisitAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = searchableFields(fields);
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFieldType()));
        List<FilterCondition> normalized;
        try {
            List<FilterCondition> values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Return visit condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            normalized = values.stream().map(condition -> ReturnVisitFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw invalid("Return visit temporary filter condition is invalid");
        }
        validateConditions(caller, access, fieldAccess, normalized);
        return normalized;
    }

    private Map<FieldCode, FieldDefinition> searchableFields(List<FieldDefinition> fields) {
        return fields.stream().filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode, Function.identity(),
                        (left, right) -> left));
    }

    private void validateConditions(
            AuthenticatedCaller caller, ReturnVisitAccessResolver.ReturnVisitAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess,
            List<FilterCondition> conditions) {
        conditions.forEach(condition -> {
            support.requireReadableUnmasked(fieldAccess, condition.fieldCode().getValue());
            validateRelationCondition(caller, access, condition);
        });
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, ReturnVisitAccessResolver.ReturnVisitAccess access,
            FilterCondition condition) {
        BusinessObjectType type = null;
        if (new FieldCode(ReturnVisitFieldCatalog.CUSTOMER).equals(condition.fieldCode())) {
            type = BusinessObjectType.CUSTOMER;
        } else if (new FieldCode(ReturnVisitFieldCatalog.CONTACT).equals(condition.fieldCode())) {
            type = BusinessObjectType.CONTACT;
        } else if (new FieldCode(ReturnVisitFieldCatalog.CONTRACT).equals(condition.fieldCode())) {
            type = BusinessObjectType.CONTRACT;
        }
        if (type == null || condition.operator() == FilterOperator.IS_EMPTY
                || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
            return;
        }
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw invalid("Return visit relation filter operator is invalid");
        }
        for (String value : condition.values()) {
            try {
                var tenantId = recordAccessGuard.requireVisible(caller, type,
                        Long.parseLong(value), false);
                if (!access.tenantId().sameValueAs(tenantId)) {
                    throw invalid("Return visit relation filter tenant is invalid");
                }
            } catch (NumberFormatException exception) {
                throw invalid("Return visit relation filter ID is invalid");
            }
        }
    }

    @Override
    public Result<List<ReturnVisitOptionVO>> options(
            AuthenticatedCaller caller, String keyword, Integer limitValue) {
        return queryTemplate.execute(UseCaseOperation.RETURN_VISIT_OPTION_QUERY,
                new ServiceOperation<>() {
                    private ReturnVisitAccessResolver.ReturnVisitAccess access;
                    private int limit;

                    @Override
                    public void validate() {
                        if (keyword != null && keyword.trim().codePointCount(0, keyword.trim().length()) > 64) {
                            throw invalid("Return visit option keyword is invalid");
                        }
                        limit = limitValue == null ? 20 : limitValue;
                        if (limit < 1 || limit > 100) {
                            throw invalid("Return visit option limit is invalid");
                        }
                        access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                                ReturnVisitAccessMode.READ);
                    }

                    @Override
                    public List<ReturnVisitOptionVO> execute() {
                        Map<io.github.archetom.crm.domain.valueobject.FieldCode, EffectiveFieldAccess>
                                fieldAccess = support.accesses(access.tenantId(), access.memberId());
                        support.requireReadableUnmasked(fieldAccess, ReturnVisitFieldCatalog.NUMBER);
                        support.requireReadableUnmasked(fieldAccess, ReturnVisitFieldCatalog.VISIT_TIME);
                        return repository.findOptions(access.tenantId(), access.visibility(), keyword, limit)
                                .stream().map(value -> new ReturnVisitOptionVO(value.getId().getValue(),
                                        value.getVisitNumber(), value.getVisitTime())).toList();
                    }
                });
    }

    @Override
    public Result<ReturnVisitVO> get(AuthenticatedCaller caller, Long returnVisitIdValue) {
        return queryTemplate.execute(UseCaseOperation.RETURN_VISIT_GET, new ServiceOperation<>() {
            private ReturnVisitAccessResolver.ReturnVisitAccess access; private ReturnVisitId id;
            @Override public void validate() {
                id = id(returnVisitIdValue);
                access = accessResolver.access(caller, AuthorityCatalog.RETURN_VISITS_READ,
                        ReturnVisitAccessMode.READ);
            }
            @Override public ReturnVisitVO execute() {
                ReturnVisit value = repository.findVisibleById(access.tenantId(), id, access.visibility())
                        .orElseThrow(ReturnVisitNotFoundException::new);
                return support.toVO(caller, access.tenantId(), value,
                        support.accesses(access.tenantId(), access.memberId()));
            }
        });
    }

    private ReturnVisitId id(Long value) { try { return new ReturnVisitId(value); } catch (RuntimeException e) { throw invalid("Return visit ID is invalid"); } }
    private MemberId member(Long value) { try { return value == null ? null : new MemberId(value); } catch (RuntimeException e) { throw invalid("Return visit owner is invalid"); } }
    private CustomerId customer(Long value) { try { return value == null ? null : new CustomerId(value); } catch (RuntimeException e) { throw invalid("Return visit customer is invalid"); } }
    private ContractId contract(Long value) { try { return value == null ? null : new ContractId(value); } catch (RuntimeException e) { throw invalid("Return visit contract is invalid"); } }
    private void requireVisible(AuthenticatedCaller caller, io.github.archetom.crm.domain.valueobject.TenantId tenantId,
                                BusinessObjectType type, Long id) {
        var actual = recordAccessGuard.requireVisible(caller, type, id, false);
        if (!tenantId.sameValueAs(actual)) throw invalid("Return visit filter tenant is invalid");
    }
    private NonRetryableApplicationException invalid(String message) { return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message); }
}
