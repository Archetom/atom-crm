package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.ContactExportRow;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactExportRootPO;
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

/** Ensures one bounded Contact export batch stays a fixed number of mapper statements. */
class ContactExportRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(9L);

    @Test
    void projectsTwoHundredRowsWithOneRootAndOneCustomValueQuery() {
        ContactMapper mapper = mock(ContactMapper.class);
        ContactRepositoryImpl repository = new ContactRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null);
        List<ContactId> contactIds = LongStream.rangeClosed(1, 200)
                .mapToObj(ContactId::new).toList();
        List<ContactExportRootPO> roots = contactIds.stream().map(contactId -> {
            ContactExportRootPO root = new ContactExportRootPO();
            root.setContactId(contactId.getValue());
            root.setName("Contact " + contactId.getValue());
            return root;
        }).toList();
        when(mapper.selectExportRoots(eq(TENANT.getValue()), anyList())).thenReturn(roots);
        when(mapper.selectExportValues(eq(TENANT.getValue()), anyList(), anyList()))
                .thenReturn(List.of());

        List<ContactExportRow> rows = repository.findExportRows(TENANT, contactIds, List.of(
                new FieldCode(ContactFieldCatalog.NAME), new FieldCode("export_custom_a"),
                new FieldCode("export_custom_b")));

        assertEquals(200, rows.size());
        List<Long> ids = contactIds.stream().map(ContactId::getValue).toList();
        verify(mapper, times(1)).selectExportRoots(eq(TENANT.getValue()), eq(ids));
        verify(mapper, times(1)).selectExportValues(eq(TENANT.getValue()), eq(ids),
                eq(List.of("export_custom_a", "export_custom_b")));
    }
}
