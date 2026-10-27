package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.ContactDuplicateCheckRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.QueryRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.security.ContactAccessResolver;
import io.github.archetom.crm.application.security.ContactAccessResolver.ContactAccess;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.SubordinateOwnerResolver;
import io.github.archetom.crm.application.service.ContactQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.ContactCollaboratorBaselineVO;
import io.github.archetom.crm.application.vo.ContactCollaboratorVO;
import io.github.archetom.crm.application.vo.ContactHierarchyVO;
import io.github.archetom.crm.application.vo.ContactVO;
import io.github.archetom.crm.application.vo.NearbyContactVO;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.ContactFieldValue;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.FilterCondition;
import io.github.archetom.crm.domain.entity.SavedView;
import io.github.archetom.crm.domain.exception.ContactNotFoundException;
import io.github.archetom.crm.domain.exception.CustomerNotFoundException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.ActivitySubjectSummary;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactDistance;
import io.github.archetom.crm.domain.model.ContactNearbyQuery;
import io.github.archetom.crm.domain.model.ContactHierarchyStats;
import io.github.archetom.crm.domain.model.ContactQuery;
import io.github.archetom.crm.domain.model.ContactSort;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.ContactFieldValuePolicy;
import io.github.archetom.crm.domain.policy.FieldAccessPolicy;
import io.github.archetom.crm.domain.policy.SavedViewPolicy;
import io.github.archetom.crm.domain.repository.ContactRepository;
import io.github.archetom.crm.domain.repository.ContactStarRepository;
import io.github.archetom.crm.domain.repository.ActivityRepository;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.repository.SavedViewRepository;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
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
public class ContactQueryServiceImpl implements ContactQueryService {

    private static final int MAX_HIERARCHY = ContactHierarchyStats.MAX_TREE_NODES;

    private final ContactRepository contactRepository;
    private final ContactStarRepository starRepository;
    private final ActivityRepository activityRepository;
    private final SavedViewRepository savedViewRepository;
    private final ContactAccessResolver accessResolver;
    private final CustomerAccessResolver customerAccessResolver;
    private final CustomerRepository customerRepository;
    private final SubordinateOwnerResolver subordinateOwnerResolver;
    private final ContactApplicationSupport support;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<Pager<ContactVO>> query(
            AuthenticatedCaller caller, ContactQueryRequest request) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_QUERY, new ServiceOperation<>() {
            private ContactAccess access;
            private int page;
            private int size;
            private String name;
            private SearchKeyword keyword;
            private CustomerId customerId;
            private ContactSort sort;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                        ContactAccessMode.READ);
                requireRequest(request, "Contact query request");
                page = page(request.getPage());
                size = size(request.getSize());
                name = normalize(request.getName());
                keyword = SearchKeyword.optional(request.getKeyword()).orElse(null);
                if (Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                        && request.getOwnerActorId() != null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contact owner scenes are mutually exclusive");
                }
                if (request.getSavedViewId() != null && request.getConditions() != null
                        && !request.getConditions().isEmpty()) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contact saved and temporary filters are mutually exclusive");
                }
                if (request.getCustomSortFieldCode() != null && request.getSortField() != null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contact static and custom sort fields are mutually exclusive");
                }
                customerId = request.getCustomerId() == null ? null : customerId(request.getCustomerId());
                if (request.getCustomSortFieldCode() == null) {
                    sort = contactSort(request.getSortField(), request.getSortDirection());
                } else if (request.getSortDirection() == null) {
                    throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                            "Contact custom sort must include direction");
                }
            }

            @Override
            public Pager<ContactVO> execute() {
                List<FieldDefinition> fields = support.fields(access.tenantId()).stream()
                        .filter(FieldDefinition::isActive).toList();
                Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                        access.tenantId(), access.memberId());
                if (name != null) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(ContactFieldCatalog.NAME));
                }
                if (request.getOwnerActorId() != null
                        || Boolean.TRUE.equals(request.getSubordinateOwnerOnly())) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(ContactFieldCatalog.OWNER_ACTOR));
                }
                if (request.getCustomSortFieldCode() != null) {
                    sort = customContactSort(request.getCustomSortFieldCode(), request.getSortDirection(),
                            fields, fieldAccess);
                } else if (!sort.isDefault()) {
                    requireReadableUnmasked(fieldAccess, sort.field().fieldCode());
                }
                if (customerId != null) {
                    requireReadableUnmasked(fieldAccess,
                            new FieldCode(ContactFieldCatalog.CUSTOMER));
                    CustomerAccess customerAccess = customerAccessResolver.access(caller,
                            AuthorityCatalog.CUSTOMERS_READ, CustomerAccessMode.READ);
                    customerRepository.findVisibleById(customerAccess.tenantId(), customerId,
                                    customerAccess.visibility())
                            .orElseThrow(CustomerNotFoundException::new);
                }
                List<FilterCondition> conditions = conditions(request.getSavedViewId(),
                        request.getConditions(), caller, access, fields, fieldAccess);
                PageResult<Contact> result = contactRepository.findContacts(access.tenantId(),
                        new ContactQuery(access.visibility(),
                                keyword == null ? null : keyword.value(),
                                FieldAccessPolicy.searchableCandidates(fields, fieldAccess,
                                        ContactFieldCatalog.searchCandidates()), name,
                                request.getOwnerActorId() == null ? null
                                        : new io.github.archetom.crm.domain.valueobject.MemberId(
                                                request.getOwnerActorId()),
                                Boolean.TRUE.equals(request.getSubordinateOwnerOnly())
                                        ? subordinateOwnerResolver.resolve(access.tenantId(),
                                        access.memberId(), access.visibility().dataScope()) : null,
                                customerId,
                                request.getStarred(), sort, conditions, page, size));
                return contactPager(result, caller, access, fieldAccess);
            }
        });
    }

    @Override
    public Result<ContactVO> get(AuthenticatedCaller caller, Long contactId) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_GET, new ServiceOperation<>() {
            private ContactAccess access;
            private ContactId id;

            @Override
            public void validate() {
                access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                        ContactAccessMode.READ);
                id = id(contactId);
            }

            @Override
            public ContactVO execute() {
                Contact contact = requireVisible(access, id);
                Set<ContactId> visibleRelations = visibleRelations(List.of(contact), access);
                return support.toVO(contact, starRepository.isStarred(
                        access.tenantId(), access.memberId(), id),
                        support.accesses(access.tenantId(), access.memberId()), visibleRelations,
                        visibleCustomers(caller, List.of(contact)), false);
            }
        });
    }

    @Override
    public Result<ContactCollaboratorBaselineVO> collaborators(
            AuthenticatedCaller caller, Long contactId) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_COLLABORATOR_GET,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private ContactId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_ASSIGN,
                                ContactAccessMode.OWNER_SCOPE);
                        id = id(contactId);
                    }

                    @Override
                    public ContactCollaboratorBaselineVO execute() {
                        Contact contact = requireVisible(access, id);
                        String callerState = contact.getOwnerMemberId().sameValueAs(access.memberId())
                                ? "OWNER" : contact.getCollaborators().stream()
                                .filter(value -> value.memberId().sameValueAs(access.memberId()))
                                .map(value -> value.access().name()).findFirst().orElse("NONE");
                        List<ContactCollaboratorVO> collaborators = contact.getCollaborators().stream()
                                .map(value -> new ContactCollaboratorVO(
                                        value.memberId().getValue(), value.access().name())).toList();
                        return new ContactCollaboratorBaselineVO(contact.getVersion(), collaborators,
                                callerState);
                    }
                });
    }

    @Override
    public Result<ContactHierarchyVO> hierarchy(
            AuthenticatedCaller caller, Long contactId) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_HIERARCHY_GET,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private ContactId id;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                        id = id(contactId);
                    }

                    @Override
                    public ContactHierarchyVO execute() {
                        Contact root = requireVisible(access, id);
                        List<Contact> descendants = contactRepository.findVisibleDescendants(
                                access.tenantId(), id, access.visibility(), MAX_HIERARCHY);
                        List<Contact> ancestors = contactRepository.findVisibleAncestors(
                                access.tenantId(), id, access.visibility(), 100);
                        Map<ContactId, Contact> visibleById = new LinkedHashMap<>();
                        visibleById.put(root.getId(), root);
                        descendants.forEach(value -> visibleById.put(value.getId(), value));
                        ancestors.forEach(value -> visibleById.put(value.getId(), value));
                        List<Contact> visible = List.copyOf(visibleById.values());
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        Set<ContactId> visibleIds = visible.stream().map(Contact::getId)
                                .collect(Collectors.toSet());
                        Set<ContactId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), visibleIds);
                        Set<CustomerId> visibleCustomers = visibleCustomers(caller, visible);
                        List<ContactVO> contacts = visible.stream()
                                .sorted(Comparator.comparing(value -> value.getId().getValue()))
                                .map(value -> support.toVO(value, starred.contains(value.getId()),
                                        fieldAccess, visibleIds,
                                        visibleCustomers, false))
                                .toList();
                        return new ContactHierarchyVO(id.getValue(), contacts);
                    }
                });
    }

    @Override
    public Result<Boolean> duplicateCheck(
            AuthenticatedCaller caller, ContactDuplicateCheckRequest request) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_DUPLICATE_CHECK,
                new ServiceOperation<>() {
                    private ContactAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                        requireRequest(request, "Contact duplicate request");
                        if (blank(request.getName()) && blank(request.getPhoneNumber())
                                && (request.getCustomFields() == null
                                || request.getCustomFields().isEmpty())) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "At least one duplicate-check value is required");
                        }
                    }

                    @Override
                    public Boolean execute() {
                        ContactId excludedId = request.getExcludedContactId() == null
                                ? null : id(request.getExcludedContactId());
                        if (excludedId != null) {
                            requireVisible(access, excludedId);
                        }
                        List<FieldDefinition> fields = support.fields(access.tenantId());
                        Map<FieldCode, FieldDefinition> byCode = support.fieldsByCode(fields);
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        if (!blank(request.getName())) {
                            requireReadableUnmasked(fieldAccess, new FieldCode(ContactFieldCatalog.NAME));
                        }
                        if (!blank(request.getPhoneNumber())) {
                            requireReadableUnmasked(fieldAccess,
                                    new FieldCode(ContactFieldCatalog.PHONE_NUMBER));
                        }
                        Map<FieldCode, String> unique = uniqueValues(
                                request.getCustomFields(), byCode, fieldAccess);
                        return contactRepository.existsDuplicate(access.tenantId(),
                                access.visibility(), excludedId, normalize(request.getName()),
                                normalize(request.getPhoneNumber()), unique);
                    }
                });
    }

    @Override
    public Result<Pager<NearbyContactVO>> nearby(
            AuthenticatedCaller caller, BigDecimal longitude, BigDecimal latitude,
            Integer radiusMeters, Integer page, Integer size) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_NEARBY_QUERY,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private int resolvedPage;
                    private int resolvedSize;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                        resolvedPage = page(page);
                        resolvedSize = size(size);
                    }

                    @Override
                    public Pager<NearbyContactVO> execute() {
                        Map<FieldCode, EffectiveFieldAccess> fieldAccess = support.accesses(
                                access.tenantId(), access.memberId());
                        requireReadableUnmasked(fieldAccess,
                                new FieldCode(ContactFieldCatalog.ADDRESSES));
                        PageResult<ContactDistance> result = contactRepository.findNearby(
                                access.tenantId(), new ContactNearbyQuery(access.visibility(),
                                        longitude, latitude, radiusMeters == null ? 0 : radiusMeters,
                                        resolvedPage, resolvedSize));
                        List<Contact> contacts = result.items().stream()
                                .map(ContactDistance::contact).toList();
                        Set<ContactId> visibleRelations = visibleRelations(contacts, access);
                        Set<ContactId> starred = starRepository.findStarred(
                                access.tenantId(), access.memberId(), contacts.stream()
                                        .map(Contact::getId).toList());
                        Set<CustomerId> visibleCustomers = visibleCustomers(caller, contacts);
                        Pager<NearbyContactVO> pager = pager(result.page(), result.size(), result.total());
                        pager.setObjectList(result.items().stream().map(value -> new NearbyContactVO(
                                support.toVO(value.contact(), starred.contains(value.contact().getId()),
                                        fieldAccess, visibleRelations,
                                        visibleCustomers, false), value.distanceMeters())).toList());
                        return pager;
                    }
                });
    }

    @Override
    public Result<List<ContactVO>> summaries(
            AuthenticatedCaller caller, List<Long> ids) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_SUMMARY_LIST,
                new ServiceOperation<>() {
                    private ContactAccess access;
                    private List<ContactId> contactIds;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                        if (ids == null || ids.isEmpty() || ids.size() > 100) {
                            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                                    "Contact summary IDs are invalid");
                        }
                        contactIds = ids.stream().map(ContactQueryServiceImpl.this::id)
                                .distinct().toList();
                    }

                    @Override
                    public List<ContactVO> execute() {
                        List<Contact> contacts = contactRepository.findVisibleByIds(
                                access.tenantId(), contactIds, access.visibility(), 100);
                        return contactVOs(contacts, caller, access,
                                support.accesses(access.tenantId(), access.memberId()));
                    }
                });
    }

    @Override
    public Result<List<String>> tags(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.CONTACT_TAG_LIST,
                new ServiceOperation<>() {
                    private ContactAccess access;

                    @Override
                    public void validate() {
                        access = accessResolver.access(caller, AuthorityCatalog.CONTACTS_READ,
                                ContactAccessMode.READ);
                    }

                    @Override
                    public List<String> execute() {
                        requireReadableUnmasked(support.accesses(
                                        access.tenantId(), access.memberId()),
                                new FieldCode(ContactFieldCatalog.TAGS));
                        return contactRepository.findVisibleTags(
                                access.tenantId(), access.visibility(), 200);
                    }
                });
    }

    private Pager<ContactVO> contactPager(
            PageResult<Contact> result, AuthenticatedCaller caller, ContactAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Pager<ContactVO> pager = pager(result.page(), result.size(), result.total());
        pager.setObjectList(contactVOs(result.items(), caller, access, fieldAccess));
        return pager;
    }

    private List<ContactVO> contactVOs(
            List<Contact> contacts, AuthenticatedCaller caller, ContactAccess access,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        Set<ContactId> visibleRelations = visibleRelations(contacts, access);
        Set<ContactId> starred = starRepository.findStarred(
                access.tenantId(), access.memberId(), contacts.stream().map(Contact::getId).toList());
        Set<CustomerId> visibleCustomers = visibleCustomers(caller, contacts);
        Map<Long, ActivitySubjectSummary> latestActivities = latestActivities(caller, access, contacts);
        return contacts.stream().map(contact -> support.toVO(contact,
                starred.contains(contact.getId()), fieldAccess, visibleRelations,
                visibleCustomers, false,
                latestActivityTime(latestActivities.get(contact.getId().getValue())),
                latestActivityContent(latestActivities.get(contact.getId().getValue())))).toList();
    }

    private Map<Long, ActivitySubjectSummary> latestActivities(
            AuthenticatedCaller caller, ContactAccess access, List<Contact> contacts) {
        if (!caller.hasAuthority(AuthorityCatalog.ACTIVITIES_READ) || contacts.isEmpty()) {
            return Map.of();
        }
        return activityRepository.findLatestBySubjects(access.tenantId(), BusinessObjectType.CONTACT,
                        contacts.stream().map(value -> value.getId().getValue()).toList())
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
            AuthenticatedCaller caller, Collection<Contact> contacts) {
        if (caller == null || !caller.hasAuthority(AuthorityCatalog.CUSTOMERS_READ)
                || contacts == null || contacts.isEmpty()) {
            return Set.of();
        }
        Set<CustomerId> ids = contacts.stream().map(Contact::getCustomerId)
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

    private Set<ContactId> visibleRelations(
            Collection<Contact> contacts, ContactAccess access) {
        Set<ContactId> parentIds = contacts.stream().map(Contact::getParentContactId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Set.of();
        }
        return contactRepository.findVisibleByIds(
                        access.tenantId(), parentIds, access.visibility(), Math.min(1_000, parentIds.size()))
                .stream().map(Contact::getId).collect(Collectors.toUnmodifiableSet());
    }

    private List<FilterCondition> conditions(
            Long viewId, List<FilterConditionRequest> requested, AuthenticatedCaller caller,
            ContactAccess access, List<FieldDefinition> fields,
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
        if (view.getObjectType() != BusinessObjectType.CONTACT) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Saved view is not a contact view");
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
            List<FilterConditionRequest> requested, AuthenticatedCaller caller, ContactAccess access,
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
                    throw new IllegalArgumentException("Contact condition is null");
                }
                return new FilterCondition(new FieldCode(value.getFieldCode()),
                        FilterOperator.valueOf(value.getOperator()), value.getValues());
            }).toList();
            SavedViewPolicy.validate(values, searchableTypes);
            values = values.stream().map(condition -> ContactFieldValuePolicy
                    .normalizeFilterCondition(searchable.get(condition.fieldCode()), condition)).toList();
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact temporary filter condition is invalid");
        }
        values.forEach(condition -> {
            requireReadableUnmasked(fieldAccess, condition.fieldCode());
            validateRelationCondition(caller, access, condition);
        });
        return values;
    }

    private void validateRelationCondition(
            AuthenticatedCaller caller, ContactAccess access, FilterCondition condition) {
        if (new FieldCode(ContactFieldCatalog.CUSTOMER).equals(condition.fieldCode())) {
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
        } else if (new FieldCode(ContactFieldCatalog.PARENT_CONTACT).equals(condition.fieldCode())) {
            requireExactRelationOperator(condition);
            Set<ContactId> ids = contactIds(condition);
            Set<ContactId> visible = contactRepository.findVisibleByIds(
                            access.tenantId(), ids, access.visibility(), ids.size())
                    .stream().map(Contact::getId).collect(Collectors.toSet());
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
                    "Contact customer relation value is invalid");
        }
    }

    private Set<ContactId> contactIds(FilterCondition condition) {
        try {
            return condition.values().stream().map(value -> new ContactId(Long.parseLong(value)))
                    .collect(Collectors.toSet());
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact parent relation value is invalid");
        }
    }

    private RuntimeException relationConditionDenied() {
        return support.error(ApplicationErrorCode.ACCESS_DENIED,
                "Contact relation condition is not available");
    }

    private ContactSort contactSort(String field, String direction) {
        try {
            return ContactSort.from(field, direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Contact sort is invalid");
        }
    }

    private ContactSort customContactSort(
            String requestedCode, String direction, List<FieldDefinition> fields,
            Map<FieldCode, EffectiveFieldAccess> fieldAccess) {
        FieldCode code;
        try {
            code = new FieldCode(requestedCode);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact custom sort field is invalid");
        }
        FieldDefinition field = fields.stream().filter(candidate -> candidate.getCode().equals(code))
                .findFirst().orElse(null);
        if (field == null || field.isBuiltin() || !field.getFieldType().supportsListSorting()
                || field.getId() == null) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact custom sort field is unavailable");
        }
        requireReadableUnmasked(fieldAccess, code);
        try {
            return ContactSort.custom(field.getId().getValue(), field.getFieldType(), direction);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID, "Contact sort is invalid");
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
                        "Contact field code is invalid");
            }
            FieldDefinition field = fields.get(code);
            if (field == null || !field.isActive() || !field.isUnique() || field.isBuiltin()) {
                throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                        "Contact duplicate field is invalid");
            }
            requireReadableUnmasked(fieldAccess, code);
            ContactFieldValue value = ContactFieldValuePolicy.normalize(field, entry.getValue());
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
                    "Contact field cannot be used for this query");
        }
    }

    private Contact requireVisible(ContactAccess access, ContactId id) {
        return contactRepository.findVisibleById(
                        access.tenantId(), id, access.visibility())
                .orElseThrow(ContactNotFoundException::new);
    }

    private <T> Pager<T> pager(long page, long size, long total) {
        Pager<T> pager = new Pager<>();
        pager.setPageNum(page);
        pager.setPageSize(size);
        pager.setTotalNum(total);
        return pager;
    }

    private ContactId id(Long value) {
        try {
            return new ContactId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Contact ID must be positive");
        }
    }

    private CustomerId customerId(Long value) {
        try {
            return new CustomerId(value);
        } catch (RuntimeException exception) {
            throw support.error(ApplicationErrorCode.PARAMETER_INVALID,
                    "Customer ID must be positive");
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
