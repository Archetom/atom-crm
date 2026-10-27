package io.github.archetom.crm.infra.external;

import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataCapabilitySnapshot;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataObservation;
import io.github.archetom.crm.application.port.out.enterprisedata.EnterpriseDataSource;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic dev/test fixture containing only conspicuously synthetic organizations. */
@Component
@Profile("(dev | test) & !prod")
@ConditionalOnProperty(
        prefix = "atom.enterprise-data.synthetic",
        name = "enabled",
        havingValue = "true")
public final class SyntheticEnterpriseDataSource implements EnterpriseDataSource {

    static final String SYNTHETIC_USCC = "91110000F1XTURE1AN";
    static final String SYNTHETIC_LEI = "0000SYNTHETICFIXTU42";
    private static final Instant SYNTHETIC_SOURCE_UPDATED_AT =
            Instant.parse("2026-07-19T00:00:00Z");

    @Override
    public EnterpriseDataCapabilitySnapshot capabilities(TenantId tenantId) {
        requireTenant(tenantId);
        return new EnterpriseDataCapabilitySnapshot(true,
                Set.of(EnterpriseQueryMode.REGISTERED_NAME_SEARCH,
                        EnterpriseQueryMode.EXACT_REGISTERED_IDENTIFIER),
                Set.of(new LegalJurisdiction("CN"), new LegalJurisdiction("GB")),
                Set.of(
                        new RegisteredIdentifierType(
                                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                                RegistryScheme.CN_USCC),
                        new RegisteredIdentifierType(
                                IdentityScope.GLOBAL_LEI_SYSTEM,
                                RegistryScheme.LEI)),
                5);
    }

    @Override
    public List<EnterpriseDataObservation> searchByRegisteredName(
            TenantId tenantId, EnterpriseNameSearchQuery query) {
        requireTenant(tenantId);
        Objects.requireNonNull(query, "query must not be null");
        String needle = query.registeredName().value().toLowerCase(Locale.ROOT);
        return fixtures().stream()
                .filter(observation -> observation.legalJurisdiction()
                        .sameValueAs(query.legalJurisdiction()))
                .filter(observation -> observation.registeredName()
                        .toLowerCase(Locale.ROOT).contains(needle))
                .limit(query.limit())
                .toList();
    }

    @Override
    public Optional<EnterpriseDataObservation> findByRegisteredIdentifier(
            TenantId tenantId, RegisteredIdentifierKey identifier) {
        requireTenant(tenantId);
        Objects.requireNonNull(identifier, "identifier must not be null");
        return fixtures().stream()
                .filter(observation -> observation.primaryIdentifier().key().equals(identifier))
                .findFirst();
    }

    private List<EnterpriseDataObservation> fixtures() {
        RegisteredIdentifier uscc = new RegisteredIdentifier(
                IdentityScope.CN_UNIFIED_SOCIAL_CREDIT_CODE,
                RegistryScheme.CN_USCC,
                SYNTHETIC_USCC,
                IdentifierStanding.UNKNOWN);
        RegisteredIdentifier lei = new RegisteredIdentifier(
                IdentityScope.GLOBAL_LEI_SYSTEM,
                RegistryScheme.LEI,
                SYNTHETIC_LEI,
                IdentifierStanding.CURRENT);
        return List.of(
                new EnterpriseDataObservation(
                        uscc, List.of(uscc), new LegalJurisdiction("CN"),
                        "合成星河技术有限公司", RegisteredEntityStatus.ACTIVE,
                        FormationDateKind.ESTABLISHED, LocalDate.of(2024, 1, 15),
                        new RegisteredAddressObservation(
                                "CN", "合成省", "合成市", "测试大道 1 号", "000000"),
                        EnterpriseDataSourceKind.SYNTHETIC_FIXTURE, false,
                        SYNTHETIC_SOURCE_UPDATED_AT, null),
                new EnterpriseDataObservation(
                        lei, List.of(lei), new LegalJurisdiction("GB"),
                        "Synthetic Registry Labs Ltd", RegisteredEntityStatus.ACTIVE,
                        FormationDateKind.ESTABLISHED, LocalDate.of(2023, 6, 20),
                        new RegisteredAddressObservation(
                                "GB", null, "Fixture City", "1 Contract Test Way", "FX1 1AA"),
                        EnterpriseDataSourceKind.SYNTHETIC_FIXTURE, false,
                        SYNTHETIC_SOURCE_UPDATED_AT, null));
    }

    private void requireTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant must not be null");
        }
    }
}
