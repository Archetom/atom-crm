package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.model.LeadExportRow;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.LeadId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadExportRootPO;
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

/** Ensures one bounded Lead export batch stays a fixed number of mapper statements. */
class LeadExportRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(9L);

    @Test
    void projectsTwoHundredRowsWithOneRootAndOneCustomValueQuery() {
        LeadMapper mapper = mock(LeadMapper.class);
        LeadRepositoryImpl repository = new LeadRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null);
        List<LeadId> leadIds = LongStream.rangeClosed(1, 200)
                .mapToObj(LeadId::new).toList();
        List<LeadExportRootPO> roots = leadIds.stream().map(leadId -> {
            LeadExportRootPO root = new LeadExportRootPO();
            root.setLeadId(leadId.getValue());
            root.setName("Lead " + leadId.getValue());
            return root;
        }).toList();
        when(mapper.selectExportRoots(eq(TENANT.getValue()), anyList())).thenReturn(roots);
        when(mapper.selectExportValues(eq(TENANT.getValue()), anyList(), anyList()))
                .thenReturn(List.of());

        List<LeadExportRow> rows = repository.findExportRows(TENANT, leadIds, List.of(
                new FieldCode(LeadFieldCatalog.NAME), new FieldCode("export_custom_a"),
                new FieldCode("export_custom_b")));

        assertEquals(200, rows.size());
        List<Long> ids = leadIds.stream().map(LeadId::getValue).toList();
        verify(mapper, times(1)).selectExportRoots(eq(TENANT.getValue()), eq(ids));
        verify(mapper, times(1)).selectExportValues(eq(TENANT.getValue()), eq(ids),
                eq(List.of("export_custom_a", "export_custom_b")));
    }
}
