package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.model.MarketingFormSubmission;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MarketingFormSubmissionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketingFormSubmissionPOConverterTest {

    private final MarketingFormSubmissionPOConverter converter =
            new MarketingFormSubmissionPOConverter();

    @Test
    void roundTripsEveryLedgerField() {
        LocalDateTime submittedTime = LocalDateTime.of(
                2026, 7, 18, 8, 30, 0, 123_456_000);
        MarketingFormSubmission source = new MarketingFormSubmission(
                new MarketingFormSubmissionId(1001L), new TenantId(7L),
                new MarketingFormId(11L), 3L, MarketingFormTargetType.LEAD, 2001L, "retry-2001",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", submittedTime);

        MarketingFormSubmission restored = converter.toDomain(converter.toPO(source));

        assertEquals(source, restored);
    }

    @Test
    void roundTripsCustomerAttributionWithoutLeadId() {
        LocalDateTime submittedTime = LocalDateTime.of(
                2026, 8, 12, 4, 15, 0, 123_456_000);
        MarketingFormSubmission source = new MarketingFormSubmission(
                new MarketingFormSubmissionId(1002L), new TenantId(7L),
                new MarketingFormId(12L), 4L, MarketingFormTargetType.CUSTOMER, 3001L, "retry-3001",
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210", submittedTime);

        MarketingFormSubmission restored = converter.toDomain(converter.toPO(source));

        assertEquals(source, restored);
    }
}
