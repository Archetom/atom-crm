package io.github.archetom.crm.infra.external.gleif;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Offline-only B23-O-L1 source used to execute the reusable B23-OC contract. */
final class GleifEnterpriseDataSource implements EnterpriseDataSource {

    private static final RegisteredIdentifierType LEI_TYPE = new RegisteredIdentifierType(
            IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI);
    private static final RegisteredIdentifierType COMPANIES_HOUSE_TYPE =
            new RegisteredIdentifierType(
                    IdentityScope.UK_COMPANIES_HOUSE, RegistryScheme.GB_CH_NUMBER);

    private final TenantId bindingTenantId;
    private final Set<LegalJurisdiction> legalJurisdictions;
    private final GleifTransport transport;
    private final GleifLeiRecordMapper mapper;

    GleifEnterpriseDataSource(
            TenantId bindingTenantId,
            Set<LegalJurisdiction> legalJurisdictions,
            GleifTransport transport) {
        if (bindingTenantId == null || legalJurisdictions == null
                || legalJurisdictions.isEmpty()
                || legalJurisdictions.stream().anyMatch(Objects::isNull)
                || transport == null) {
            throw new IllegalArgumentException("GLEIF offline source binding is invalid");
        }
        this.bindingTenantId = bindingTenantId;
        this.legalJurisdictions = Set.copyOf(legalJurisdictions);
        this.transport = transport;
        this.mapper = new GleifLeiRecordMapper();
    }

    @Override
    public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
        requireTenant(tenantId);
        if (!bindingTenantId.sameValueAs(tenantId)) {
            return EnterpriseDataCapabilitySnapshot.unavailable();
        }
        return new EnterpriseDataCapabilitySnapshot(
                true,
                Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                legalJurisdictions,
                Set.of(LEI_TYPE, COMPANIES_HOUSE_TYPE),
                EnterpriseDataCapabilitySnapshot.CORE_MAX_CANDIDATES);
    }

    @Override
    public List<EnterpriseDataObservation> searchByRegisteredName(
            TenantId tenantId, EnterpriseNameSearchQuery query) {
        requireBoundTenant(tenantId);
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (!legalJurisdictions.contains(query.legalJurisdiction())) {
            throw sourceFailure(EnterpriseDataSourceError.UNSUPPORTED);
        }
        GleifLegalNameQuery nativeQuery = new GleifLegalNameQuery(
                query.registeredName().value(), query.legalJurisdiction().value(), query.limit());
        GleifLeiPage page = invoke(() -> transport.searchByLegalName(tenantId, nativeQuery));
        try {
            return mapper.mapSearch(page, query.legalJurisdiction(), query.limit());
        } catch (RuntimeException exception) {
            throw sourceFailure(EnterpriseDataSourceError.UNAVAILABLE);
        }
    }

    @Override
    public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
            TenantId tenantId, RegisteredIdentifierKey identifier) {
        requireBoundTenant(tenantId);
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        if (!LEI_TYPE.equals(identifier.type())) {
            throw sourceFailure(EnterpriseDataSourceError.UNSUPPORTED);
        }
        GleifLeiPage page = invoke(() -> transport.findByLei(
                tenantId, identifier.getCanonicalValue()));
        if (page != null && "1.0".equals(page.jsonApiVersion())
                && page.data() != null && page.data().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.mapExact(page, identifier));
        } catch (GleifQualificationException exception) {
            throw sourceFailure(EnterpriseDataSourceError.UNSUPPORTED);
        } catch (RuntimeException exception) {
            throw sourceFailure(EnterpriseDataSourceError.UNAVAILABLE);
        }
    }

    private GleifLeiPage invoke(Supplier<GleifLeiPage> sourceCall) {
        try {
            return sourceCall.get();
        } catch (GleifTransportException exception) {
            EnterpriseDataSourceError error = switch (exception.error()) {
                case LIMITED -> EnterpriseDataSourceError.LIMITED;
                case UNSUPPORTED -> EnterpriseDataSourceError.UNSUPPORTED;
                case UNAVAILABLE -> EnterpriseDataSourceError.UNAVAILABLE;
            };
            throw sourceFailure(error);
        } catch (RuntimeException exception) {
            throw sourceFailure(EnterpriseDataSourceError.UNAVAILABLE);
        }
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant must not be null");
        }
    }

    private void requireBoundTenant(TenantId tenantId) {
        requireTenant(tenantId);
        if (!bindingTenantId.sameValueAs(tenantId)) {
            throw sourceFailure(EnterpriseDataSourceError.UNAVAILABLE);
        }
    }

    private EnterpriseDataSourceException sourceFailure(EnterpriseDataSourceError error) {
        return new EnterpriseDataSourceException(error);
    }
}
