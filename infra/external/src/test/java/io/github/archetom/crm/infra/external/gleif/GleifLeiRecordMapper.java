package io.github.archetom.crm.infra.external.gleif;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.RegisteredAddressObservation;
import io.github.archetom.crm.domain.model.EnterpriseDataSourceKind;
import io.github.archetom.crm.domain.model.FormationDateKind;
import io.github.archetom.crm.domain.model.IdentifierStanding;
import io.github.archetom.crm.domain.model.IdentityScope;
import io.github.archetom.crm.domain.model.RegisteredEntityStatus;
import io.github.archetom.crm.domain.model.RegistryScheme;
import io.github.archetom.crm.domain.valueobject.LegalJurisdiction;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifier;
import io.github.archetom.crm.domain.valueobject.RegisteredIdentifierKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Maps one synthetic GLEIF JSON:API-like native shape to the fixed B23a allowlist. */
final class GleifLeiRecordMapper {

    private static final String JSON_API_VERSION = "1.0";
    private static final String LEI_RECORD_TYPE = "lei-records";
    private static final Set<String> QUALIFIED_CATEGORIES = Set.of(
            "GENERAL",
            "FUND",
            "BRANCH",
            "RESIDENT_GOVERNMENT_ENTITY",
            "INTERNATIONAL_ORGANIZATION");
    private final GleifRegistrationAuthorityIdentifierMapper registrationAuthorityMapper =
            new GleifRegistrationAuthorityIdentifierMapper();

    EnterpriseDataObservation mapExact(
            GleifLeiPage page, RegisteredIdentifierKey expectedIdentifier) {
        requireEnvelope(page);
        if (page.data().size() != 1) {
            throw new GleifMappingException();
        }
        GleifLeiRecord record = requireRecord(page.data().getFirst());
        requireQualified(record);
        return mapQualified(record, expectedIdentifier, null);
    }

    List<EnterpriseDataObservation> mapSearch(
            GleifLeiPage page, LegalJurisdiction expectedJurisdiction, int limit) {
        requireEnvelope(page);
        if (page.data().size() > limit) {
            throw new GleifMappingException();
        }
        List<EnterpriseDataObservation> observations = new ArrayList<>();
        for (GleifLeiRecord candidate : page.data()) {
            GleifLeiRecord record = requireRecord(candidate);
            if (!isQualified(record.entity().category())) {
                continue;
            }
            observations.add(mapQualified(record, null, expectedJurisdiction));
        }
        return List.copyOf(observations);
    }

    boolean isQualifiedCategory(String category) {
        return isQualified(category);
    }

    private EnterpriseDataObservation mapQualified(
            GleifLeiRecord record,
            RegisteredIdentifierKey expectedIdentifier,
            LegalJurisdiction expectedJurisdiction) {
        try {
            if (!LEI_RECORD_TYPE.equals(record.type()) || record.registration() == null) {
                throw new GleifMappingException();
            }
            RegisteredIdentifier primaryIdentifier = new RegisteredIdentifier(
                    new RegisteredIdentifierKey(
                            IdentityScope.GLOBAL_LEI_SYSTEM,
                            RegistryScheme.LEI,
                            record.lei()),
                    mapStanding(record.registration().status()));
            if (expectedIdentifier != null && !expectedIdentifier.equals(primaryIdentifier.key())) {
                throw new GleifMappingException();
            }

            LegalJurisdiction jurisdiction = new LegalJurisdiction(
                    record.entity().legalJurisdiction());
            if (expectedJurisdiction != null
                    && !expectedJurisdiction.sameValueAs(jurisdiction)) {
                throw new GleifMappingException();
            }

            FormationDateKind formationDateKind = record.entity().creationDate() == null
                    ? null
                    : FormationDateKind.ESTABLISHED;
            List<RegisteredIdentifier> identifiers = new ArrayList<>();
            identifiers.add(primaryIdentifier);
            registrationAuthorityMapper.map(
                            record.entity().registrationAuthorityId(),
                            record.entity().registrationAuthorityEntityId())
                    .ifPresent(identifiers::add);
            return new EnterpriseDataObservation(
                    primaryIdentifier,
                    identifiers,
                    jurisdiction,
                    record.entity().legalName(),
                    mapEntityStatus(record.entity().status()),
                    formationDateKind,
                    record.entity().creationDate(),
                    mapAddress(record.entity().legalAddress()),
                    EnterpriseDataSourceKind.OPEN_REFERENCE_INDEX,
                    record.sourceDeclaredPartial(),
                    record.registration().lastUpdateDate(),
                    null);
        } catch (GleifMappingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GleifMappingException();
        }
    }

    private RegisteredAddressObservation mapAddress(GleifAddress address) {
        if (address == null) {
            return null;
        }
        List<String> lines = address.addressLines();
        if (lines == null) {
            return null;
        }
        String addressLine = lines.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(String::trim)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        if (addressLine == null) {
            return null;
        }
        return new RegisteredAddressObservation(
                address.country(), address.region(), address.city(), addressLine,
                address.postalCode());
    }

    private RegisteredEntityStatus mapEntityStatus(String status) {
        if (status == null) {
            return RegisteredEntityStatus.UNKNOWN;
        }
        return switch (status) {
            case "ACTIVE" -> RegisteredEntityStatus.ACTIVE;
            case "INACTIVE" -> RegisteredEntityStatus.INACTIVE;
            default -> RegisteredEntityStatus.UNKNOWN;
        };
    }

    private IdentifierStanding mapStanding(String status) {
        if (status == null) {
            return IdentifierStanding.UNKNOWN;
        }
        return switch (status) {
            case "ISSUED" -> IdentifierStanding.CURRENT;
            case "LAPSED" -> IdentifierStanding.STALE;
            case "PENDING_TRANSFER", "PENDING_ARCHIVAL" -> IdentifierStanding.TRANSITIONING;
            case "DUPLICATE" -> IdentifierStanding.SUPERSEDED;
            case "ANNULLED" -> IdentifierStanding.INVALID;
            case "RETIRED" -> IdentifierStanding.RETIRED;
            default -> IdentifierStanding.UNKNOWN;
        };
    }

    private void requireEnvelope(GleifLeiPage page) {
        if (page == null || !JSON_API_VERSION.equals(page.jsonApiVersion())
                || page.data() == null) {
            throw new GleifMappingException();
        }
    }

    private GleifLeiRecord requireRecord(GleifLeiRecord record) {
        if (record == null || record.entity() == null) {
            throw new GleifMappingException();
        }
        return record;
    }

    private void requireQualified(GleifLeiRecord record) {
        if (!isQualified(record.entity().category())) {
            throw new GleifQualificationException();
        }
    }

    private boolean isQualified(String category) {
        return category != null && QUALIFIED_CATEGORIES.contains(category);
    }
}

final class GleifMappingException extends RuntimeException {

    GleifMappingException() {
        super("GLEIF native mapping failed");
    }
}

final class GleifQualificationException extends RuntimeException {

    GleifQualificationException() {
        super("GLEIF organization qualification failed");
    }
}
