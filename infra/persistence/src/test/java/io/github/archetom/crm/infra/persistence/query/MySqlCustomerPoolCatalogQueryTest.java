package io.github.archetom.crm.infra.persistence.query;

import io.github.archetom.crm.application.port.out.CustomerPoolCatalogQuery;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerPoolMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolCatalogPO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlCustomerPoolCatalogQueryTest {

    private static final TenantId TENANT = new TenantId(99L);
    private static final MemberId MEMBER = new MemberId(7L);
    private static final DepartmentId DEPARTMENT = new DepartmentId(3L);

    @Test
    void usesOneLightweightProjectionQuery() {
        CustomerPoolCatalogPO row = new CustomerPoolCatalogPO();
        row.setId(11L);
        row.setName("Projected pool");
        row.setStatus(CustomerPoolStatus.ACTIVE.name());
        row.setAdministrator(true);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Object[]> invocation = new AtomicReference<>();
        CustomerPoolMapper poolMapper = (CustomerPoolMapper) Proxy.newProxyInstance(
                CustomerPoolMapper.class.getClassLoader(),
                new Class<?>[] {CustomerPoolMapper.class},
                (proxy, method, arguments) -> {
                    if (!"selectCatalogEntries".equals(method.getName())) {
                        throw new AssertionError("Unexpected mapper call: " + method.getName());
                    }
                    calls.incrementAndGet();
                    invocation.set(arguments);
                    return List.of(row);
                });
        MySqlCustomerPoolCatalogQuery query = new MySqlCustomerPoolCatalogQuery(poolMapper);

        List<CustomerPoolCatalogQuery.Entry> result = query.findAccessible(
                TENANT, MEMBER, DEPARTMENT, false);

        assertEquals(1, result.size());
        assertEquals(11L, result.getFirst().id().getValue());
        assertEquals("Projected pool", result.getFirst().name());
        assertEquals(CustomerPoolStatus.ACTIVE, result.getFirst().status());
        assertTrue(result.getFirst().administrator());
        assertEquals(1, calls.get());
        assertArrayEquals(new Object[] {99L, 7L, 3L, false, 501}, invocation.get());
    }
}
