package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.model.BusinessObjectType;
import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FilterOperator;
import io.github.archetom.crm.domain.model.OpportunityCollaboratorAccess;
import io.github.archetom.crm.domain.model.OpportunityOutcome;
import io.github.archetom.crm.domain.model.OpportunityStatus;
import io.github.archetom.crm.domain.policy.OpportunityFieldValuePolicy;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpportunityModelTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId OWNER = new MemberId(11L);
    private static final CustomerId CUSTOMER = new CustomerId(21L);

    @Test
    void createsBoundedOpportunityWithPrimaryContact() {
        ContactId primary = new ContactId(31L);
        Opportunity opportunity = Opportunity.create(TENANT, OWNER, CUSTOMER,
                "  Renewal  ", "direct", "qualification",
                LocalDate.of(2027, 1, 2), new BigDecimal("120.00"),
                null, "  Annual renewal  ", List.of(primary), primary,
                List.of(new OpportunityCollaborator(new MemberId(12L),
                        OpportunityCollaboratorAccess.WRITE)), List.of());

        assertEquals("Renewal", opportunity.getName());
        assertEquals(new BigDecimal("120.00"), opportunity.getEstimatedAmount());
        assertEquals(primary, opportunity.getPrimaryContactId());
        assertEquals(OpportunityOutcome.OPEN, opportunity.getOutcome());
        assertTrue(opportunity.isOpen());
    }

    @Test
    void rejectsPrimaryContactOutsideRelationship() {
        assertThrows(OpportunityDomainException.class, () -> Opportunity.create(
                TENANT, OWNER, CUSTOMER, "Renewal", "direct", "qualification",
                null, null, null, null, List.of(new ContactId(31L)),
                new ContactId(32L), List.of(), List.of()));
    }

    @Test
    void closedOpportunityMustBeReopenedBeforeEditing() {
        Opportunity opportunity = opportunity();
        opportunity.changeOutcome(OpportunityOutcome.WON, "Signed");

        assertThrows(OpportunityDomainException.class,
                () -> opportunity.changeStage("direct", "contract"));

        opportunity.changeOutcome(OpportunityOutcome.OPEN, null);
        opportunity.changeStage("direct", "negotiation");

        assertEquals(OpportunityOutcome.OPEN, opportunity.getOutcome());
        assertNull(opportunity.getOutcomeTime());
        assertNull(opportunity.getOutcomeRemark());
        assertEquals("negotiation", opportunity.getStageCode());
    }

    @Test
    void changingOwnerRemovesDuplicateCollaborator() {
        MemberId nextOwner = new MemberId(12L);
        Opportunity opportunity = Opportunity.create(TENANT, OWNER, CUSTOMER,
                "Renewal", "direct", "qualification", null, null, null, null,
                List.of(), null, List.of(new OpportunityCollaborator(nextOwner,
                        OpportunityCollaboratorAccess.READ)), List.of());

        opportunity.changeOwner(nextOwner);

        assertEquals(nextOwner, opportunity.getOwnerMemberId());
        assertTrue(opportunity.getCollaborators().isEmpty());
    }

    @Test
    void collaboratorCanExitWithoutAssignmentAuthority() {
        MemberId collaborator = new MemberId(12L);
        Opportunity opportunity = Opportunity.create(TENANT, OWNER, CUSTOMER,
                "Renewal", "direct", "qualification", null, null, null, null,
                List.of(), null, List.of(new OpportunityCollaborator(collaborator,
                        OpportunityCollaboratorAccess.READ)), List.of());

        assertTrue(opportunity.exitCollaborator(collaborator));
        assertTrue(opportunity.getCollaborators().isEmpty());
        assertThrows(OpportunityDomainException.class,
                () -> opportunity.exitCollaborator(OWNER));
    }

    @Test
    void reconstructionRejectsOpenOutcomeWithClosingMetadata() {
        assertThrows(OpportunityDomainException.class, () -> Opportunity.reconstitute(
                new Opportunity.Snapshot(new OpportunityId(1L), TENANT, OWNER, CUSTOMER,
                        null, "Renewal", "direct", "qualification", null,
                        BigDecimal.ZERO, null, null, OpportunityOutcome.OPEN,
                        LocalDateTime.now(), null, OpportunityStatus.ACTIVE,
                        List.of(), List.of(), List.of(), List.of(), 0L,
                        LocalDateTime.now(), LocalDateTime.now())));
    }

    @Test
    void customUrlRejectsAsciiExpansionBeyondPersistenceBound() {
        FieldDefinition field = FieldDefinition.createCustom(
                TENANT, BusinessObjectType.OPPORTUNITY,
                new FieldCode("cf_site"),
                "Site", null, CustomFieldType.URL,
                false, false, true, 1, null, List.of());

        assertThrows(OpportunityDomainException.class,
                () -> OpportunityFieldValuePolicy.normalize(field,
                        List.of("https://example.invalid/" + "测".repeat(100))));
    }

    @Test
    void savedViewDecimalValuesAreValidatedAndCanonicalized() {
        FieldDefinition amount = OpportunityFieldCatalog.definitions(TENANT).stream()
                .filter(field -> OpportunityFieldCatalog.ESTIMATED_AMOUNT.equals(
                        field.getCode().getValue()))
                .findFirst().orElseThrow();
        FilterCondition valid = OpportunityFieldValuePolicy.normalizeFilterCondition(
                amount, new FilterCondition(amount.getCode(), FilterOperator.GT,
                        List.of("001.500")));
        FilterCondition pointRange = OpportunityFieldValuePolicy.normalizeFilterCondition(
                amount, new FilterCondition(amount.getCode(), FilterOperator.BETWEEN,
                        List.of("1.0", "1")));

        assertEquals(List.of("1.5"), valid.values());
        assertEquals(List.of("1", "1"), pointRange.values());
        assertThrows(OpportunityDomainException.class,
                () -> OpportunityFieldValuePolicy.normalizeFilterCondition(
                        amount, new FilterCondition(amount.getCode(), FilterOperator.GT,
                                List.of("abc"))));
    }

    @Test
    void savedViewDecimalRejectsUnsafeExpansionAndSqlOverflow() {
        FieldDefinition amount = builtinField(OpportunityFieldCatalog.ESTIMATED_AMOUNT);

        assertEquals(List.of("10000000000000000000"),
                condition(amount, FilterOperator.GT, "1e+19").values());
        assertEquals(List.of("99999999999999999999.9999999999"),
                condition(amount, FilterOperator.GT,
                        "99999999999999999999.9999999999").values());
        assertThrows(OpportunityDomainException.class,
                () -> condition(amount, FilterOperator.GT, "1e+20"));
        assertThrows(OpportunityDomainException.class,
                () -> condition(amount, FilterOperator.GT, "1e+1000000"));
        assertThrows(OpportunityDomainException.class,
                () -> condition(amount, FilterOperator.GT,
                        "100000000000000000000"));
        assertThrows(OpportunityDomainException.class,
                () -> condition(amount, FilterOperator.GT, "0.00000000001"));
    }

    @Test
    void opportunityAndSavedViewRejectDatesOutsidePersistenceRange() {
        LocalDate unsupportedDate = LocalDate.of(10_000, 1, 1);
        LocalDateTime unsupportedDateTime = unsupportedDate.atStartOfDay();

        assertThrows(OpportunityDomainException.class, () -> Opportunity.create(
                TENANT, OWNER, CUSTOMER, "Renewal", "direct", "qualification",
                unsupportedDate, BigDecimal.ZERO, null, null,
                List.of(), null, List.of(), List.of()));

        Opportunity opportunity = opportunity();
        assertThrows(OpportunityDomainException.class, () -> opportunity.replaceDetails(
                "Renewal", "direct", "qualification", null, BigDecimal.ZERO,
                unsupportedDateTime, null, List.of()));

        FieldDefinition date = builtinField(OpportunityFieldCatalog.EXPECTED_CLOSE_DATE);
        FieldDefinition dateTime = builtinField(OpportunityFieldCatalog.NEXT_CONTACT_TIME);
        assertEquals(List.of("1000-01-01"),
                condition(date, FilterOperator.GTE, "1000-01-01").values());
        assertEquals(List.of("9999-12-31T23:59:59"),
                condition(dateTime, FilterOperator.LTE,
                        "9999-12-31T23:59:59").values());
        assertThrows(OpportunityDomainException.class,
                () -> condition(date, FilterOperator.LT, "0999-12-31"));
        assertThrows(OpportunityDomainException.class,
                () -> condition(date, FilterOperator.GT, "+10000-01-01"));
        assertThrows(OpportunityDomainException.class,
                () -> condition(dateTime, FilterOperator.LT,
                        "0999-12-31T23:59:59"));
        assertThrows(OpportunityDomainException.class,
                () -> condition(dateTime, FilterOperator.GT,
                        "+10000-01-01T00:00:00"));
    }

    @Test
    void opportunityAndSavedViewNormalizeDateTimesToMicroseconds() {
        LocalDateTime input = LocalDateTime.of(
                2027, 2, 3, 4, 5, 6, 123_456_789);
        LocalDateTime normalized = input.withNano(123_456_000);
        Opportunity opportunity = Opportunity.create(
                TENANT, OWNER, CUSTOMER, "Renewal", "direct", "qualification",
                null, BigDecimal.ZERO, input, null,
                List.of(), null, List.of(), List.of());

        assertEquals(normalized, opportunity.getNextContactTime());

        FieldDefinition dateTime = builtinField(OpportunityFieldCatalog.NEXT_CONTACT_TIME);
        assertEquals(List.of("2027-02-03T04:05:06.123456"),
                condition(dateTime, FilterOperator.EQ,
                        "2027-02-03T04:05:06.123456789").values());
        assertEquals(List.of("9999-12-31T23:59:59.999999"),
                condition(dateTime, FilterOperator.LTE,
                        "9999-12-31T23:59:59.999999999").values());

        opportunity.changeOutcome(OpportunityOutcome.WON, null);
        assertEquals(0, opportunity.getOutcomeTime().getNano() % 1_000);
        assertEquals(0, opportunity.getUpdatedTime().getNano() % 1_000);
    }

    @Test
    void savedViewContainsAcceptsTypedValueFragments() {
        FieldDefinition email = filterField("cf_email", CustomFieldType.EMAIL);
        FieldDefinition phone = filterField("cf_phone", CustomFieldType.PHONE);
        FieldDefinition url = filterField("cf_url", CustomFieldType.URL);

        assertEquals(List.of("example.com"), contains(email, "Example.COM").values());
        assertEquals(List.of("555"), contains(phone, "555").values());
        assertEquals(List.of("example.com/path"),
                contains(url, "example.com/path").values());
    }

    private FilterCondition contains(FieldDefinition field, String value) {
        return condition(field, FilterOperator.CONTAINS, value);
    }

    private FilterCondition condition(
            FieldDefinition field, FilterOperator operator, String value) {
        return OpportunityFieldValuePolicy.normalizeFilterCondition(field,
                new FilterCondition(field.getCode(), operator, List.of(value)));
    }

    private FieldDefinition builtinField(String code) {
        return OpportunityFieldCatalog.definitions(TENANT).stream()
                .filter(field -> code.equals(field.getCode().getValue()))
                .findFirst().orElseThrow();
    }

    private FieldDefinition filterField(String code, CustomFieldType type) {
        return FieldDefinition.createCustom(TENANT, BusinessObjectType.OPPORTUNITY,
                new FieldCode(code), code, null, type,
                false, false, true, 1, null, List.of());
    }

    private Opportunity opportunity() {
        return Opportunity.create(TENANT, OWNER, CUSTOMER,
                "Renewal", "direct", "qualification", null,
                BigDecimal.ZERO, null, null, List.of(), null, List.of(), List.of());
    }
}
