package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContactAccessResolver.ContactAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.EffectiveFieldAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver;
import io.github.archetom.crm.application.security.OpportunityAccessResolver.OpportunityAccess;
import io.github.archetom.crm.application.service.OpportunityQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.OpportunityCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityCollaboratorVO;
import io.github.archetom.crm.application.vo.OpportunityContactVO;
import io.github.archetom.crm.application.vo.OpportunityContactsBaselineVO;
import io.github.archetom.crm.application.vo.OpportunityEditableBaselineVO;
import io.github.archetom.crm.application.vo.OpportunitySummaryVO;
import io.github.archetom.crm.application.vo.OpportunityVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.Opportunity;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.exception.OpportunityNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ActivitySubjectSummary;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityQuery;
import io.github.archetom.crm.domain.model.OpportunitySort;
import io.github.archetom.crm.domain.model.OpportunitySummary;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldValuePolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.OpportunityStarRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpportunityQueryServiceImpl implements OpportunityQueryService {

    private final OpportunityRepository opportunityRepository;
    private final OpportunityStarRepository starRepository;
    private final ActivityRepository activityRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final SavedViewRepository savedViewRepository;
    private final OpportunityAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final EffectiveFieldAccessResolver fieldAccessResolver;
    private final OpportunityApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<OpportunityVO>> query(
            AuthenticatedCaller caller, OpportunityQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_QUERY,
                new ServiceOperation<>() {
                    private OpportunityAccess access;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity query request");
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                    }

                    @Override
                    public Pager<OpportunityVO> execute() {
                        QueryContext context = queryContext(caller, access, request);
                        PageResult<Opportunity> result = opportunityRepository
                                .findOpportunities(access.tenantId(), context.query());
                        List<OpportunityVO> values = values(
                                caller, access, result.items(), context.fieldAccess());
                        Pager<OpportunityVO> pager = pager(
                                result.page(), result.size(), result.total());
                        pager.setObjectList(values);
                        return pager;
                    }
                });
    }

    @Override
    public Result<OpportunityVO> get(
            AuthenticatedCaller caller, Long opportunityId) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_GET,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        id = id(opportunityId);
                    }

                    @Override
                    public OpportunityVO execute() {
                        Opportunity opportunity = requireVisible(access, id);
                        return values(caller, access, List.of(opportunity),
                                support.accesses(access.tenantId(), access.memberId()))
                                .getFirst();
                    }
                });
    }

    @Override
    public Result<OpportunityEditableBaselineVO> editableBaseline(
            AuthenticatedCaller caller, Long opportunityId) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_EDITABLE_BASELINE_GET,
                new ServiceOperation<>() {
                    private OpportunityAccess writeAccess;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        accessResolver.access(caller, AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        writeAccess = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_WRITE,
                                OpportunityAccessMode.WRITE);
                        id = id(opportunityId);
                    }

                    @Override
                    public OpportunityEditableBaselineVO execute() {
                        Opportunity opportunity = requireVisible(writeAccess, id);
                        return support.editableBaseline(opportunity,
                                support.accesses(writeAccess.tenantId(), writeAccess.memberId()));
                    }
                });
    }

    @Override
    public Result<OpportunityCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long opportunityId) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_COLLABORATOR_GET,
                new ServiceOperation<>() {
                    private OpportunityAccess access;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.OPPORTUNITIES_ASSIGN,
                                OpportunityAccessMode.OWNER_SCOPE);
                        id = id(opportunityId);
                    }

                    @Override
                    public OpportunityCollaboratorBaselineVO execute() {
                        Opportunity opportunity = requireVisible(access, id);
                        String callerState = opportunity.getOwnerMemberId().sameValueAs(access.memberId())
                                ? "OWNER" : opportunity.getCollaborators().stream()
                                .filter(value -> value.memberId().sameValueAs(access.memberId()))
                                .map(value -> value.access().name()).findFirst().orElse("NONE");
                        List<OpportunityCollaboratorVO> collaborators = opportunity.getCollaborators()
                                .stream().map(value -> new OpportunityCollaboratorVO(
                                        value.memberId().getValue(), value.access().name())).toList();
                        return new OpportunityCollaboratorBaselineVO(opportunity.getVersion(),
                                collaborators, callerState);
                    }
                });
    }

    @Override
    public Result<OpportunityContactsBaselineVO> contactsBaseline(
            AuthenticatedCaller caller, Long opportunityId) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_CONTACT_BASELINE_GET,
                new ServiceOperation<>() {
                    private OpportunityAccess writeAccess;
                    private ContactAccess contactAccess;
                    private OpportunityId id;

                    @Override
                    public void validate() {
                        accessResolver.access(caller, AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        writeAccess = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_WRITE,
                                OpportunityAccessMode.WRITE);
                        contactAccess = contactAccessResolver.access(caller,
                                AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
                        id = id(opportunityId);
                    }

                    @Override
                    public OpportunityContactsBaselineVO execute() {
                        Opportunity opportunity = requireVisible(writeAccess, id);
                        Map<FieldCode, EffectiveFieldAccess> opportunityFields =
                                support.accesses(writeAccess.tenantId(), writeAccess.memberId());
                        if (!support.canReturnContactsBaseline(opportunityFields)) {
                            return unavailableContactsBaseline(opportunity);
                        }
                        Map<FieldCode, EffectiveFieldAccess> contactFields =
                                fieldAccessResolver.resolve(contactAccess.tenantId(),
                                        contactAccess.memberId(), BusinessObjectType.CONTACT);
                        EffectiveFieldAccess nameAccess = contactFields.getOrDefault(
                                new FieldCode(ContactFieldCatalog.NAME),
                                EffectiveFieldAccess.hidden());
                        if (nameAccess.accessLevel() == FieldAccessLevel.HIDDEN) {
                            return unavailableContactsBaseline(opportunity);
                        }
                        List<Contact> visible = opportunity.getContactIds().isEmpty()
                                ? List.of() : contactRepository.findVisibleByIds(
                                        contactAccess.tenantId(), opportunity.getContactIds(),
                                        contactAccess.visibility(),
                                        opportunity.getContactIds().size());
                        Map<ContactId, Contact> byId = visible.stream().collect(
                                Collectors.toMap(Contact::getId, Function.identity()));
                        if (!byId.keySet().containsAll(opportunity.getContactIds())) {
                            return unavailableContactsBaseline(opportunity);
                        }
                        List<OpportunityContactVO> contacts = opportunity.getContactIds().stream()
                                .map(byId::get).map(contact -> new OpportunityContactVO(
                                        contact.getId().getValue(),
                                        masked(contact.getName(), nameAccess),
                                        opportunity.getPrimaryContactId() != null
                                                && opportunity.getPrimaryContactId()
                                                .sameValueAs(contact.getId())))
                                .toList();
                        return new OpportunityContactsBaselineVO(opportunity.getId().getValue(),
                                opportunity.getVersion(), true,
                                opportunity.getCustomerId().getValue(), contacts);
                    }
                });
    }

    @Override
    public Result<Pager<OpportunityContactVO>> contacts(
            AuthenticatedCaller caller, Long opportunityId,
            Integer pageValue, Integer sizeValue) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_CONTACT_QUERY,
                new ServiceOperation<>() {
                    private OpportunityAccess opportunityAccess;
                    private ContactAccess contactAccess;
                    private OpportunityId id;
                    private int page;
                    private int size;

                    @Override
                    public void validate() {
                        opportunityAccess = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                        contactAccess = contactAccessResolver.access(caller,
                                AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
                        id = id(opportunityId);
                        page = page(pageValue);
                        size = size(sizeValue);
                    }

                    @Override
                    public Pager<OpportunityContactVO> execute() {
                        Opportunity opportunity = requireVisible(opportunityAccess, id);
                        Map<FieldCode, EffectiveFieldAccess> opportunityFields =
                                support.accesses(opportunityAccess.tenantId(),
                                        opportunityAccess.memberId());
                        requireReadableUnmasked(opportunityFields,
                                new FieldCode(OpportunityFieldCatalog.CONTACTS));
                        requireReadableUnmasked(opportunityFields,
                                new FieldCode(OpportunityFieldCatalog.PRIMARY_CONTACT));
                        Map<FieldCode, EffectiveFieldAccess> contactFields =
                                fieldAccessResolver.resolve(contactAccess.tenantId(),
                                        contactAccess.memberId(), BusinessObjectType.CONTACT);
                        EffectiveFieldAccess nameAccess = contactFields.getOrDefault(
                                new FieldCode(ContactFieldCatalog.NAME),
                                EffectiveFieldAccess.hidden());
                        List<Contact> visible = opportunity.getContactIds().isEmpty()
                                ? List.of() : contactRepository.findVisibleByIds(
                                        contactAccess.tenantId(), opportunity.getContactIds(),
                                        contactAccess.visibility(),
                                        opportunity.getContactIds().size());
                        Map<ContactId, Contact> byId = visible.stream().collect(
                                Collectors.toMap(Contact::getId, Function.identity()));
                        List<OpportunityContactVO> all = opportunity.getContactIds().stream()
                                .map(byId::get).filter(java.util.Objects::nonNull)
                                .map(contact -> new OpportunityContactVO(
                                        contact.getId().getValue(),
                                        masked(contact.getName(), nameAccess),
                                        opportunity.getPrimaryContactId() != null
                                                && opportunity.getPrimaryContactId()
                                                .sameValueAs(contact.getId())))
                                .toList();
                        int from = Math.min(all.size(), (page - 1) * size);
                        int to = Math.min(all.size(), from + size);
                        Pager<OpportunityContactVO> pager = pager(page, size, all.size());
                        pager.setObjectList(all.subList(from, to));
                        return pager;
                    }
                });
    }

    private OpportunityContactsBaselineVO unavailableContactsBaseline(
            Opportunity opportunity) {
        return new OpportunityContactsBaselineVO(opportunity.getId().getValue(),
                opportunity.getVersion(), false, null, List.of());
    }

    @Override
    public Result<OpportunitySummaryVO> summary(
            AuthenticatedCaller caller, OpportunityQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.OPPORTUNITY_SUMMARY,
                new ServiceOperation<>() {
                    private OpportunityAccess access;

                    @Override
                    public void validate() {
                        requireRequest(request, "Opportunity summary request");
                        access = accessResolver.access(caller,
                                AuthorityCatalog.OPPORTUNITIES_READ,
                                OpportunityAccessMode.READ);
                    }

                    @Override
                    public OpportunitySummaryVO execute() {
                        QueryContext context = queryContext(caller, access, request);
                        requireReadableUnmasked(context.fieldAccess(),
                                new FieldCode(OpportunityFieldCatalog.ESTIMATED_AMOUNT));
                        OpportunitySummary result = opportunityRepository.summarize(
                                access.tenantId(), context.query());
                        return new OpportunitySummaryVO(
                                result.count(), result.estimatedAmount());
                    }
                });
    }

    private QueryContext queryContext(
            AuthenticatedCaller caller, OpportunityAccess access,
            OpportunityQueryRequest request) {
        if (request.getSavedViewId() != null && request.getConditions() != null
                && !request.getConditions().isEmpty()) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity saved and temporary filters are mutually exclusive");
        }
        if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity static and custom sort fields are mutually exclusive");
        }
        List<FieldDefinition> fields = support.fields(access.tenantId()).stream()
                .filter(FieldDefinition::isActive).toList();
        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                access.tenantId(), access.memberId());
        SearchKeyword keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
        if (request.getName() != null) {
            requireReadableUnmasked(fieldAccess,
                    new FieldCode(OpportunityFieldCatalog.NAME));
        }
        CustomerId customerId = customerId(request.getCustomerId());
        if (customerId != null) {
            requireReadableUnmasked(fieldAccess,
                    new FieldCode(OpportunityFieldCatalog.CUSTOMER));
            requireVisibleCustomer(caller, customerId);
        }
        MemberId owner = memberId(request.getOwnerActorId());
        if (owner != null) {
            requireReadableUnmasked(fieldAccess,
                    new FieldCode(OpportunityFieldCatalog.OWNER_ACTOR));
        }
        OpportunityOutcome outcome = request.getOutcome() == null
                ? null : support.parse(OpportunityOutcome.class,
                        request.getOutcome(), "Opportunity outcome");
        if (outcome != null) {
            requireReadableUnmasked(fieldAccess,
                    new FieldCode(OpportunityFieldCatalog.OUTCOME));
        }
        OpportunitySort sort;
        if (request.getCustomSortFieldCode() == null) {
            sort = opportunitySort(request.getSortField(), request.getSortDirection());
        } else if (request.getSortDirection() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity custom sort must include direction");
        } else {
            sort = customOpportunitySort(request.getCustomSortFieldCode(), request.getSortDirection(),
                    fields, fieldAccess);
        }
        if (!sort.isCustom() && !sort.isDefault()) {
            requireReadableUnmasked(fieldAccess, sort.field().fieldCode());
        }
        List<FilterCondition> conditions = conditions(
                caller, request.getSavedViewId(), request.getConditions(), access, fields, fieldAccess);
        return new QueryContext(new OpportunityQuery(access.visibility(),
                keyword == null ? null : keyword.value(),
                FieldAccessPolicy.searchableCandidates(fields, fieldAccess,
                        OpportunityFieldCatalog.searchCandidates()),
                request.getName(), owner, customerId, outcome, request.getStarred(), sort,
                conditions, page(request.getPage()), size(request.getSize())), fieldAccess);
    }

    private OpportunitySort opportunitySort(String field, String direction) {
        try {
            return OpportunitySort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Opportunity sort is invalid");
        }
    }

    private OpportunitySort customOpportunitySort(
            String requestedCode, String direction, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity custom sort field is invalid");
        }
        FieldDefinition field = fields.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity custom sort field is unavailable");
        }
        requireReadableUnmasked(fieldAccess, code);
        try {
            return OpportunitySort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Opportunity sort is invalid");
        }
    }

    private List<FilterCondition> conditions(
            AuthenticatedCaller caller, Long viewId, List<FilterConditionRequest> requested,
            OpportunityAccess access,
            List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (viewId == null && requested != null && !requested.isEmpty()) {
            return temporaryConditions(caller, requested, access, fields, fieldAccess);
        }
        if (viewId == null) {
            return List.of();
        }
        SavedView view;
        try {
            view = savedViewRepository.findById(access.tenantId(), access.memberId(),
                            new SavedViewId(viewId))
                    .orElseThrow(() -> support.error(
                            ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.OPPORTUNITY) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not an opportunity view");
        }
        Map<FieldCode, FieldDefinition> searchableFields = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> searchableTypes = searchableFields.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        view.assertApplicableTo(searchableTypes);
        List<FilterCondition> normalized;
        try {
            normalized = view.getConditions().stream()
                    .map(condition -> OpportunityFieldValuePolicy
                            .normalizeFilterCondition(
                                    searchableFields.get(condition.fieldCode()), condition))
                    .toList();
        } catch (OpportunityDomainException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity saved view condition is invalid");
        }
        for (FilterCondition condition : normalized) {
            requireReadableUnmasked(fieldAccess, condition.fieldCode());
            validateRelationCondition(caller, condition);
        }
        return normalized;
    }

    private List<FilterCondition> temporaryConditions(
            AuthenticatedCaller caller, List<FilterConditionRequest> requested,
            OpportunityAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        List<FilterCondition> normalized;
        try {
            List<FilterCondition> values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Opportunity condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            normalized = values.stream().map(condition -> OpportunityFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity temporary filter condition is invalid");
        }
        for (FilterCondition condition : normalized) {
            requireReadableUnmasked(fieldAccess, condition.fieldCode());
            validateRelationCondition(caller, condition);
        }
        return normalized;
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, FilterCondition condition) {
        if (new FieldCode(OpportunityFieldCatalog.CUSTOMER)
                .equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            if (condition.operator() == FilterOperator.IS_EMPTY
                    || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
                return;
            }
            Set<CustomerId> ids = relationIds(condition).stream()
                    .map(CustomerId::new).collect(Collectors.toSet());
            CustomerAccess access = customerAccessResolver.access(caller,
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            Set<CustomerId> visible = customerRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size())
                    .stream().map(io.github.archetom.crm.domain.entity.Customer::getId)
                    .collect(Collectors.toSet());
            if (!visible.containsAll(ids)) {
                throw relationDenied();
            }
        } else if (new FieldCode(OpportunityFieldCatalog.PRIMARY_CONTACT)
                .equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            if (condition.operator() == FilterOperator.IS_EMPTY
                    || condition.operator() == FilterOperator.IS_NOT_EMPTY) {
                return;
            }
            Set<ContactId> ids = relationIds(condition).stream()
                    .map(ContactId::new).collect(Collectors.toSet());
            ContactAccess access = contactAccessResolver.access(caller,
                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
            Set<ContactId> visible = contactRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size())
                    .stream().map(Contact::getId).collect(Collectors.toSet());
            if (!visible.containsAll(ids)) {
                throw relationDenied();
            }
        }
    }

    private void requireExactRelationOperator(FilterCondition condition) {
        if (condition.operator() != FilterOperator.EQ
                && condition.operator() != FilterOperator.IN
                && condition.operator() != FilterOperator.IS_EMPTY
                && condition.operator() != FilterOperator.IS_NOT_EMPTY) {
            throw relationDenied();
        }
    }

    private Set<Long> relationIds(FilterCondition condition) {
        try {
            return condition.values().stream().map(Long::parseLong)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity relation value is invalid");
        }
    }

    private RuntimeException relationDenied() {
        return support.error(ApplicationErrorCode.ACCESS_DENIED,
                "Opportunity relation condition is not available");
    }

    private List<OpportunityVO> values(
            AuthenticatedCaller caller, OpportunityAccess access,
            List<Opportunity> opportunities,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (opportunities.isEmpty()) {
            return List.of();
        }
        Set<OpportunityId> starred = starRepository.findStarred(
                access.tenantId(), access.memberId(), opportunities.stream()
                        .map(Opportunity::getId).toList());
        Set<CustomerId> customers = visibleCustomers(caller, opportunities);
        Set<ContactId> contacts = visibleContacts(caller, opportunities);
        Set<OpportunityId> collaboratorVisible = collaboratorVisibleIds(
                caller, opportunities);
        Map<Long, ActivitySubjectSummary> latestActivities = latestActivities(
                caller, access, opportunities);
        return opportunities.stream().map(opportunity -> support.toVO(
                opportunity, starred.contains(opportunity.getId()), fieldAccess,
                customers.contains(opportunity.getCustomerId()), contacts,
                collaboratorVisible.contains(opportunity.getId()),
                latestActivityTime(latestActivities.get(opportunity.getId().getValue())))).toList();
    }

    private Map<Long, ActivitySubjectSummary> latestActivities(
            AuthenticatedCaller caller, OpportunityAccess access,
            List<Opportunity> opportunities) {
        if (!caller.hasAuthority(AuthorityCatalog.ACTIVITIES_READ) || opportunities.isEmpty()) {
            return Map.of();
        }
        return activityRepository.findLatestBySubjects(access.tenantId(), BusinessObjectType.OPPORTUNITY,
                        opportunities.stream().map(value -> value.getId().getValue()).toList())
                .stream().collect(Collectors.toMap(ActivitySubjectSummary::subjectId,
                        Function.identity()));
    }

    private static LocalDateTime latestActivityTime(ActivitySubjectSummary value) {
        return value == null ? null : value.occurredTime();
    }

    private Set<OpportunityId> collaboratorVisibleIds(
            AuthenticatedCaller caller, List<Opportunity> opportunities) {
        try {
            OpportunityAccess access = accessResolver.access(caller,
                    AuthorityCatalog.OPPORTUNITIES_ASSIGN,
                    OpportunityAccessMode.OWNER_SCOPE);
            return opportunityRepository.findVisibleByIds(
                            access.tenantId(), opportunities.stream()
                                    .map(Opportunity::getId).toList(),
                            access.visibility(), opportunities.size())
                    .stream().map(Opportunity::getId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private Set<CustomerId> visibleCustomers(
            AuthenticatedCaller caller, List<Opportunity> opportunities) {
        Set<CustomerId> ids = opportunities.stream().map(Opportunity::getCustomerId)
                .collect(Collectors.toSet());
        try {
            CustomerAccess access = customerAccessResolver.access(caller,
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            return customerRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size())
                    .stream().map(io.github.archetom.crm.domain.entity.Customer::getId)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private Set<ContactId> visibleContacts(
            AuthenticatedCaller caller, List<Opportunity> opportunities) {
        Set<ContactId> ids = opportunities.stream()
                .flatMap(value -> value.getContactIds().stream())
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        try {
            ContactAccess access = contactAccessResolver.access(caller,
                    AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
            LinkedHashSet<ContactId> visible = new LinkedHashSet<>();
            List<ContactId> ordered = ids.stream().sorted(Comparator.comparing(
                    ContactId::getValue)).toList();
            for (int offset = 0; offset < ordered.size(); offset += 1_000) {
                List<ContactId> batch = ordered.subList(offset,
                        Math.min(ordered.size(), offset + 1_000));
                contactRepository.findVisibleByIds(access.tenantId(), batch,
                                access.visibility(), batch.size())
                        .stream().map(Contact::getId).forEach(visible::add);
            }
            return Set.copyOf(visible);
        } catch (NonRetryableApplicationException exception) {
            return Set.of();
        }
    }

    private void requireVisibleCustomer(
            AuthenticatedCaller caller, CustomerId customerId) {
        CustomerAccess access = customerAccessResolver.access(caller,
                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        if (customerRepository.findVisibleById(access.tenantId(), customerId,
                access.visibility()).isEmpty()) {
            throw support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                    "Customer was not found");
        }
    }

    private void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Opportunity field cannot be used for this query");
        }
    }

    private Opportunity requireVisible(
            OpportunityAccess access, OpportunityId id) {
        return opportunityRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(OpportunityNotFoundException::new);
    }

    private String masked(String value, EffectiveFieldAccess access) {
        if (access.accessLevel() == FieldAccessLevel.HIDDEN) {
            return null;
        }
        if (access.maskType() == FieldMaskType.NONE) {
            return value;
        }
        return "[REDACTED]";
    }

    private OpportunityId id(Long value) {
        try {
            return new OpportunityId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Opportunity ID must be positive");
        }
    }

    private CustomerId customerId(Long value) {
        try {
            return value == null ? null : new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
        }
    }

    private MemberId memberId(Long value) {
        try {
            return value == null ? null : new MemberId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Owner actor ID must be positive");
        }
    }

    private int page(Integer value) {
        int resolved = value == null ? QueryRequest.DEFAULT_PAGE : value;
        if (resolved < 1 || resolved > QueryRequest.MAX_PAGE) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Page is outside the supported range");
        }
        return resolved;
    }

    private int size(Integer value) {
        int resolved = value == null ? QueryRequest.DEFAULT_SIZE : value;
        if (resolved < 1 || resolved > QueryRequest.MAX_SIZE) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Page size is outside the supported range");
        }
        return resolved;
    }

    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        return pager;
    }

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    private record QueryContext(
            OpportunityQuery query,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
    }
}
