package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataAdoptionPreviewRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseDataIdentitySelectionRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.CustomizationObjectLock;
import io.github.archetom.crm.application.port.out.enterprisedata.CustomerRegisteredIdentityStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAdoptionProvenanceStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreview;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataPreviewStore;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.application.security.CustomerAccessResolver;
import io.github.archetom.crm.application.security.CustomerAccessResolver.CustomerAccess;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.EnterpriseDataAdoptionService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.EnterpriseDataAdoptionPreviewVO;
import io.github.archetom.crm.application.vo.EnterpriseDataAdoptionVO;
import io.github.archetom.crm.application.vo.EnterpriseDataObservationVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerAddress;
import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.exception.RegisteredIdentityAlreadyClaimedException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.EnterpriseDataAdoptableFact;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.repository.CustomerRepository;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.ApplicationException;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * B23-XI/XP existing-Customer adoption flow. Provider I/O occurs in prepare, then the command
 * transaction serializes B05, Customer, identity reservations and value-free provenance together.
 */
@Service
@RequiredArgsConstructor
public class EnterpriseDataAdoptionServiceImpl implements EnterpriseDataAdoptionService {
    private static final Pattern ADDRESS_SLOT = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Set<IdentifierStanding> DEFAULT_ADOPTABLE_STANDINGS =
            Set.of(IdentifierStanding.CURRENT, IdentifierStanding.STALE);

    private final EnterpriseDataSource source;
    private final EnterpriseDataReadPolicyResolver readPolicyResolver;
    private final EnterpriseDataAdoptionPolicyResolver adoptionPolicyResolver;
    private final EnterpriseDataPreviewStore previewStore;
    private final CustomerRegisteredIdentityStore identities;
    private final EnterpriseDataAdoptionProvenanceStore provenance;
    private final OrganizationAccessGuard organizationAccessGuard;
    private final CustomerAccessResolver customerAccessResolver;
    private final CustomerRepository customerRepository;
    private final CustomerApplicationSupport customerSupport;
    private final CustomizationObjectLock objectLock;
    private final QueryServiceTemplate queryTemplate;
    private final CommandServiceTemplate commandTemplate;
    private final Clock clock;

    @Override
    public Result<EnterpriseDataAdoptionPreviewVO> preview(
            AuthenticatedCaller caller, EnterpriseDataAdoptionPreviewRequest request) {
        return queryTemplate.execute(UseCaseOperation.ENTERPRISE_DATA_ADOPTION_PREVIEW,
                new ServiceOperation<>() {
                    private CustomerAccess customerAccess;
                    private CustomerId customerId;
                    private RegisteredIdentifierKey selector;
                    private String addressSlot;
                    private EnterpriseDataAdoptionPolicy policy;
                    private EnterpriseDataObservation observation;

                    @Override public void validate() {
                        require(request);
                        organizationAccessGuard.requireActiveMembership(caller,
                                AuthorityCatalog.ENTERPRISE_DATA_READ);
                        customerAccess = customerAccessResolver.access(caller,
                                AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
                        customerId = new CustomerId(request.getCustomerId());
                        selector = selector(request.getIdentityScope(), request.getRegistryScheme(), request.getValue());
                        addressSlot = addressSlot(request.getAddressSlotKey());
                    }

                    @Override public void prepare() {
                        try {
                            policy = requireAdoptionPolicy(customerAccess.tenantId());
                            EnterpriseDataCapabilitySnapshot capability = requireExactCapability(
                                    customerAccess.tenantId(), selector, policy);
                            observation = exact(customerAccess.tenantId(), selector, policy);
                            requireAdvertisedIdentifiers(observation, capability);
                        } catch (EnterpriseDataSourceException exception) {
                            throw mapSourceFailure(exception.error());
                        } catch (ApplicationException exception) {
                            throw exception;
                        } catch (RuntimeException exception) {
                            throw unavailable();
                        }
                    }

                    @Override public EnterpriseDataAdoptionPreviewVO execute() {
                        Customer customer = customerRepository.findVisibleById(
                                        customerAccess.tenantId(), customerId, customerAccess.visibility())
                                .orElseThrow(() -> unavailableCustomer());
                        customerSupport.requireVersion(customer, request.getCustomerVersion());
                        Instant now = clock.instant();
                        Instant expires = earliest(now.plus(policy.maximumObservationTtl()),
                                now.plus(readPolicy(customerAccess.tenantId()).maximumObservationTtl()));
                        EnterpriseDataPreview preview = new EnterpriseDataPreview(customerAccess.tenantId(),
                                customerAccess.memberId(), customerId, customer.getVersion(), addressSlot,
                                observation, policy, now, expires);
                        String reference = previewStore.issue(preview);
                        return new EnterpriseDataAdoptionPreviewVO(observationVO(observation, preview,
                                        policy.previewAttribution()), reference,
                                diffs(customer, observation, addressSlot, policy));
                    }
                });
    }

    @Override
    public Result<EnterpriseDataAdoptionVO> adopt(
            AuthenticatedCaller caller, EnterpriseDataAdoptRequest request) {
        return commandTemplate.execute(UseCaseOperation.ENTERPRISE_DATA_ADOPT, new ServiceOperation<>() {
            private CustomerAccess customerAccess;
            private EnterpriseDataPreview preview;
            private EnterpriseDataObservation refreshed;
            private Set<EnterpriseDataAdoptableFact> facts;
            private List<RegisteredIdentifier> selectedIdentities;

            @Override public void validate() {
                require(request);
                organizationAccessGuard.requireActiveMembership(caller,
                        AuthorityCatalog.ENTERPRISE_DATA_READ);
                customerAccess = customerAccessResolver.access(caller,
                        AuthorityCatalog.CUSTOMERS_WRITE, CustomerAccessMode.WRITE);
                facts = facts(request.getFacts());
                if (!facts.contains(EnterpriseDataAdoptableFact.REGISTERED_IDENTITY)) {
                    throw invalid();
                }
            }

            @Override public void prepare() {
                Instant now = clock.instant();
                // Read without consuming: prepare() runs outside the transaction, so spending the
                // reference here would burn it on every attempt that later rolls back. It is
                // consumed at the end of execute(), once the adoption itself has succeeded.
                preview = previewStore.peek(customerAccess.tenantId(), customerAccess.memberId(),
                                request.getPreviewReference(), now)
                        .orElseThrow(EnterpriseDataAdoptionServiceImpl::conflict);
                if (!preview.tenantId().sameValueAs(customerAccess.tenantId())
                        || !preview.memberId().sameValueAs(customerAccess.memberId())
                        || !Long.valueOf(preview.customerVersion()).equals(request.getCustomerVersion())) {
                    throw conflict();
                }
                EnterpriseDataAdoptionPolicy current = requireAdoptionPolicy(customerAccess.tenantId());
                requireSamePolicy(preview.policy(), current);
                if (!current.allowedFacts().containsAll(facts)) throw unsupported();
                try {
                    EnterpriseDataCapabilitySnapshot capability = requireExactCapability(customerAccess.tenantId(),
                            preview.observation().primaryIdentifier().key(), current);
                    refreshed = exact(customerAccess.tenantId(),
                            preview.observation().primaryIdentifier().key(), current);
                    requireAdvertisedIdentifiers(refreshed, capability);
                } catch (EnterpriseDataSourceException exception) {
                    throw mapSourceFailure(exception.error());
                } catch (ApplicationException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw unavailable();
                }
                selectedIdentities = selectedIdentities(request.getIdentities(), refreshed,
                        preview.observation().primaryIdentifier());
                requireFreshEqual(preview.observation(), refreshed, facts, selectedIdentities);
            }

            @Override public EnterpriseDataAdoptionVO execute() {
                objectLock.acquire(customerAccess.tenantId(), BusinessObjectType.CUSTOMER);
                Customer customer = customerRepository.findVisibleByIdForUpdate(
                                customerAccess.tenantId(), preview.customerId(), customerAccess.visibility())
                        .orElseThrow(() -> unavailableCustomer());
                customerSupport.requireVersion(customer, preview.customerVersion());
                customerSupport.requireVersion(customer, request.getCustomerVersion());
                Map<io.github.archetom.crm.domain.valueobject.FieldCode,
                        io.github.archetom.crm.domain.model.EffectiveFieldAccess> fieldAccess =
                        customerSupport.accesses(customerAccess.tenantId(), customerAccess.memberId());
                requireWritableFacts(fieldAccess, facts, refreshed);
                List<CustomerRegisteredIdentityStore.RegisteredIdentityAssociation> associations;
                try {
                    associations = identities.reserve(customerAccess.tenantId(), customer.getId(),
                            selectedIdentities.stream().map(RegisteredIdentifier::key).toList());
                } catch (RegisteredIdentityAlreadyClaimedException exception) {
                    throw conflict();
                }
                List<EnterpriseDataAdoptionProvenanceStore.Selection> selections = new ArrayList<>();
                for (CustomerRegisteredIdentityStore.RegisteredIdentityAssociation association : associations) {
                    selections.add(new EnterpriseDataAdoptionProvenanceStore.Selection(
                            EnterpriseDataAdoptableFact.REGISTERED_IDENTITY,
                            "identity:" + association.associationId(), false));
                }
                String name = customer.getName();
                List<CustomerAddress> addresses = customer.getAddresses();
                if (facts.contains(EnterpriseDataAdoptableFact.REGISTERED_NAME)) {
                    boolean changed = !name.equals(refreshed.registeredName());
                    name = refreshed.registeredName();
                    selections.add(new EnterpriseDataAdoptionProvenanceStore.Selection(
                            EnterpriseDataAdoptableFact.REGISTERED_NAME,
                            "field:" + CustomerFieldCatalog.NAME, changed));
                }
                if (facts.contains(EnterpriseDataAdoptableFact.REGISTERED_ADDRESS)) {
                    AddressMutation mutation = replaceAddress(addresses, preview.addressSlotKey(),
                            refreshed.registeredAddress());
                    addresses = mutation.addresses();
                    selections.add(new EnterpriseDataAdoptionProvenanceStore.Selection(
                            EnterpriseDataAdoptableFact.REGISTERED_ADDRESS,
                            "address:" + preview.addressSlotKey(), mutation.changed()));
                }
                long expectedVersion = customer.getVersion();
                customer.replaceDetails(name, customer.getPhoneNumber(), customer.getTelephone(),
                        customer.getEmail(), customer.getWebsite(), customer.getDescription(), addresses,
                        customer.getCustomFieldValues());
                List<FieldDefinition> fields = customerSupport.fields(customerAccess.tenantId());
                Customer saved = customerRepository.save(customerAccess.tenantId(), customer, fields);
                long primaryAssociation = associations.stream()
                        .filter(value -> value.identity().equals(refreshed.primaryIdentifier().key()))
                        .findFirst().orElseThrow(EnterpriseDataAdoptionServiceImpl::conflict).associationId();
                Instant deleteAfter = earliest(preview.observedAt().plus(preview.policy().provenanceRetention()),
                        preview.expiresAt().plus(preview.policy().provenanceRetention()));
                provenance.append(new EnterpriseDataAdoptionProvenanceStore.Adoption(UUID.randomUUID(),
                        customerAccess.tenantId(), saved.getId(), customerAccess.memberId(), primaryAssociation,
                        refreshed.sourceKind(), preview.policy().sourceBindingRef(),
                        preview.policy().adapterEvidenceRef(), preview.policy().policyVersion(),
                        preview.policy().attributionVersion(), preview.policy().mappingVersion(),
                        preview.observedAt(), preview.expiresAt(), expectedVersion, saved.getVersion(),
                        deleteAfter, selections));
                // Spend the one-shot reference last, once everything that can fail has succeeded.
                // A concurrent adoption of the same preview loses here and rolls back, so the
                // reference still adopts at most once.
                previewStore.claim(customerAccess.tenantId(), customerAccess.memberId(),
                                request.getPreviewReference(), clock.instant())
                        .orElseThrow(EnterpriseDataAdoptionServiceImpl::conflict);
                return new EnterpriseDataAdoptionVO(saved.getId().getValue(), saved.getVersion(),
                        attributionVO(preview.policy().adoptionConfirmationAttribution()));
            }
        });
    }

    private EnterpriseDataAdoptionPolicy requireAdoptionPolicy(TenantId tenantId) {
        EnterpriseDataAdoptionPolicy policy = adoptionPolicyResolver.resolve(tenantId);
        EnterpriseDataReadPolicy read = readPolicy(tenantId);
        if (policy == null || !policy.ready() || !read.ready()
                || read.maximumUsageMode() != EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS
                || read.sourceKind() != policy.sourceKind()) {
            throw unavailable();
        }
        return policy;
    }

    private EnterpriseDataReadPolicy readPolicy(TenantId tenantId) {
        EnterpriseDataReadPolicy policy = readPolicyResolver.resolve(tenantId);
        if (policy == null || !policy.ready()) throw unavailable();
        return policy;
    }

    private EnterpriseDataCapabilitySnapshot requireExactCapability(TenantId tenantId,
                                                                     RegisteredIdentifierKey selector,
                                                                     EnterpriseDataAdoptionPolicy policy) {
        EnterpriseDataCapabilitySnapshot capability = source.capabilities(tenantId);
        if (capability == null || !capability.ready()
                || !capability.queryModes().contains(EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER)
                || !capability.identifierTypes().contains(selector.type())
                || policy.sourceKind() == null) {
            throw unavailable();
        }
        return capability;
    }

    private EnterpriseDataObservation exact(TenantId tenantId, RegisteredIdentifierKey selector,
                                            EnterpriseDataAdoptionPolicy policy) {
        EnterpriseDataObservation value = source.findByRegisteredIdentifier(tenantId, selector)
                .orElseThrow(() -> new EnterpriseDataSourceException(EnterpriseDataSourceError.NO_RESULT));
        if (!value.primaryIdentifier().key().equals(selector) || value.sourceKind() != policy.sourceKind()
                || value.identifiers().isEmpty()) {
            throw unavailable();
        }
        return value;
    }

    private static void requireAdvertisedIdentifiers(EnterpriseDataObservation observation,
                                                      EnterpriseDataCapabilitySnapshot capability) {
        if (observation.identifiers().stream().anyMatch(identifier ->
                !capability.identifierTypes().contains(identifier.key().type()))) {
            throw unavailable();
        }
    }

    private void requireWritableFacts(
            Map<io.github.archetom.crm.domain.valueobject.FieldCode,
                    io.github.archetom.crm.domain.model.EffectiveFieldAccess> access,
            Set<EnterpriseDataAdoptableFact> facts, EnterpriseDataObservation observation) {
        if (facts.contains(EnterpriseDataAdoptableFact.REGISTERED_NAME)) {
            customerSupport.requireWrite(access, CustomerFieldCatalog.NAME);
        }
        if (facts.contains(EnterpriseDataAdoptableFact.REGISTERED_ADDRESS)) {
            if (observation.registeredAddress() == null) throw unsupported();
            customerSupport.requireWrite(access, CustomerFieldCatalog.ADDRESSES);
        }
    }

    private List<EnterpriseDataAdoptionPreviewVO.DiffVO> diffs(Customer customer,
            EnterpriseDataObservation observation, String addressSlot, EnterpriseDataAdoptionPolicy policy) {
        List<EnterpriseDataAdoptionPreviewVO.DiffVO> values = new ArrayList<>();
        if (policy.allowedFacts().contains(EnterpriseDataAdoptableFact.REGISTERED_IDENTITY)) {
            values.add(new EnterpriseDataAdoptionPreviewVO.DiffVO(
                    EnterpriseDataAdoptableFact.REGISTERED_IDENTITY.name(), "identity", true));
        }
        if (policy.allowedFacts().contains(EnterpriseDataAdoptableFact.REGISTERED_NAME)) {
            values.add(new EnterpriseDataAdoptionPreviewVO.DiffVO(
                    EnterpriseDataAdoptableFact.REGISTERED_NAME.name(),
                    "field:" + CustomerFieldCatalog.NAME,
                    !customer.getName().equals(observation.registeredName())));
        }
        if (policy.allowedFacts().contains(EnterpriseDataAdoptableFact.REGISTERED_ADDRESS)) {
            CustomerAddress existing = customer.getAddresses().stream()
                    .filter(value -> value.key().equals(addressSlot)).findFirst().orElse(null);
            values.add(new EnterpriseDataAdoptionPreviewVO.DiffVO(
                    EnterpriseDataAdoptableFact.REGISTERED_ADDRESS.name(), "address:" + addressSlot,
                    observation.registeredAddress() != null && (existing == null
                            || !sameAddress(existing, observation.registeredAddress()))));
        }
        return List.copyOf(values);
    }

    private static AddressMutation replaceAddress(List<CustomerAddress> existing, String key,
                                                   RegisteredAddressObservation sourceAddress) {
        if (sourceAddress == null) throw unsupported();
        List<CustomerAddress> next = new ArrayList<>(existing);
        for (int index = 0; index < next.size(); index++) {
            CustomerAddress current = next.get(index);
            if (!current.key().equals(key)) continue;
            boolean changed = !sameAddress(current, sourceAddress);
            next.set(index, fromSource(key, current.label(), current.primary(), sourceAddress));
            return new AddressMutation(List.copyOf(next), changed);
        }
        if (next.size() >= Customer.MAX_ADDRESSES) throw unsupported();
        next.add(fromSource(key, "Registered address", false, sourceAddress));
        return new AddressMutation(List.copyOf(next), true);
    }

    private static CustomerAddress fromSource(String key, String label, boolean primary,
                                              RegisteredAddressObservation source) {
        return new CustomerAddress(key, label, source.countryCode(), source.region(), source.locality(),
                source.postalCode(), source.addressLine(), null, null, primary);
    }

    private static boolean sameAddress(CustomerAddress current, RegisteredAddressObservation source) {
        return source != null && equals(current.country(), source.countryCode())
                && equals(current.region(), source.region()) && equals(current.city(), source.locality())
                && equals(current.postalCode(), source.postalCode())
                && equals(current.addressLine(), source.addressLine());
    }

    private static boolean equals(String left, String right) {
        return java.util.Objects.equals(left, right);
    }

    private static Set<EnterpriseDataAdoptableFact> facts(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw invalid();
        Set<EnterpriseDataAdoptableFact> values = EnumSet.noneOf(EnterpriseDataAdoptableFact.class);
        try {
            for (String value : raw) values.add(EnterpriseDataAdoptableFact.valueOf(value.trim()));
        } catch (RuntimeException exception) { throw invalid(); }
        if (values.size() != raw.size()) throw invalid();
        return Set.copyOf(values);
    }

    private static List<RegisteredIdentifier> selectedIdentities(
            List<EnterpriseDataIdentitySelectionRequest> requested,
            EnterpriseDataObservation observed, RegisteredIdentifier primary) {
        if (requested == null || requested.isEmpty()) throw invalid();
        Map<String, RegisteredIdentifier> byType = new HashMap<>();
        for (RegisteredIdentifier value : observed.identifiers()) {
            if (!DEFAULT_ADOPTABLE_STANDINGS.contains(value.getIdentifierStanding())) continue;
            byType.put(value.getIdentityScope().name() + ':' + value.getRegistryScheme().name(), value);
        }
        List<RegisteredIdentifier> selected = new ArrayList<>();
        for (EnterpriseDataIdentitySelectionRequest selection : requested) {
            if (selection == null) throw invalid();
            RegisteredIdentifier value = byType.get(selection.getIdentityScope().trim().toUpperCase()
                    + ':' + selection.getRegistryScheme().trim().toUpperCase());
            if (value == null) throw unsupported();
            selected.add(value);
        }
        if (new LinkedHashSet<>(selected).size() != selected.size() || !selected.contains(primary)) {
            throw unsupported();
        }
        selected.sort(Comparator.comparing((RegisteredIdentifier value) -> value.getIdentityScope().name())
                .thenComparing(value -> value.getRegistryScheme().name())
                .thenComparing(RegisteredIdentifier::getCanonicalValue));
        return List.copyOf(selected);
    }

    private static void requireFreshEqual(EnterpriseDataObservation preview,
                                          EnterpriseDataObservation refreshed,
                                          Set<EnterpriseDataAdoptableFact> facts,
                                          List<RegisteredIdentifier> identities) {
        if (!preview.primaryIdentifier().key().equals(refreshed.primaryIdentifier().key())
                || preview.sourceKind() != refreshed.sourceKind()) throw conflict();
        if (facts.contains(EnterpriseDataAdoptableFact.REGISTERED_NAME)
                && !preview.registeredName().equals(refreshed.registeredName())) throw conflict();
        if (facts.contains(EnterpriseDataAdoptableFact.REGISTERED_ADDRESS)
                && !sameObservationAddress(preview.registeredAddress(), refreshed.registeredAddress())) throw conflict();
        for (RegisteredIdentifier selected : identities) {
            if (!preview.identifiers().contains(selected)) throw conflict();
        }
    }

    private static boolean sameObservationAddress(RegisteredAddressObservation left,
                                                  RegisteredAddressObservation right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void requireSamePolicy(EnterpriseDataAdoptionPolicy preview,
                                          EnterpriseDataAdoptionPolicy current) {
        if (!preview.ready() || !current.ready()
                || preview.sourceKind() != current.sourceKind()
                || !preview.sourceBindingRef().equals(current.sourceBindingRef())
                || !preview.adapterEvidenceRef().equals(current.adapterEvidenceRef())
                || !preview.policyVersion().equals(current.policyVersion())
                || !preview.attributionVersion().equals(current.attributionVersion())
                || !preview.mappingVersion().equals(current.mappingVersion())
                || !preview.allowedFacts().equals(current.allowedFacts())) throw conflict();
    }

    private static EnterpriseDataObservationVO observationVO(EnterpriseDataObservation source,
            EnterpriseDataPreview preview, io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution attribution) {
        RegisteredAddressObservation address = source.registeredAddress();
        return new EnterpriseDataObservationVO(identifierVO(source.primaryIdentifier()),
                source.identifiers().stream().map(EnterpriseDataAdoptionServiceImpl::identifierVO).toList(),
                source.legalJurisdiction() == null ? null : source.legalJurisdiction().value(),
                source.registeredName(), source.entityStatus().name(),
                source.formationDateKind() == null ? null : source.formationDateKind().name(),
                source.formationDate(), address == null ? null : new EnterpriseDataObservationVO.AddressVO(
                        address.countryCode(), address.region(), address.locality(), address.addressLine(),
                        address.postalCode()), source.sourceKind().name(), source.sourceDeclaredPartial(),
                attributionVO(attribution), preview.observedAt(), source.sourceUpdatedAt(), null,
                preview.expiresAt(), EnterpriseDataUsageMode.ADOPT_SELECTED_FACTS.name());
    }

    private static EnterpriseDataObservationVO.IdentifierVO identifierVO(RegisteredIdentifier value) {
        return new EnterpriseDataObservationVO.IdentifierVO(value.getIdentityScope().name(),
                value.getRegistryScheme().name(), value.getCanonicalValue(),
                value.getIdentifierStanding().name());
    }

    private static EnterpriseDataObservationVO.AttributionVO attributionVO(
            io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution value) {
        return new EnterpriseDataObservationVO.AttributionVO(value.context().name(), value.sourceName(),
                value.informationUrl(), value.disclaimer());
    }

    private static RegisteredIdentifierKey selector(String scope, String scheme, String value) {
        try {
            return new RegisteredIdentifierKey(
                    io.github.archetom.crm.domain.model.IdentityScope.valueOf(scope.trim().toUpperCase()),
                    io.github.archetom.crm.domain.model.RegistryScheme.valueOf(scheme.trim().toUpperCase()), value);
        } catch (RuntimeException exception) { throw invalid(); }
    }

    private static String addressSlot(String value) {
        if (value == null || !ADDRESS_SLOT.matcher(value).matches()) throw invalid();
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static Instant earliest(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static void require(Object value) { if (value == null) throw invalid(); }
    private static NonRetryableApplicationException invalid() {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID);
    }
    private static NonRetryableApplicationException unavailableCustomer() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
    }
    private static NonRetryableApplicationException unsupported() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ENTERPRISE_DATA_UNSUPPORTED);
    }
    private static NonRetryableApplicationException conflict() {
        return new NonRetryableApplicationException(ApplicationErrorCode.VERSION_CONFLICT);
    }
    private static ApplicationException unavailable() {
        return new ApplicationException(ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE);
    }
    private static ApplicationException mapSourceFailure(EnterpriseDataSourceError error) {
        return switch (error) {
            case NO_RESULT -> new ApplicationException(ApplicationErrorCode.ENTERPRISE_DATA_NO_RESULT);
            case LIMITED -> new ApplicationException(ApplicationErrorCode.ENTERPRISE_DATA_LIMITED);
            case UNAVAILABLE -> unavailable();
            case UNSUPPORTED -> new ApplicationException(ApplicationErrorCode.ENTERPRISE_DATA_UNSUPPORTED);
        };
    }

    private record AddressMutation(List<CustomerAddress> addresses, boolean changed) { }
}
