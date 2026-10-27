package io.github.archetom.crm.infra.external.gleif.runtime;

import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Small, versioned RA allowlist for the only secondary identity currently supported by B23.
 * Unknown RA rows are omitted, never guessed. Its digest is part of GLEIF deployment readiness.
 */
public final class GleifRegistrationAuthorityCatalog {
    public static final String VERSION = "GLEIF-RA-1.8.1/B23-CH-2026-07-23";
    public static final String DIGEST = digest(VERSION + "\n"
            + "RA000585|numeric=true|AC,BR,CE,FC,FE,GE,IC,IP,LP,OC,OE,PC,RC,RS,SE,ZC\n"
            + "RA000586|numeric=false|EN,GN,NA,NC,NF,NI,NL,NO,NP,NR,NV,NZ,R0\n"
            + "RA000587|numeric=false|CS,ES,GS,SA,SC,SF,SG,SI,SL,SO,SP,SR,SZ\n");

    private static final Map<String, NumberClass> NUMBER_CLASSES = Map.of(
            "RA000585", new NumberClass(true, Set.of("AC", "BR", "CE", "FC", "FE", "GE", "IC", "IP", "LP", "OC", "OE", "PC", "RC", "RS", "SE", "ZC")),
            "RA000586", new NumberClass(false, Set.of("EN", "GN", "NA", "NC", "NF", "NI", "NL", "NO", "NP", "NR", "NV", "NZ", "R0")),
            "RA000587", new NumberClass(false, Set.of("CS", "ES", "GS", "SA", "SC", "SF", "SG", "SI", "SL", "SO", "SP", "SR", "SZ")));

    private GleifRegistrationAuthorityCatalog() { }

    public static Optional<RegisteredIdentifier> registeredIdentifier(String raId, String entityId) {
        NumberClass numberClass = NUMBER_CLASSES.get(raId);
        if (numberClass == null || entityId == null || entityId.isBlank()) return Optional.empty();
        try {
            RegisteredIdentifierKey key = new RegisteredIdentifierKey(
                    IdentityScope.UK_COMPANIES_HOUSE, RegistryScheme.GB_CH_NUMBER, entityId);
            return numberClass.accepts(key.getCanonicalValue())
                    ? Optional.of(new RegisteredIdentifier(key, IdentifierStanding.UNKNOWN))
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String digest(String canonical) {
        try {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("GLEIF RA catalog digest is unavailable", exception);
        }
    }

    private record NumberClass(boolean acceptsNumeric, Set<String> prefixes) {
        private NumberClass { prefixes = Set.copyOf(prefixes); }
        private boolean accepts(String value) {
            boolean numeric = value.chars().allMatch(character -> character >= '0' && character <= '9');
            return numeric ? acceptsNumeric : prefixes.contains(value.substring(0, 2));
        }
    }
}
