package io.github.archetom.crm.domain.repository;

import io.github.archetom.crm.domain.entity.FieldDefinition;
import io.github.archetom.crm.domain.entity.Invoice;
import io.github.archetom.crm.domain.model.InvoiceQuery;
import io.github.archetom.crm.domain.model.InvoiceCustomerSummary;
import io.github.archetom.crm.domain.model.InvoiceVisibility;
import io.github.archetom.crm.domain.valueobject.ContractId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.InvoiceId;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface InvoiceRepository {
    Invoice save(
            TenantId tenantId, Invoice invoice,
            Collection<FieldDefinition> fieldDefinitions);

    Optional<Invoice> findById(TenantId tenantId, InvoiceId invoiceId);

    /** Internal B24 worker/root lock after a typed attachment relation proved ownership. */
    Optional<Invoice> findByIdForAttachmentMutation(TenantId tenantId, InvoiceId invoiceId);

    Optional<Invoice> findVisibleById(
            TenantId tenantId, InvoiceId invoiceId, InvoiceVisibility visibility);

    Optional<Invoice> findVisibleByIdForUpdate(
            TenantId tenantId, InvoiceId invoiceId, InvoiceVisibility visibility);

    List<Invoice> findVisibleByIds(
            TenantId tenantId, Collection<InvoiceId> invoiceIds,
            InvoiceVisibility visibility, int limit);

    /**
     * Projects only the visible ids under the same predicate.
     *
     * A caller that needs an id set must not pay for the whole aggregate: reusing
     * {@code findVisibleByIds} loads every child collection and then discards it.
     */
    Set<Long> findVisibleIdsIn(
            TenantId tenantId, Collection<InvoiceId> ids,
            InvoiceVisibility visibility, int limit);

    PageResult<Invoice> findInvoices(TenantId tenantId, InvoiceQuery query);

    InvoiceCustomerSummary summarizeByCustomer(
            TenantId tenantId, CustomerId customerId, InvoiceVisibility visibility);

    BigDecimal sumCommittedAmount(
            TenantId tenantId, ContractId contractId, InvoiceId excludingInvoiceId);

    boolean existsByContract(TenantId tenantId, ContractId contractId);
}
