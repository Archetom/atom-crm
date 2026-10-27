package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.CustomerExportRow;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerExportRootPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ensures one bounded export batch stays a fixed number of mapper statements. */
class CustomerExportRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(9L);

    @Test
    void projectsTwoHundredRowsWithOneRootAndOneCustomValueQuery() {
        CustomerMapper mapper = mock(CustomerMapper.class);
        CustomerRepositoryImpl repository = new CustomerRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null, null);
        List<CustomerId> customerIds = LongStream.rangeClosed(1, 200)
                .mapToObj(CustomerId::new).toList();
        List<CustomerExportRootPO> roots = customerIds.stream().map(customerId -> {
            CustomerExportRootPO root = new CustomerExportRootPO();
            root.setCustomerId(customerId.getValue());
            root.setName("Customer " + customerId.getValue());
            return root;
        }).toList();
        when(mapper.selectExportRoots(eq(TENANT.getValue()), anyList())).thenReturn(roots);
        when(mapper.selectExportValues(eq(TENANT.getValue()), anyList(), anyList()))
                .thenReturn(List.of());

        List<CustomerExportRow> rows = repository.findExportRows(TENANT, customerIds, List.of(
                new FieldCode(CustomerFieldCatalog.NAME), new FieldCode("export_custom_a"),
                new FieldCode("export_custom_b")));

        assertEquals(200, rows.size());
        verify(mapper, times(1)).selectExportRoots(eq(TENANT.getValue()), eq(customerIds.stream()
                .map(CustomerId::getValue).toList()));
        verify(mapper, times(1)).selectExportValues(eq(TENANT.getValue()), eq(customerIds.stream()
                .map(CustomerId::getValue).toList()), eq(List.of("export_custom_a", "export_custom_b")));
    }
}
