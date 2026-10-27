package io.github.archetom.crm.infra.external.companieshouse.runtime;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceError;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSourceException;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseNameSearchQuery;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.EnterpriseQueryMode;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Companies House runtime adapter. It maps the fixed public allowlist and discards all raw JSON. */
public final class CompaniesHouseEnterpriseDataSource implements EnterpriseDataSource {
    private static final LegalJurisdiction UK = new LegalJurisdiction("GB");
    private static final RegisteredIdentifierType COMPANY_NUMBER = new RegisteredIdentifierType(
            IdentityScope.UK_COMPANIES_HOUSE, RegistryScheme.GB_CH_NUMBER);
    private final CompaniesHouseEnterpriseDataProperties properties;
    private final CompaniesHouseHttpTransport transport;
    private final ObjectMapper json;
    private final boolean canaryOnly;

    public CompaniesHouseEnterpriseDataSource(CompaniesHouseEnterpriseDataProperties properties) {
        this(properties, new CompaniesHouseHttpTransport.JavaHttpTransport(java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()), new ObjectMapper(),
                false);
    }

    CompaniesHouseEnterpriseDataSource(CompaniesHouseEnterpriseDataProperties properties,
                                       CompaniesHouseHttpTransport transport, ObjectMapper json) {
        this(properties, transport, json, true);
    }

    private CompaniesHouseEnterpriseDataSource(CompaniesHouseEnterpriseDataProperties properties,
                                                CompaniesHouseHttpTransport transport, ObjectMapper json,
                                                boolean canaryOnly) {
        if (properties == null || transport == null || json == null) {
            throw new IllegalArgumentException("Companies House adapter dependencies are required");
        }
        this.properties = properties;
        this.transport = transport;
        this.json = json;
        this.canaryOnly = canaryOnly;
    }

    @Override public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
        requireTenant(tenantId);
        if (!ready(tenantId)) return EnterpriseDataCapabilitySnapshot.unavailable();
        return new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER), Set.of(UK), Set.of(COMPANY_NUMBER),
                properties.getMaxCandidates());
    }

    @Override public List<EnterpriseDataObservation> searchByRegisteredName(
            TenantId tenantId, EnterpriseNameSearchQuery query) {
        if (query == null) throw new IllegalArgumentException("registered-name query is required");
        requireReady(tenantId);
        if (!UK.sameValueAs(query.legalJurisdiction())) throw unsupported();
        URI uri = URI.create(properties.getApiBaseUri() + "search/companies?q="
                + encode(query.registeredName().value()) + "&items_per_page=" + query.limit());
        JsonNode items = body(response(uri)).path("items");
        if (!items.isArray()) throw unavailable();
        List<EnterpriseDataObservation> values = new ArrayList<>();
        for (JsonNode item : items) map(item).ifPresent(values::add);
        if (values.size() > query.limit()) throw unavailable();
        return List.copyOf(values);
    }

    @Override public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
            TenantId tenantId, RegisteredIdentifierKey identifier) {
        if (identifier == null) throw new IllegalArgumentException("registered identifier is required");
        requireReady(tenantId);
        if (!COMPANY_NUMBER.equals(identifier.type())) throw unsupported();
        JsonNode root = body(response(URI.create(properties.getApiBaseUri() + "company/"
                + encode(identifier.getCanonicalValue()))));
        EnterpriseDataObservation observation = map(root).orElseThrow(CompaniesHouseEnterpriseDataSource::unavailable);
        return observation.primaryIdentifier().key().equals(identifier) ? Optional.of(observation) : failMismatch();
    }

    private boolean ready(TenantId tenantId) {
        // Production requires an independent Companies House live-conformance report before this
        // adapter is composed; this constructor is used only by the private mapper canary tests.
        return canaryOnly && CompaniesHouseSourcePolicyCatalog.resolve(properties, tenantId).ready();
    }

    private void requireReady(TenantId tenantId) {
        requireTenant(tenantId);
        if (!ready(tenantId)) throw unavailable();
    }

    private CompaniesHouseHttpTransport.Response response(URI uri) {
        CompaniesHouseHttpTransport.Response response = transport.get(uri, properties.getApiKey(),
                properties.getRequestTimeout());
        if (response.status() == 404) throw new EnterpriseDataSourceException(EnterpriseDataSourceError.NO_RESULT);
        if (response.status() == 429) throw new EnterpriseDataSourceException(EnterpriseDataSourceError.LIMITED);
        if (response.status() < 200 || response.status() >= 300) throw unavailable();
        return response;
    }

    private JsonNode body(CompaniesHouseHttpTransport.Response response) {
        try { return json.readTree(response.body()); }
        catch (Exception exception) { throw unavailable(); }
    }

    private Optional<EnterpriseDataObservation> map(JsonNode source) {
        try {
            String number = text(source, "company_number");
            RegisteredIdentifier identifier = new RegisteredIdentifier(IdentityScope.UK_COMPANIES_HOUSE,
                    RegistryScheme.GB_CH_NUMBER, number, standing(source.path("company_status").asText(null)));
            return Optional.of(new EnterpriseDataObservation(identifier, List.of(identifier), UK,
                    text(source, "company_name"), status(source.path("company_status").asText(null)),
                    formationKind(source.path("date_of_creation").asText(null)),
                    date(source.path("date_of_creation").asText(null)), address(source.path("registered_office_address")),
                    EnterpriseDataSourceKind.OFFICIAL_REGISTRY, true, null, null));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static RegisteredAddressObservation address(JsonNode source) {
        if (!source.isObject()) return null;
        String line = join(source.path("premises").asText(null), source.path("address_line_1").asText(null),
                source.path("address_line_2").asText(null));
        if (line == null) return null;
        try { return new RegisteredAddressObservation("GB", source.path("region").asText(null),
                source.path("locality").asText(null), line, source.path("postal_code").asText(null)); }
        catch (RuntimeException exception) { return null; }
    }
    private static String join(String... values) {
        StringBuilder combined = new StringBuilder();
        for (String value : values) if (value != null && !value.isBlank()) {
            if (!combined.isEmpty()) combined.append(", ");
            combined.append(value.trim());
        }
        return combined.isEmpty() ? null : combined.toString();
    }
    private static FormationDateKind formationKind(String value) { return date(value) == null ? null : FormationDateKind.ESTABLISHED; }
    private static LocalDate date(String value) { try { return value == null ? null : LocalDate.parse(value); }
        catch (RuntimeException exception) { return null; } }
    private static RegisteredEntityStatus status(String value) {
        if ("active".equalsIgnoreCase(value)) return RegisteredEntityStatus.ACTIVE;
        if (value == null || value.isBlank()) return RegisteredEntityStatus.UNKNOWN;
        return RegisteredEntityStatus.INACTIVE;
    }
    private static IdentifierStanding standing(String value) {
        if ("active".equalsIgnoreCase(value)) return IdentifierStanding.CURRENT;
        if (value == null || value.isBlank()) return IdentifierStanding.UNKNOWN;
        return IdentifierStanding.STALE;
    }
    private static String text(JsonNode source, String field) {
        String value = source.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Companies House required field is absent");
        return value;
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static void requireTenant(TenantId value) { if (value == null) throw new IllegalArgumentException("tenant is required"); }
    private static EnterpriseDataSourceException unsupported() { return new EnterpriseDataSourceException(EnterpriseDataSourceError.UNSUPPORTED); }
    private static EnterpriseDataSourceException unavailable() { return new EnterpriseDataSourceException(EnterpriseDataSourceError.UNAVAILABLE); }
    private static Optional<EnterpriseDataObservation> failMismatch() { throw unavailable(); }
}
