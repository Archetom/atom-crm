package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.LeadVO;
import io.github.archetom.crm.domain.entity.Lead;
import io.github.archetom.crm.domain.entity.LeadCollaborator;
import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.model.LeadCollaboratorAccess;
import io.github.archetom.crm.domain.model.LeadDealStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LeadAssemblerTest {

    @Test
    void dealTimeRequiresUnmaskedDealStatusAccess() {
        Lead lead = Lead.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        lead.changeState(false, LeadDealStatus.WON);
        lead.onPersisted(new LeadId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());
        FieldCode dealStatus = new FieldCode(LeadFieldCatalog.DEAL_STATUS);

        LeadVO masked = LeadAssembler.toVO(lead, false, Map.of(
                dealStatus, new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.FULL)), Set.of());
        assertEquals("[REDACTED]", masked.dealStatus());
        assertNull(masked.dealTime());

        LeadVO unmasked = LeadAssembler.toVO(lead, false, Map.of(
                dealStatus, new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.NONE)), Set.of());
        assertEquals(LeadDealStatus.WON.name(), unmasked.dealStatus());
        assertNotNull(unmasked.dealTime());
    }

    @Test
    void auditTimesRequireUnmaskedFieldAccess() {
        Lead lead = Lead.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        LocalDateTime createdTime = LocalDateTime.of(2026, 7, 15, 9, 30);
        LocalDateTime updatedTime = createdTime.plusHours(1);
        lead.onPersisted(new LeadId(21L), 0L, createdTime, updatedTime);
        FieldCode created = new FieldCode(LeadFieldCatalog.CREATED_TIME);
        FieldCode updated = new FieldCode(LeadFieldCatalog.UPDATED_TIME);

        LeadVO masked = LeadAssembler.toVO(lead, false, Map.of(
                created, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.FULL),
                updated, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.PARTIAL)),
                Set.of());
        assertNull(masked.createdTime());
        assertNull(masked.updatedTime());

        LeadVO unmasked = LeadAssembler.toVO(lead, false, Map.of(
                created, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE),
                updated, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE)),
                Set.of());
        assertEquals(createdTime, unmasked.createdTime());
        assertEquals(updatedTime, unmasked.updatedTime());
    }

    @Test
    void partialMaskPreservesSupplementaryUnicodeCodePoints() {
        Lead lead = Lead.create(new TenantId(9L), new MemberId(11L),
                "😀Acme🚀", null, null, null, null, null, List.of(), List.of());
        lead.onPersisted(new LeadId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());

        LeadVO masked = LeadAssembler.toVO(lead, false, Map.of(
                new FieldCode(LeadFieldCatalog.NAME), new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.PARTIAL)), Set.of());

        assertEquals("😀****🚀", masked.name());
    }

    @Test
    void collaboratorsRequireAnExplicitlyAuthorizedMappingPath() {
        Lead lead = Lead.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        lead.replaceCollaborators(List.of(new LeadCollaborator(
                new MemberId(12L), LeadCollaboratorAccess.WRITE)));
        lead.onPersisted(new LeadId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());

        LeadVO ordinary = LeadAssembler.toVO(
                lead, false, Map.of(), Set.of());
        LeadVO authorized = LeadAssembler.toVO(
                lead, false, Map.of(), Set.of(), true);

        assertEquals(List.of(), ordinary.collaborators());
        assertEquals(12L, authorized.collaborators().getFirst().actorId());
        assertEquals("WRITE", authorized.collaborators().getFirst().access());
    }

    @Test
    void convertedContactRequiresVisibleContactRelation() {
        TenantId tenantId = new TenantId(9L);
        MemberId ownerId = new MemberId(11L);
        Lead lead = Lead.create(tenantId, ownerId,
                "Acme", null, null, null, null, null, List.of(), List.of());
        lead.onPersisted(new LeadId(21L), 0L, LocalDateTime.now(), LocalDateTime.now());
        Customer customer = Customer.create(tenantId, ownerId,
                "Acme", null, null, null, null, null, List.of(), List.of());
        customer.onPersisted(new CustomerId(31L), 0L, LocalDateTime.now(), LocalDateTime.now());
        Contact contact = Contact.create(tenantId, ownerId,
                "Decision maker", null, null, null, null, null, List.of(), List.of());
        contact.onPersisted(new ContactId(41L), 0L, LocalDateTime.now(), LocalDateTime.now());
        lead.convert(customer.getId(), contact.getId());

        LeadVO hidden = LeadAssembler.toVO(lead, false, Map.of(), Set.of(), Set.of(), Set.of(), false);
        LeadVO visible = LeadAssembler.toVO(lead, false, Map.of(), Set.of(), Set.of(),
                Set.of(contact.getId()), false);

        assertNull(hidden.convertedContactId());
        assertEquals(contact.getId().getValue(), visible.convertedContactId());
    }
}
