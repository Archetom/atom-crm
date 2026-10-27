package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.EnterpriseIdentifierLookupRequest;
import io.github.archetom.crm.api.dto.request.EnterpriseNameSearchRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataAttribution;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicy;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataReadPolicyResolver;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.EnterpriseDataQueryService;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.EnterpriseDataCapabilityVO;
import io.github.archetom.crm.application.vo.EnterpriseDataObservationVO;
import io.github.archetom.crm.domain.model.EnterpriseDataAttributionContext;
import io.github.archetom.crm.domain.model.EnterpriseDataUsageMode;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * B23a read kernel. External work is deliberately completed in {@code prepare},
 * before the query template opens its database snapshot.
 */
@Service
@RequiredArgsConstructor
public class EnterpriseDataQueryServiceImpl implements EnterpriseDataQueryService {

    private final EnterpriseDataSource dataSource;
    private final EnterpriseDataReadPolicyResolver readPolicyResolver;
    private final OrganizationAccessGuard accessGuard;
    private final QueryServiceTemplate queryTemplate;
    private final Clock clock;

    @Override
    public Result<EnterpriseDataCapabilityVO> capabilities(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.ENTERPRISE_DATA_CAPABILITY_GET,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private ResolvedCapability capability;

                    @Override
                    public void validate() {
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.ENTERPRISE_DATA_READ);
                    }

                    @Override
                    public void prepare() {
                        try {
                            capability = resolveCapability(tenantId);
                        } catch (RuntimeException exception) {
                            capability = ResolvedCapability.unavailable();
                        }
                    }

                    @Override
                    public EnterpriseDataCapabilityVO execute() {
                        return capabilityVO(capability);
                    }
                });
    }

    @Override
    public Result<List<EnterpriseDataObservationVO>> search(
            AuthenticatedCaller caller, EnterpriseNameSearchRequest request) {
        return queryTemplate.execute(UseCaseOperation.ENTERPRISE_DATA_NAME_SEARCH,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private SearchKeyword registeredName;
                    private LegalJurisdiction legalJurisdiction;
                    private List<GovernedObservation> observations;
                    private ResolvedCapability capability;

                    @Override
                    public void validate() {
                        requireRequest(request);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.ENTERPRISE_DATA_READ);
                        registeredName = new SearchKeyword(request.getRegisteredName());
                        legalJurisdiction = new LegalJurisdiction(request.getLegalJurisdiction());
                    }

                    @Override
                    public void prepare() {
                        try {
                            capability = requireReady(
                                    tenantId, EnterpriseQueryMode.REGISTERED_NAME_SEARCH);
                            if (!capability.adapter().legalJurisdictions()
                                    .contains(legalJurisdiction)) {
                                throw unsupported();
                            }
                            List<EnterpriseDataObservation> returned = dataSource.searchByRegisteredName(
                                    tenantId, new EnterpriseNameSearchQuery(registeredName,
                                            legalJurisdiction,
                                            capability.adapter().maxCandidates()));
                            observations = requireSearchResults(
                                    returned, capability, legalJurisdiction);
                        } catch (EnterpriseDataSourceException exception) {
                            throw mapFailure(exception.error());
                        } catch (ApplicationException exception) {
                            throw exception;
                        } catch (RuntimeException exception) {
                            throw unavailable();
                        }
                    }

                    @Override
                    public List<EnterpriseDataObservationVO> execute() {
                        return observations.stream().map(
                                observation -> observationVO(observation, capability,
                                        EnterpriseDataAttributionContext.SEARCH_RESULT)).toList();
                    }
                });
    }

    @Override
    public Result<EnterpriseDataObservationVO> lookup(
            AuthenticatedCaller caller, EnterpriseIdentifierLookupRequest request) {
        return queryTemplate.execute(UseCaseOperation.ENTERPRISE_DATA_IDENTIFIER_LOOKUP,
                new ServiceOperation<>() {
                    private TenantId tenantId;
                    private RegisteredIdentifierKey selector;
                    private GovernedObservation observation;
                    private ResolvedCapability capability;

                    @Override
                    public void validate() {
                        requireRequest(request);
                        tenantId = accessGuard.requireActiveMembership(
                                caller, AuthorityCatalog.ENTERPRISE_DATA_READ);
                        selector = new RegisteredIdentifierKey(
                                parse(IdentityScope.class, request.getIdentityScope()),
                                parse(RegistryScheme.class, request.getRegistryScheme()),
                                request.getValue());
                    }

                    @Override
                    public void prepare() {
                        try {
                            capability = requireReady(
                                    tenantId, EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER);
                            if (!capability.adapter().identifierTypes().contains(selector.type())) {
                                throw unsupported();
                            }
                            EnterpriseDataObservation returned = dataSource
                                    .findByRegisteredIdentifier(tenantId, selector)
                                    .orElseThrow(() -> new EnterpriseDataSourceException(
                                            EnterpriseDataSourceError.NO_RESULT));
                            requireObservation(returned, capability);
                            if (!returned.primaryIdentifier().key().equals(selector)) {
                                throw unavailable();
                            }
                            observation = governed(returned, capability);
                        } catch (EnterpriseDataSourceException exception) {
                            throw mapFailure(exception.error());
                        } catch (ApplicationException exception) {
                            throw exception;
                        } catch (RuntimeException exception) {
                            throw unavailable();
                        }
                    }

                    @Override
                    public EnterpriseDataObservationVO execute() {
                        return observationVO(observation, capability,
                                EnterpriseDataAttributionContext.DETAIL);
                    }
                });
    }

    private ResolvedCapability requireReady(
            TenantId tenantId, EnterpriseQueryMode queryMode) {
        ResolvedCapability capability = resolveCapability(tenantId);
        if (!capability.ready()) {
            throw unavailable();
        }
        if (!capability.adapter().queryModes().contains(queryMode)) {
            throw unsupported();
        }
        return capability;
    }

    private ResolvedCapability resolveCapability(TenantId tenantId) {
        EnterpriseDataReadPolicy policy = readPolicyResolver.resolve(tenantId);
        if (policy == null || !policy.ready()) {
            return ResolvedCapability.unavailable();
        }
        EnterpriseDataCapabilitySnapshot adapter = requireCapability(
                dataSource.capabilities(tenantId));
        if (!adapter.ready()) {
            return ResolvedCapability.unavailable();
        }
        EnterpriseDataUsageMode usageMode = EnterpriseDataUsageMode.EPHEMERAL_ONLY
                .mostRestrictive(policy.maximumUsageMode());
        if (usageMode == EnterpriseDataUsageMode.NOT_READY) {
            return ResolvedCapability.unavailable();
        }
        return new ResolvedCapability(adapter, policy, usageMode);
    }

    private EnterpriseDataCapabilitySnapshot requireCapability(
            EnterpriseDataCapabilitySnapshot capability) {
        if (capability == null) {
            throw unavailable();
        }
        return capability;
    }

    private List<GovernedObservation> requireSearchResults(
            List<EnterpriseDataObservation> returned,
            ResolvedCapability capability,
            LegalJurisdiction expectedJurisdiction) {
        if (returned == null || returned.size() > capability.adapter().maxCandidates()
                || returned.size() > EnterpriseDataCapabilitySnapshot.CORE_MAX_CANDIDATES) {
            throw unavailable();
        }
        List<GovernedObservation> copy = new ArrayList<>(returned.size());
        Set<RegisteredIdentifierKey> seen = new HashSet<>();
        Instant observedAt = clock.instant();
        Instant expiresAt = observedAt.plus(
                capability.policy().maximumObservationTtl());
        for (EnterpriseDataObservation observation : returned) {
            requireObservation(observation, capability);
            if ((observation.legalJurisdiction() != null
                    && !observation.legalJurisdiction().sameValueAs(expectedJurisdiction))
                    || !seen.add(observation.primaryIdentifier().key())) {
                throw unavailable();
            }
            copy.add(new GovernedObservation(observation, observedAt, expiresAt));
        }
        return List.copyOf(copy);
    }

    private void requireObservation(
            EnterpriseDataObservation observation,
            ResolvedCapability capability) {
        if (observation == null
                || !capability.adapter().identifierTypes().contains(
                        observation.primaryIdentifier().key().type())) {
            throw unavailable();
        }
        if (observation.identifiers().stream().anyMatch(identifier ->
                !capability.adapter().identifierTypes().contains(identifier.key().type()))) {
            throw unavailable();
        }
        if (observation.sourceKind() != capability.policy().sourceKind()) {
            throw unavailable();
        }
    }

    private GovernedObservation governed(
            EnterpriseDataObservation source,
            ResolvedCapability capability) {
        Instant observedAt = clock.instant();
        return new GovernedObservation(source, observedAt,
                observedAt.plus(capability.policy().maximumObservationTtl()));
    }

    private EnterpriseDataCapabilityVO capabilityVO(ResolvedCapability capability) {
        EnterpriseDataCapabilitySnapshot adapter = capability.adapter();
        List<String> modes = adapter.queryModes().stream().map(Enum::name).sorted().toList();
        List<String> jurisdictions = adapter.legalJurisdictions().stream()
                .map(LegalJurisdiction::value).sorted().toList();
        List<EnterpriseDataCapabilityVO.IdentifierTypeVO> identifierTypes = adapter.identifierTypes()
                .stream()
                .sorted(Comparator.comparing((RegisteredIdentifierType type) ->
                                type.identityScope().name())
                        .thenComparing(type -> type.registryScheme().name()))
                .map(type -> new EnterpriseDataCapabilityVO.IdentifierTypeVO(
                        type.identityScope().name(), type.registryScheme().name()))
                .toList();
        return new EnterpriseDataCapabilityVO(capability.ready(), modes, jurisdictions,
                identifierTypes, adapter.maxCandidates(), capability.usageMode().name());
    }

    private EnterpriseDataObservationVO observationVO(
            GovernedObservation governed,
            ResolvedCapability capability,
            EnterpriseDataAttributionContext attributionContext) {
        EnterpriseDataObservation source = governed.source();
        RegisteredAddressObservation address = source.registeredAddress();
        EnterpriseDataAttribution attribution = capability.policy()
                .attribution(attributionContext);
        return new EnterpriseDataObservationVO(
                identifierVO(source.primaryIdentifier()),
                source.identifiers().stream().map(this::identifierVO).toList(),
                source.legalJurisdiction() == null
                        ? null : source.legalJurisdiction().value(),
                source.registeredName(), source.entityStatus().name(),
                source.formationDateKind() == null
                        ? null : source.formationDateKind().name(),
                source.formationDate(),
                address == null ? null : new EnterpriseDataObservationVO.AddressVO(
                        address.countryCode(), address.region(), address.locality(),
                        address.addressLine(), address.postalCode()),
                source.sourceKind().name(), source.sourceDeclaredPartial(),
                new EnterpriseDataObservationVO.AttributionVO(
                        attribution.context().name(), attribution.sourceName(),
                        attribution.informationUrl(), attribution.disclaimer()),
                governed.observedAt(), source.sourceUpdatedAt(), source.sourceRevision(),
                governed.expiresAt(), capability.usageMode().name());
    }

    private EnterpriseDataObservationVO.IdentifierVO identifierVO(RegisteredIdentifier source) {
        return new EnterpriseDataObservationVO.IdentifierVO(
                source.getIdentityScope().name(), source.getRegistryScheme().name(),
                source.getCanonicalValue(), source.getIdentifierStanding().name());
    }

    private RuntimeException mapFailure(EnterpriseDataSourceError error) {
        return switch (error) {
            case NO_RESULT -> new NonRetryableApplicationException(
                    ApplicationErrorCode.ENTERPRISE_DATA_NO_RESULT);
            case LIMITED -> new ApplicationException(
                    ApplicationErrorCode.ENTERPRISE_DATA_LIMITED);
            case UNAVAILABLE -> unavailable();
            case UNSUPPORTED -> unsupported();
        };
    }

    private ApplicationException unavailable() {
        return new ApplicationException(ApplicationErrorCode.ENTERPRISE_DATA_UNAVAILABLE);
    }

    private NonRetryableApplicationException unsupported() {
        return new NonRetryableApplicationException(
                ApplicationErrorCode.ENTERPRISE_DATA_UNSUPPORTED);
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new NonRetryableApplicationException(
                    ApplicationErrorCode.PARAMETER_INVALID,
                    ApplicationErrorCode.PARAMETER_INVALID.getDescription());
        }
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("enterprise data selector is invalid");
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }

    private record ResolvedCapability(
            EnterpriseDataCapabilitySnapshot adapter,
            EnterpriseDataReadPolicy policy,
            EnterpriseDataUsageMode usageMode
    ) {
        private ResolvedCapability {
            if (adapter == null || policy == null || usageMode == null) {
                throw new IllegalArgumentException("resolved enterprise data capability is invalid");
            }
        }

        static ResolvedCapability unavailable() {
            return new ResolvedCapability(EnterpriseDataCapabilitySnapshot.unavailable(),
                    EnterpriseDataReadPolicy.unavailable(),
                    EnterpriseDataUsageMode.NOT_READY);
        }

        boolean ready() {
            return adapter.ready() && policy.ready()
                    && usageMode != EnterpriseDataUsageMode.NOT_READY;
        }
    }

    private record GovernedObservation(
            EnterpriseDataObservation source,
            Instant observedAt,
            Instant expiresAt
    ) {
    }
}
