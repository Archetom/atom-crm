package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.LeadDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.LeadAccessResolver;
import io.github.archetom.crm.application.security.LeadAccessResolver.LeadAccess;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContactAccessResolver.ContactAccess;
import io.github.archetom.crm.application.service.LeadQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.LeadCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.LeadCollaboratorVO;
import io.github.archetom.crm.application.vo.LeadHierarchyVO;
import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.application.vo.NearbyLeadVO;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.LeadNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ActivitySubjectSummary;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadDistance;
import io.github.archetom.crm.domain.model.LeadNearbyQuery;
import io.github.archetom.crm.domain.model.LeadHierarchyStats;
import io.github.archetom.crm.domain.model.LeadQuery;
import io.github.archetom.crm.domain.model.LeadSort;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldValuePolicy;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.LeadRepository;
import io.github.archetom.crm.domain.repository.LeadStarRepository;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.SavedViewId;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.common.result.Pager;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadQueryServiceImpl implements LeadQueryService {

    private static final int MAX_HIERARCHY = LeadHierarchyStats.MAX_TREE_NODES;

    private final LeadRepository leadRepository;
    private final LeadStarRepository starRepository;
    private final ActivityRepository activityRepository;
    private final SavedViewRepository savedViewRepository;
    private final LeadAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final ContactAccessResolver contactAccessResolver;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final LeadApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<LeadVO>> query(
            AuthenticatedCaller caller, LeadQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.LEAD_QUERY, new ServiceOperation<>() {
            private LeadAccess access;
            private int page;
            private int size;
            private String name;
            private SearchKeyword keyword;
            private LeadSort sort;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                        LeadAccessMode.READ);
                requireRequest(request, "Lead query request");
                page = page(request.getPage());
                size = size(request.getSize());
                name = normalize(request.getName());
                keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
                if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                        && request.getOwnerActorId() != null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Lead owner scenes are mutually exclusive");
                }
                if (request.getSavedViewId() != null && request.getConditions() != null
                        && !request.getConditions().isEmpty()) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Lead saved and temporary filters are mutually exclusive");
                }
                if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Lead static and custom sort fields are mutually exclusive");
                }
                if (request.getCustomSortFieldCode() == null) {
                    sort = leadSort(request.getSortField(), request.getSortDirection());
                } else if (request.getSortDirection() == null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Lead custom sort must include direction");
                }
            }

            @Override
            public Pager<LeadVO> execute() {
                List<FieldDefinition> fields = support.fields(access.tenantId()).stream()
                        .filter(FieldDefinition::isActive).toList();
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                        access.tenantId(), access.memberId());
                if (name != null) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(LeadFieldCatalog.NAME));
                }
                if (request.getOwnerActorId() != null
                        || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(LeadFieldCatalog.OWNER_ACTOR));
                }
                if (request.getCustomSortFieldCode() != null) {
                    sort = customLeadSort(request.getCustomSortFieldCode(), request.getSortDirection(),
                            fields, fieldAccess);
                } else if (!sort.isDefault()) {
                    requireReadableUnmasked(fieldAccess, sort.field().fieldCode());
                }
                List<FilterCondition> conditions = conditions(request.getSavedViewId(),
                        request.getConditions(),
                        caller, access, fields, fieldAccess);
                PageResult<Lead> result = leadRepository.findLeads(access.tenantId(),
                        new LeadQuery(access.visibility(),
                                keyword == null ? null : keyword.value(),
                                FieldAccessPolicy.searchableCandidates(fields, fieldAccess,
                                        LeadFieldCatalog.searchCandidates()), name,
                                request.getOwnerActorId() == null ? null
                                        : new io.github.archetom.crm.domain.valueobject.MemberId(
                                                request.getOwnerActorId()),
                                Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                                        ? subordinateOwnerResolver.resolve(access.tenantId(),
                                        access.memberId(), access.visibility().dataScope()) : null,
                                request.getStarred(), request.getConverted(), sort, conditions, page, size));
                return leadPager(result, caller, access, fieldAccess);
            }
        });
    }

    @Override
    public Result<LeadVO> get(AuthenticatedCaller caller, Long leadId) {
        return queryTemplate.execute(UseCaseOperation.LEAD_GET, new ServiceOperation<>() {
            private LeadAccess access;
            private LeadId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                        LeadAccessMode.READ);
                id = id(leadId);
            }

            @Override
            public LeadVO execute() {
                Lead lead = requireVisible(access, id);
                Set<LeadId> visibleRelations = visibleRelations(List.of(lead), access);
                return support.toVO(lead, starRepository.isStarred(
                        access.tenantId(), access.memberId(), id),
                        support.accesses(access.tenantId(), access.memberId()), visibleRelations,
                        visibleCustomers(caller, List.of(lead)),
                        visibleConvertedContacts(caller, List.of(lead)), false);
            }
        });
    }

    @Override
    public Result<LeadCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long leadId) {
        return queryTemplate.execute(UseCaseOperation.LEAD_COLLABORATOR_GET,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private LeadId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_ASSIGN,
                                LeadAccessMode.OWNER_SCOPE);
                        id = id(leadId);
                    }

                    @Override
                    public LeadCollaboratorBaselineVO execute() {
                        Lead lead = requireVisible(access, id);
                        String callerState = lead.getOwnerMemberId().sameValueAs(access.memberId())
                                ? "OWNER" : lead.getCollaborators().stream()
                                .filter(value -> value.memberId().sameValueAs(access.memberId()))
                                .map(value -> value.access().name()).findFirst().orElse("NONE");
                        List<LeadCollaboratorVO> collaborators = lead.getCollaborators().stream()
                                .map(value -> new LeadCollaboratorVO(
                                        value.memberId().getValue(), value.access().name())).toList();
                        return new LeadCollaboratorBaselineVO(lead.getVersion(), collaborators,
                                callerState);
                    }
                });
    }

    @Override
    public Result<LeadHierarchyVO> hierarchy(
            AuthenticatedCaller caller, Long leadId) {
        return queryTemplate.execute(UseCaseOperation.LEAD_HIERARCHY_GET,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private LeadId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                        id = id(leadId);
                    }

                    @Override
                    public LeadHierarchyVO execute() {
                        Lead root = requireVisible(access, id);
                        List<Lead> descendants = leadRepository.findVisibleDescendants(
                                access.tenantId(), id, access.visibility(), MAX_HIERARCHY);
                        List<Lead> ancestors = leadRepository.findVisibleAncestors(
                                access.tenantId(), id, access.visibility(), 100);
                        Map<LeadId, Lead> visibleById = new LinkedHashMap<>();
                        visibleById.put(root.getId(), root);
                        descendants.forEach(value -> visibleById.put(value.getId(), value));
                        ancestors.forEach(value -> visibleById.put(value.getId(), value));
                        List<Lead> visible = List.copyOf(visibleById.values());
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        Set<LeadId> visibleIds = visible.stream().map(Lead::getId)
                                .collect(Collectors.toSet());
                        Set<LeadId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), visibleIds);
                        Set<CustomerId> visibleCustomers = visibleCustomers(caller, visible);
                        Set<ContactId> visibleConvertedContacts = visibleConvertedContacts(caller, visible);
                        List<LeadVO> leads = visible.stream()
                                .sorted(Comparator.comparing(value -> value.getId().getValue()))
                                .map(value -> support.toVO(value, starred.contains(value.getId()),
                                        fieldAccess, visibleIds,
                                        visibleCustomers, visibleConvertedContacts, false))
                                .toList();
                        return new LeadHierarchyVO(id.getValue(), leads);
                    }
                });
    }

    @Override
    public Result<Boolean> duplicateCheck(
            AuthenticatedCaller caller, LeadDuplicateCheckRequest request) {
        return queryTemplate.execute(UseCaseOperation.LEAD_DUPLICATE_CHECK,
                new ServiceOperation<>() {
                    private LeadAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                        requireRequest(request, "Lead duplicate request");
                        if (blank(request.getName()) && blank(request.getPhoneNumber())
                                && (request.getCustomFields() == null
                                || request.getCustomFields().isEmpty())) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "At least one duplicate-check value is required");
                        }
                    }

                    @Override
                    public Boolean execute() {
                        LeadId excludedId = request.getExcludedLeadId() == null
                                ? null : id(request.getExcludedLeadId());
                        if (excludedId != null) {
                            requireVisible(access, excludedId);
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        if (!blank(request.getName())) {
                            requireReadableUnmasked(fieldAccess, new FieldCode(LeadFieldCatalog.NAME));
                        }
                        if (!blank(request.getPhoneNumber())) {
                            requireReadableUnmasked(fieldAccess,
                                    new FieldCode(LeadFieldCatalog.PHONE_NUMBER));
                        }
                        Map<FieldCode, String> unique = uniqueValues(
                                request.getCustomFields(), byCode, fieldAccess);
                        return leadRepository.existsDuplicate(access.tenantId(),
                                access.visibility(), excludedId, normalize(request.getName()),
                                normalize(request.getPhoneNumber()), unique);
                    }
                });
    }

    @Override
    public Result<Pager<NearbyLeadVO>> nearby(
            AuthenticatedCaller caller, BigDecimal longitude, BigDecimal latitude,
            Integer radiusMeters, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.LEAD_NEARBY_QUERY,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private int resolvedPage;
                    private int resolvedSize;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                        resolvedPage = page(page);
                        resolvedSize = size(size);
                    }

                    @Override
                    public Pager<NearbyLeadVO> execute() {
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireReadableUnmasked(fieldAccess,
                                new FieldCode(LeadFieldCatalog.ADDRESSES));
                        PageResult<LeadDistance> result = leadRepository.findNearby(
                                access.tenantId(), new LeadNearbyQuery(access.visibility(),
                                        longitude, latitude, radiusMeters == null ? 0 : radiusMeters,
                                        resolvedPage, resolvedSize));
                        List<Lead> leads = result.items().stream()
                                .map(LeadDistance::lead).toList();
                        Set<LeadId> visibleRelations = visibleRelations(leads, access);
                        Set<LeadId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), leads.stream()
                                        .map(Lead::getId).toList());
                        Set<CustomerId> visibleCustomers = visibleCustomers(caller, leads);
                        Set<ContactId> visibleConvertedContacts = visibleConvertedContacts(caller, leads);
                        Pager<NearbyLeadVO> pager = pager(result.page(), result.size(), result.total());
                        pager.setObjectList(result.items().stream().map(value -> new NearbyLeadVO(
                                support.toVO(value.lead(), starred.contains(value.lead().getId()),
                                        fieldAccess, visibleRelations,
                                        visibleCustomers, visibleConvertedContacts, false),
                                value.distanceMeters())).toList());
                        return pager;
                    }
                });
    }

    @Override
    public Result<List<LeadVO>> summaries(
            AuthenticatedCaller caller, List<Long> ids) {
        return queryTemplate.execute(UseCaseOperation.LEAD_SUMMARY_LIST,
                new ServiceOperation<>() {
                    private LeadAccess access;
                    private List<LeadId> leadIds;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                        if (ids == null || ids.isEmpty() || ids.size() > 100) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Lead summary IDs are invalid");
                        }
                        leadIds = ids.stream().map(LeadQueryServiceImpl.this::id)
                                .distinct().toList();
                    }

                    @Override
                    public List<LeadVO> execute() {
                        List<Lead> leads = leadRepository.findVisibleByIds(
                                access.tenantId(), leadIds, access.visibility(), 100);
                        return leadVOs(leads, caller, access,
                                support.accesses(access.tenantId(), access.memberId()));
                    }
                });
    }

    @Override
    public Result<List<String>> tags(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.LEAD_TAG_LIST,
                new ServiceOperation<>() {
                    private LeadAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.LEADS_READ,
                                LeadAccessMode.READ);
                    }

                    @Override
                    public List<String> execute() {
                        requireReadableUnmasked(support.accesses(
                                        access.tenantId(), access.memberId()),
                                new FieldCode(LeadFieldCatalog.TAGS));
                        return leadRepository.findVisibleTags(
                                access.tenantId(), access.visibility(), 200);
                    }
                });
    }

    private Pager<LeadVO> leadPager(
            PageResult<Lead> result, AuthenticatedCaller caller, LeadAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Pager<LeadVO> pager = pager(result.page(), result.size(), result.total());
        pager.setObjectList(leadVOs(result.items(), caller, access, fieldAccess));
        return pager;
    }

    private List<LeadVO> leadVOs(
            List<Lead> leads, AuthenticatedCaller caller, LeadAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Set<LeadId> visibleRelations = visibleRelations(leads, access);
        Set<LeadId> starred = starRepository.findStarred(
                access.tenantId(), access.memberId(), leads.stream().map(Lead::getId).toList());
        Set<CustomerId> visibleCustomers = visibleCustomers(caller, leads);
        Set<ContactId> visibleConvertedContacts = visibleConvertedContacts(caller, leads);
        Map<Long, ActivitySubjectSummary> latestActivities = latestActivities(caller, access, leads);
        return leads.stream().map(lead -> support.toVO(lead,
                starred.contains(lead.getId()), fieldAccess, visibleRelations,
                visibleCustomers, visibleConvertedContacts, false,
                latestActivityTime(latestActivities.get(lead.getId().getValue())),
                latestActivityContent(latestActivities.get(lead.getId().getValue())))).toList();
    }

    private Map<Long, ActivitySubjectSummary> latestActivities(
            AuthenticatedCaller caller, LeadAccess access, List<Lead> leads) {
        if (!caller.hasAuthority(AuthorityCatalog.ACTIVITIES_READ) || leads.isEmpty()) {
            return Map.of();
        }
        return activityRepository.findLatestBySubjects(access.tenantId(), BusinessObjectType.LEAD,
                        leads.stream().map(value -> value.getId().getValue()).toList())
                .stream().collect(Collectors.toMap(ActivitySubjectSummary::subjectId,
                        Function.identity()));
    }

    private static LocalDateTime latestActivityTime(ActivitySubjectSummary value) {
        return value == null ? null : value.occurredTime();
    }

    private static String latestActivityContent(ActivitySubjectSummary value) {
        return value == null ? null : value.content();
    }

    private Set<CustomerId> visibleCustomers(
            AuthenticatedCaller caller, Collection<Lead> leads) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)
                || leads == null || leads.isEmpty()) {
            return Set.of();
        }
        Set<CustomerId> ids = leads.stream().map(Lead::getCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        CustomerAccess access = customerAccessResolver.access(caller,
                AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
        return customerRepository.findVisibleByIds(access.tenantId(), ids,
                        access.visibility(), ids.size())
                .stream().map(io.github.archetom.crm.domain.entity.Customer::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<ContactId> visibleConvertedContacts(
            AuthenticatedCaller caller, Collection<Lead> leads) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.CONTACTS_READ)
                || leads == null || leads.isEmpty()) {
            return Set.of();
        }
        Set<ContactId> ids = leads.stream().map(Lead::getConvertedContactId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        ContactAccess access = contactAccessResolver.access(caller,
                AuthorityCatalog.CONTACTS_READ, ContactAccessMode.READ);
        return contactRepository.findVisibleByIds(access.tenantId(), ids,
                        access.visibility(), ids.size())
                .stream().map(io.github.archetom.crm.domain.entity.Contact::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<LeadId> visibleRelations(
            Collection<Lead> leads, LeadAccess access) {
        Set<LeadId> parentIds = leads.stream().map(Lead::getParentLeadId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return leadRepository.findVisibleByIds(
                        access.tenantId(), parentIds, access.visibility(), Math.min(1_000, parentIds.size()))
                .stream().map(Lead::getId).collect(Collectors.toUnmodifiableSet());
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            LeadAccess access, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
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
                    .orElseThrow(() -> support.error(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                            "Saved view was not found"));
        } catch (IllegalArgumentException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view ID is invalid");
        }
        if (view.getObjectType() != BusinessObjectType.LEAD) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a lead view");
        }
        Map<FieldCode, CustomFieldType> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        FieldDefinition::getFieldType, (left, right) -> left));
        view.assertApplicableTo(searchable);
        view.getConditions().forEach(condition -> {
            requireReadableUnmasked(fieldAccess, condition.fieldCode());
            validateRelationCondition(caller, access, condition);
        });
        return view.getConditions();
    }

    private List<FilterCondition> temporaryConditions(
            List<FilterConditionRequest> requested, AuthenticatedCaller caller, LeadAccess access,
            List<FieldDefinition> fields, Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Map<FieldCode, FieldDefinition> searchable = fields.stream()
                .filter(FieldDefinition::isSearchable)
                .collect(Collectors.toMap(FieldDefinition::getCode,
                        Function.identity(), (left, right) -> left));
        Map<FieldCode, CustomFieldType> searchableTypes = searchable.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getFieldType()));
        List<FilterCondition> values;
        try {
            values = requested.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Lead condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            values = values.stream().map(condition -> LeadFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead temporary filter condition is invalid");
        }
        values.forEach(condition -> {
            requireReadableUnmasked(fieldAccess, condition.fieldCode());
            validateRelationCondition(caller, access, condition);
        });
        return values;
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, LeadAccess access, FilterCondition condition) {
        if (new FieldCode(LeadFieldCatalog.CUSTOMER).equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            Set<CustomerId> ids = customerIds(condition);
            CustomerAccess customerAccess = customerAccessResolver.access(caller,
                    AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
            Set<CustomerId> visible = customerRepository.findVisibleByIds(
                            customerAccess.tenantId(), ids, customerAccess.visibility(), ids.size())
                    .stream().map(io.github.archetom.crm.domain.entity.Customer::getId)
                    .collect(Collectors.toSet());
            if (!visible.containsAll(ids)) {
                throw relationConditionDenied();
            }
        } else if (new FieldCode(LeadFieldCatalog.PARENT_LEAD).equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            Set<LeadId> ids = leadIds(condition);
            Set<LeadId> visible = leadRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size())
                    .stream().map(Lead::getId).collect(Collectors.toSet());
            if (!visible.containsAll(ids)) {
                throw relationConditionDenied();
            }
        }
    }

    private void requireExactRelationOperator(FilterCondition condition) {
        if (condition.operator() != FilterOperator.EQ && condition.operator() != FilterOperator.IN) {
            throw relationConditionDenied();
        }
    }

    private Set<CustomerId> customerIds(FilterCondition condition) {
        try {
            return condition.values().stream().map(value -> new CustomerId(Long.parseLong(value)))
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead customer relation value is invalid");
        }
    }

    private Set<LeadId> leadIds(FilterCondition condition) {
        try {
            return condition.values().stream().map(value -> new LeadId(Long.parseLong(value)))
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead parent relation value is invalid");
        }
    }

    private RuntimeException relationConditionDenied() {
        return support.error(ApplicationErrorCode.ACCESS_DENIED,
                "Lead relation condition is not available");
    }

    private LeadSort leadSort(String field, String direction) {
        try {
            return LeadSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Lead sort is invalid");
        }
    }

    private LeadSort customLeadSort(
            String requestedCode, String direction, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead custom sort field is invalid");
        }
        FieldDefinition field = fields.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead custom sort field is unavailable");
        }
        requireReadableUnmasked(fieldAccess, code);
        try {
            return LeadSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Lead sort is invalid");
        }
    }

    private Map<FieldCode, String> uniqueValues(
            Map<String, List<String>> requested,
            Map<FieldCode, FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<FieldCode, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : requested.entrySet()) {
            FieldCode code;
            try {
                code = new FieldCode(entry.getKey());
            } catch (RuntimeException exception) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Lead field code is invalid");
            }
            FieldDefinition field = fields.get(code);
            if (field == null || !field.isActive() || !field.isUnique() || field.isBuiltin()) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Lead duplicate field is invalid");
            }
            requireReadableUnmasked(fieldAccess, code);
            LeadFieldValue value = LeadFieldValuePolicy.normalize(field, entry.getValue());
            if (value != null) {
                values.put(code, value.values().getFirst());
            }
        }
        return Map.copyOf(values);
    }

    private void requireReadableUnmasked(
            Map<FieldCode, EffectiveFieldAccess> accesses, FieldCode code) {
        EffectiveFieldAccess access = accesses.get(code);
        if (access == null || access.accessLevel() == FieldAccessLevel.HIDDEN
                || access.maskType() != FieldMaskType.NONE) {
            throw support.error(ApplicationErrorCode.ACCESS_DENIED,
                    "Lead field cannot be used for this query");
        }
    }

    private Lead requireVisible(LeadAccess access, LeadId id) {
        return leadRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(LeadNotFoundException::new);
    }

    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        return pager;
    }

    private LeadId id(Long value) {
        try {
            return new LeadId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Lead ID must be positive");
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

    private void requireRequest(Object request, String description) {
        if (request == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    description + " must not be empty");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim();
    }
}
