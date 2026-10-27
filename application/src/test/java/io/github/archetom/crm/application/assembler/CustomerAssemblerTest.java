package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.application.vo.CustomerVO;
import io.github.archetom.crm.domain.entity.Customer;
import io.github.archetom.crm.domain.entity.CustomerCollaborator;
import io.github.archetom.crm.domain.model.CustomerCollaboratorAccess;
import io.github.archetom.crm.domain.model.CustomerDealStatus;
import io.github.archetom.crm.domain.model.EffectiveFieldAccess;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
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

class CustomerAssemblerTest {

    @Test
    void dealTimeRequiresUnmaskedDealStatusAccess() {
        Customer customer = Customer.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        customer.changeState(false, CustomerDealStatus.WON);
        customer.onPersisted(new CustomerId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());
        FieldCode dealStatus = new FieldCode(CustomerFieldCatalog.DEAL_STATUS);

        CustomerVO masked = CustomerAssembler.toVO(customer, false, Map.of(
                dealStatus, new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.FULL)), Set.of());
        assertEquals("[REDACTED]", masked.dealStatus());
        assertNull(masked.dealTime());

        CustomerVO unmasked = CustomerAssembler.toVO(customer, false, Map.of(
                dealStatus, new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.NONE)), Set.of());
        assertEquals(CustomerDealStatus.WON.name(), unmasked.dealStatus());
        assertNotNull(unmasked.dealTime());
    }

    @Test
    void auditTimesRequireUnmaskedFieldAccess() {
        Customer customer = Customer.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        LocalDateTime createdTime = LocalDateTime.of(2026, 7, 15, 9, 30);
        LocalDateTime updatedTime = createdTime.plusHours(1);
        customer.onPersisted(new CustomerId(21L), 0L, createdTime, updatedTime);
        FieldCode created = new FieldCode(CustomerFieldCatalog.CREATED_TIME);
        FieldCode updated = new FieldCode(CustomerFieldCatalog.UPDATED_TIME);

        CustomerVO masked = CustomerAssembler.toVO(customer, false, Map.of(
                created, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.FULL),
                updated, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.PARTIAL)),
                Set.of());
        assertNull(masked.createdTime());
        assertNull(masked.updatedTime());

        CustomerVO unmasked = CustomerAssembler.toVO(customer, false, Map.of(
                created, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE),
                updated, new EffectiveFieldAccess(FieldAccessLevel.READ, FieldMaskType.NONE)),
                Set.of());
        assertEquals(createdTime, unmasked.createdTime());
        assertEquals(updatedTime, unmasked.updatedTime());
    }

    @Test
    void partialMaskPreservesSupplementaryUnicodeCodePoints() {
        Customer customer = Customer.create(new TenantId(9L), new MemberId(11L),
                "😀Acme🚀", null, null, null, null, null, List.of(), List.of());
        customer.onPersisted(new CustomerId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());

        CustomerVO masked = CustomerAssembler.toVO(customer, false, Map.of(
                new FieldCode(CustomerFieldCatalog.NAME), new EffectiveFieldAccess(
                        FieldAccessLevel.READ, FieldMaskType.PARTIAL)), Set.of());

        assertEquals("😀****🚀", masked.name());
    }

    @Test
    void collaboratorsRequireAnExplicitlyAuthorizedMappingPath() {
        Customer customer = Customer.create(new TenantId(9L), new MemberId(11L),
                "Acme", null, null, null, null, null, List.of(), List.of());
        customer.replaceCollaborators(List.of(new CustomerCollaborator(
                new MemberId(12L), CustomerCollaboratorAccess.WRITE)));
        customer.onPersisted(new CustomerId(21L), 0L,
                LocalDateTime.now(), LocalDateTime.now());

        CustomerVO ordinary = CustomerAssembler.toVO(
                customer, false, Map.of(), Set.of());
        CustomerVO authorized = CustomerAssembler.toVO(
                customer, false, Map.of(), Set.of(), true);

        assertEquals(List.of(), ordinary.collaborators());
        assertEquals(12L, authorized.collaborators().getFirst().actorId());
        assertEquals("WRITE", authorized.collaborators().getFirst().access());
    }
}
