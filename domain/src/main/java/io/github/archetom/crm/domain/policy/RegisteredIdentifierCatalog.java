package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegistryScheme;

import java.util.Locale;
import java.util.Set;

/** Governed registry scheme catalog and scheme-specific validation policy. */
public final class RegisteredIdentifierCatalog {

    private static final String USCC_ALPHABET = "0123456789ABCDEFGHJKLMNPQRTUWXY";
    private static final int[] USCC_WEIGHTS = {
            1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28
    };
    /**
     * B23-CH catalog v2026-07-20. This is an explicit registry-number class
     * directory, including historic and current Companies House prefixes; it
     * must not be replaced with a generic two-letter pattern.
     */
    private static final Set<String> COMPANIES_HOUSE_PREFIXES = Set.of(
            "AC", "BR", "CE", "CS", "EN", "ES", "FC", "FE", "GE", "GN",
            "GS", "IC", "IP", "LP", "NA", "NC", "NF", "NI", "NL", "NO",
            "NP", "NR", "NV", "NZ", "OC", "OE", "PC", "R0", "RC", "RS",
            "SA", "SC", "SE", "SF", "SG", "SI", "SL", "SO", "SP", "SR",
            "SZ", "ZC");

    private RegisteredIdentifierCatalog() {
    }

    public static boolean supports(IdentityScope identityScope, RegistryScheme registryScheme) {
        return (identityScope == IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE
                && registryScheme == RegistryScheme.CN_USCC)
                || (identityScope == IdentityScope.GLOBAL_LEI_SYSTEM
                && registryScheme == RegistryScheme.LEI)
                || (identityScope == IdentityScope.UK_COMPANIES_HOUSE
                && registryScheme == RegistryScheme.GB_CH_NUMBER);
    }

    public static String canonicalize(
            IdentityScope identityScope, RegistryScheme registryScheme, String value) {
        if (!supports(identityScope, registryScheme)) {
            throw new IllegalArgumentException("identity scope and registry scheme are not supported");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("registered identifier must not be blank");
        }
        return switch (registryScheme) {
            case CN_USCC -> requireValidUscc(value.trim().toUpperCase(Locale.ROOT));
            case LEI -> requireValidLei(value.trim().toUpperCase(Locale.ROOT));
            case GB_CH_NUMBER -> requireValidCompaniesHouseNumber(value.strip());
        };
    }

    private static String requireValidCompaniesHouseNumber(String value) {
        if (value.length() != 8) {
            throw new IllegalArgumentException(
                    "Companies House number must contain exactly 8 characters");
        }
        StringBuilder canonical = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 'a' && character <= 'z') {
                canonical.append((char) (character - ('a' - 'A')));
            } else if ((character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')) {
                canonical.append(character);
            } else {
                throw new IllegalArgumentException(
                        "Companies House number must contain only ASCII letters or digits");
            }
        }
        String normalized = canonical.toString();
        if (isAsciiDigits(normalized)) {
            return normalized;
        }
        if (!COMPANIES_HOUSE_PREFIXES.contains(normalized.substring(0, 2))) {
            throw new IllegalArgumentException("Companies House number prefix is not supported");
        }
        return normalized;
    }

    private static boolean isAsciiDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }

    private static String requireValidUscc(String value) {
        if (value.length() != 18) {
            throw new IllegalArgumentException("CN USCC must contain exactly 18 characters");
        }
        String allowedCategories = usccCategories(value.charAt(0));
        if (allowedCategories == null
                || allowedCategories.indexOf(value.charAt(1)) < 0) {
            throw new IllegalArgumentException(
                    "CN USCC registration department and organization category are invalid");
        }
        for (int index = 2; index < 8; index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                throw new IllegalArgumentException(
                        "CN USCC administrative division must contain six digits");
            }
        }
        int sum = 0;
        for (int index = 0; index < 17; index++) {
            int digit = USCC_ALPHABET.indexOf(value.charAt(index));
            if (digit < 0) {
                throw new IllegalArgumentException("CN USCC contains an invalid character");
            }
            sum += digit * USCC_WEIGHTS[index];
        }
        int expected = (31 - sum % 31) % 31;
        if (USCC_ALPHABET.indexOf(value.charAt(17)) != expected) {
            throw new IllegalArgumentException("CN USCC checksum is invalid");
        }
        return value;
    }

    private static String usccCategories(char registrationDepartment) {
        return switch (registrationDepartment) {
            case '1' -> "1239";
            case '2' -> "19";
            case '3' -> "123459";
            case '4' -> "19";
            case '5' -> "1239";
            case '6', '7' -> "129";
            case '8' -> "19";
            case '9' -> "123";
            case 'A' -> "19";
            case 'N' -> "1239";
            case 'Y' -> "1";
            default -> null;
        };
    }

    private static String requireValidLei(String value) {
        if (value.length() != 20 || !value.matches("[0-9A-Z]{20}")) {
            throw new IllegalArgumentException("LEI must contain exactly 20 ASCII letters or digits");
        }
        int remainder = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                remainder = (remainder * 10 + character - '0') % 97;
            } else {
                int expanded = character - 'A' + 10;
                remainder = (remainder * 100 + expanded) % 97;
            }
        }
        if (remainder != 1) {
            throw new IllegalArgumentException("LEI checksum is invalid");
        }
        return value;
    }
}
