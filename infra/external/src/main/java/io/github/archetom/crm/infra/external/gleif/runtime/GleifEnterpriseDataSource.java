package io.github.archetom.crm.infra.external.gleif.runtime;

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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GLEIF Level-1 HTTP adapter. It maps a bounded JSON:API response directly to the B23 allowlist,
 * discarding relationship, managing-LOU, person, account and native diagnostics fields.
 */
public final class GleifEnterpriseDataSource implements EnterpriseDataSource {
    private static final String SOURCE_KEY = "GLEIF_LEVEL1";
    private static final RegisteredIdentifierType LEI_TYPE = new RegisteredIdentifierType(
            IdentityScope.GLOBAL_LEI_SYSTEM, RegistryScheme.LEI);
    private static final RegisteredIdentifierType COMPANIES_HOUSE_TYPE = new RegisteredIdentifierType(
            IdentityScope.UK_COMPANIES_HOUSE, RegistryScheme.GB_CH_NUMBER);
    private static final Set<String> QUALIFIED_CATEGORIES = Set.of("GENERAL", "FUND", "BRANCH",
            "RESIDENT_GOVERNMENT_ENTITY", "INTERNATIONAL_ORGANIZATION");

    private final GleifEnterpriseDataProperties properties;
    private final GleifSharedQuota quota;
    private final GleifHttpTransport transport;
    private final ObjectMapper json;
    private final Clock clock;
    private final GleifConformanceReportSource conformanceReports;
    private final boolean canaryOnly;

    public GleifEnterpriseDataSource(GleifEnterpriseDataProperties properties, GleifSharedQuota quota) {
        this(properties, quota, new GleifHttpTransport.JavaHttpTransport(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build()), new ObjectMapper(), Clock.systemUTC(), null, false);
    }

    GleifEnterpriseDataSource(GleifEnterpriseDataProperties properties, GleifSharedQuota quota,
                              GleifHttpTransport transport, ObjectMapper json, Clock clock) {
        this(properties, quota, transport, json, clock, null, true);
    }

    GleifEnterpriseDataSource(GleifEnterpriseDataProperties properties, GleifSharedQuota quota,
                              GleifHttpTransport transport, ObjectMapper json, Clock clock,
                              GleifConformanceReportSource conformanceReports, boolean canaryOnly) {
        if (properties == null || quota == null || transport == null || json == null || clock == null) {
            throw new IllegalArgumentException("GLEIF adapter dependencies are required");
        }
        this.properties = properties;
        this.quota = quota;
        this.transport = transport;
        this.json = json;
        this.clock = clock;
        this.conformanceReports = conformanceReports;
        this.canaryOnly = canaryOnly;
    }

    @Override
    public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
        requireTenant(tenantId);
        GleifSourcePolicyCatalog.Resolved readiness = readiness(tenantId);
        if (!readiness.ready()) return EnterpriseDataCapabilitySnapshot.unavailable();
        return new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                readiness.jurisdictions(), Set.of(LEI_TYPE, COMPANIES_HOUSE_TYPE),
                properties.getMaxCandidates());
    }

    @Override
    public List<EnterpriseDataObservation> searchByRegisteredName(TenantId tenantId,
                                                                    EnterpriseNameSearchQuery query) {
        if (query == null) throw new IllegalArgumentException("registered-name query is required");
        GleifSourcePolicyCatalog.Resolved readiness = requireReady(tenantId);
        if (!readiness.jurisdictions().contains(query.legalJurisdiction())) throw unsupported();
        acquire(tenantId);
        URI uri = URI.create(properties.getApiBaseUri().toString() + "lei-records?filter%5Bentity.legalName%5D="
                + encoded(query.registeredName().value()) + "&filter%5Bentity.jurisdiction%5D="
                + encoded(query.legalJurisdiction().value()) + "&page%5Bsize%5D=" + query.limit()
                + "&page%5Bnumber%5D=1");
        List<JsonNode> records = records(response(uri), false);
        List<EnterpriseDataObservation> mapped = new ArrayList<>();
        for (JsonNode record : records) {
            map(record).filter(value -> value.legalJurisdiction().sameValueAs(query.legalJurisdiction()))
                    .ifPresent(mapped::add);
        }
        if (mapped.size() > query.limit()) throw unavailable();
        return List.copyOf(mapped);
    }

    @Override
    public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(TenantId tenantId,
                                                                            RegisteredIdentifierKey identifier) {
        if (identifier == null) throw new IllegalArgumentException("registered identifier is required");
        requireReady(tenantId);
        acquire(tenantId);
        if (LEI_TYPE.equals(identifier.type())) {
            URI uri = URI.create(properties.getApiBaseUri().toString() + "lei-records/" + identifier.getCanonicalValue());
            List<JsonNode> records = records(response(uri), true);
            if (records.isEmpty()) return Optional.empty();
            if (records.size() != 1) throw unavailable();
            EnterpriseDataObservation observation = map(records.getFirst()).orElseThrow(() -> unsupported());
            return observation.primaryIdentifier().key().equals(identifier) ? Optional.of(observation) : failMismatch();
        }
        if (!COMPANIES_HOUSE_TYPE.equals(identifier.type())) throw unsupported();
        URI uri = URI.create(properties.getApiBaseUri().toString()
                + "lei-records?filter%5Bentity.registeredAs%5D=" + encoded(identifier.getCanonicalValue())
                + "&page%5Bsize%5D=2&page%5Bnumber%5D=1");
        List<EnterpriseDataObservation> matches = records(response(uri), false).stream()
                .map(this::map).flatMap(Optional::stream)
                .filter(value -> value.identifiers().stream().anyMatch(item -> item.key().equals(identifier))).toList();
        if (matches.size() > 1) throw unavailable();
        return matches.stream().findFirst();
    }

    private GleifSourcePolicyCatalog.Resolved requireReady(TenantId tenantId) {
        requireTenant(tenantId);
        GleifSourcePolicyCatalog.Resolved readiness = readiness(tenantId);
        if (!readiness.ready()) throw unavailable();
        return readiness;
    }

    private GleifSourcePolicyCatalog.Resolved readiness(TenantId tenantId) {
        GleifSourcePolicyCatalog.Resolved policy = GleifSourcePolicyCatalog.resolve(properties, quota, tenantId);
        return (canaryOnly || GleifConformanceManifest.current(conformanceReports, clock.instant()))
                ? policy : GleifSourcePolicyCatalog.Resolved.unavailable();
    }

    private void acquire(TenantId tenantId) {
        Instant now = clock.instant();
        if (!quota.tryAcquire(tenantId, SOURCE_KEY, now, properties.getSharedQuota().getMaximumRequests(),
                properties.getSharedQuota().getWindow())) {
            throw new EnterpriseDataSourceException(EnterpriseDataSourceError.LIMITED);
        }
    }

    private GleifHttpTransport.Response response(URI uri) {
        GleifHttpTransport.Response response = transport.get(uri, properties.getRequestTimeout());
        if (response.status() == 429) throw new EnterpriseDataSourceException(EnterpriseDataSourceError.LIMITED);
        if (response.status() == 404) return response;
        if (response.status() < 200 || response.status() >= 300) throw unavailable();
        return response;
    }

    private List<JsonNode> records(GleifHttpTransport.Response response, boolean exact) {
        if (response.status() == 404) return List.of();
        try {
            JsonNode root = json.readTree(response.body());
            JsonNode data = root.path("data");
            if (data.isArray()) {
                List<JsonNode> values = new ArrayList<>();
                data.forEach(values::add);
                return List.copyOf(values);
            }
            return exact && data.isObject() ? List.of(data) : List.of();
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private Optional<EnterpriseDataObservation> map(JsonNode record) {
        try {
            if (!"lei-records".equals(text(record, "type"))) return Optional.empty();
            JsonNode attributes = record.path("attributes");
            JsonNode entity = attributes.path("entity");
            String category = text(entity, "category");
            if (!QUALIFIED_CATEGORIES.contains(category)) return Optional.empty();
            RegisteredIdentifier primary = new RegisteredIdentifier(IdentityScope.GLOBAL_LEI_SYSTEM,
                    RegistryScheme.LEI, text(attributes, "lei"), standing(attributes.path("registration").path("status").asText(null)));
            LegalJurisdiction jurisdiction = new LegalJurisdiction(text(entity, "jurisdiction"));
            List<RegisteredIdentifier> identifiers = new ArrayList<>();
            identifiers.add(primary);
            GleifRegistrationAuthorityCatalog.registeredIdentifier(text(entity.path("registeredAt"), "id"),
                    text(entity, "registeredAs")).ifPresent(identifiers::add);
            return Optional.of(new EnterpriseDataObservation(primary, identifiers, jurisdiction,
                    text(entity.path("legalName"), "name"), status(text(entity, "status")),
                    formationKind(entity), formationDate(entity), address(entity.path("legalAddress")),
                    EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX, true,
                    instant(attributes.path("registration").path("lastUpdateDate").asText(null)), null));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static FormationDateKind formationKind(JsonNode entity) {
        return formationDate(entity) == null ? null : FormationDateKind.ESTABLISHED;
    }
    private static LocalDate formationDate(JsonNode entity) {
        String value = entity.path("creationDate").asText(null);
        if (value == null || value.length() < 10) return null;
        try { return LocalDate.parse(value.substring(0, 10)); }
        catch (RuntimeException exception) { return null; }
    }
    private static RegisteredAddressObservation address(JsonNode address) {
        if (!address.isObject() || !address.path("addressLines").isArray()) return null;
        StringBuilder line = new StringBuilder();
        for (JsonNode value : address.path("addressLines")) {
            String segment = value.asText(null);
            if (segment == null || segment.isBlank()) continue;
            if (!line.isEmpty()) line.append(", ");
            line.append(segment.trim());
        }
        if (line.isEmpty()) return null;
        try { return new RegisteredAddressObservation(address.path("country").asText(null),
                address.path("region").asText(null), address.path("city").asText(null), line.toString(),
                address.path("postalCode").asText(null)); }
        catch (RuntimeException exception) { return null; }
    }
    private static RegisteredEntityStatus status(String value) {
        if ("ACTIVE".equals(value)) return RegisteredEntityStatus.ACTIVE;
        if ("INACTIVE".equals(value)) return RegisteredEntityStatus.INACTIVE;
        return RegisteredEntityStatus.UNKNOWN;
    }
    private static IdentifierStanding standing(String value) {
        if (value == null) return IdentifierStanding.UNKNOWN;
        return switch (value) {
            case "ISSUED" -> IdentifierStanding.CURRENT;
            case "LAPSED" -> IdentifierStanding.STALE;
            case "PENDING_TRANSFER", "PENDING_ARCHIVAL" -> IdentifierStanding.TRANSITIONING;
            case "DUPLICATE" -> IdentifierStanding.SUPERSEDED;
            case "ANNULLED" -> IdentifierStanding.INVALID;
            case "RETIRED" -> IdentifierStanding.RETIRED;
            default -> IdentifierStanding.UNKNOWN;
        };
    }
    private static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (RuntimeException exception) { return null; }
    }
    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("GLEIF required field is absent");
        return value;
    }
    private static String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static Optional<EnterpriseDataObservation> failMismatch() { throw unavailable(); }
    private static EnterpriseDataSourceException unavailable() {
        return new EnterpriseDataSourceException(EnterpriseDataSourceError.UNAVAILABLE);
    }
    private static EnterpriseDataSourceException unsupported() {
        return new EnterpriseDataSourceException(EnterpriseDataSourceError.UNSUPPORTED);
    }
    private static void requireTenant(TenantId tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("tenant must not be null");
    }
}
