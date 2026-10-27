package io.github.archetom.crm.infra.persistence.query;

import io.github.archetom.crm.application.port.out.ContactPoolCatalogQuery;
import io.github.archetom.crm.domain.model.ContactPoolStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactPoolMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolCatalogPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlContactPoolCatalogQueryTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final DepartmentId DEPARTMENT = new DepartmentId(3L);

    @Test
    void usesOneLightweightProjectionQuery() {
        ContactPoolCatalogPO row = new ContactPoolCatalogPO();
        row.setId(11L);
        row.setName("Projected pool");
        row.setStatus(ContactPoolStatus.ACTIVE.name());
        row.setAdministrator(true);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Object[]> invocation = new AtomicReference<>();
        ContactPoolMapper poolMapper = (ContactPoolMapper) Proxy.newProxyInstance(
                ContactPoolMapper.class.getClassLoader(),
                new Class<?>[] {ContactPoolMapper.class},
                (proxy, method, arguments) -> {
                    if (!"selectCatalogEntries".equals(method.getName())) {
                        throw new AssertionError("Unexpected mapper call: " + method.getName());
                    }
                    calls.incrementAndGet();
                    invocation.set(arguments);
                    return List.of(row);
                });
        MySqlContactPoolCatalogQuery query = new MySqlContactPoolCatalogQuery(poolMapper);

        List<ContactPoolCatalogQuery.Entry> result = query.findAccessible(
                TENANT, MEMBER, DEPARTMENT, false);

        assertEquals(1, result.size());
        assertEquals(11L, result.getFirst().id().getValue());
        assertEquals("Projected pool", result.getFirst().name());
        assertEquals(ContactPoolStatus.ACTIVE, result.getFirst().status());
        assertTrue(result.getFirst().administrator());
        assertEquals(1, calls.get());
        assertArrayEquals(new Object[] {99L, 7L, 3L, false, 501}, invocation.get());
    }
}
