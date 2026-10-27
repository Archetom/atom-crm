package io.github.archetom.crm.infra.external.gleif;

import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test-scope B23-RA catalog for GLEIF Companies House registration authorities.
 *
 * <p>The mapping is pinned independently from the domain scheme validator. A valid Companies
 * House number is not enough: its number class must also belong to the exact GLEIF RA row that
 * supplied the EntityID. Unknown or inconsistent native values produce no secondary identity.</p>
 */
final class GleifRegistrationAuthorityIdentifierMapper {

    /** GLEIF RA List 1.8.1 plus B23-CH prefix catalog, reviewed 2026-07-20. */
    private static final String CATALOG_VERSION = "GLEIF-RA-1.8.1/B23-CH-2026-07-20";
    private static final String ENGLAND_AND_WALES_RA = "RA000585";
    private static final String NORTHERN_IRELAND_RA = "RA000586";
    private static final String SCOTLAND_RA = "RA000587";

    private static final Set<String> ENGLAND_AND_WALES_PREFIXES = Set.of(
            "AC", "BR", "CE", "FC", "FE", "GE", "IC", "IP", "LP", "OC",
            "OE", "PC", "RC", "RS", "SE", "ZC");
    private static final Set<String> NORTHERN_IRELAND_PREFIXES = Set.of(
            "EN", "GN", "NA", "NC", "NF", "NI", "NL", "NO", "NP", "NR",
            "NV", "NZ", "R0");
    private static final Set<String> SCOTLAND_PREFIXES = Set.of(
            "CS", "ES", "GS", "SA", "SC", "SF", "SG", "SI", "SL", "SO",
            "SP", "SR", "SZ");

    private static final Map<String, NumberClass> NUMBER_CLASSES = Map.of(
            ENGLAND_AND_WALES_RA,
            new NumberClass(true, ENGLAND_AND_WALES_PREFIXES),
            NORTHERN_IRELAND_RA,
            new NumberClass(false, NORTHERN_IRELAND_PREFIXES),
            SCOTLAND_RA,
            new NumberClass(false, SCOTLAND_PREFIXES));

    Optional<RegisteredIdentifier> map(String registrationAuthorityId, String entityId) {
        NumberClass numberClass = NUMBER_CLASSES.get(registrationAuthorityId);
        if (numberClass == null || entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        try {
            RegisteredIdentifierKey key = new RegisteredIdentifierKey(
                    IdentityScope.UK_COMPANIES_HOUSE,
                    RegistryScheme.GB_CH_NUMBER,
                    entityId);
            if (!numberClass.accepts(key.getCanonicalValue())) {
                return Optional.empty();
            }
            return Optional.of(new RegisteredIdentifier(key, IdentifierStanding.UNKNOWN));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    String catalogVersion() {
        return CATALOG_VERSION;
    }

    private record NumberClass(boolean acceptsNumeric, Set<String> prefixes) {

        private NumberClass {
            prefixes = Set.copyOf(prefixes);
        }

        private boolean accepts(String canonicalValue) {
            return isNumeric(canonicalValue)
                    ? acceptsNumeric
                    : prefixes.contains(canonicalValue.substring(0, 2));
        }

        private boolean isNumeric(String value) {
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
            }
            return true;
        }
    }
}
