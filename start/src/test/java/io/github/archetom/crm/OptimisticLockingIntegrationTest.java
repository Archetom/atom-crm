package io.github.archetom.crm;

import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductPO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Concurrent writers cannot silently overwrite each other.
 *
 * <p>Every aggregate in this system relies on optimistic locking to refuse a lost update, and the
 * whole mechanism is two lines that are easy to lose: {@code BasePO.version} carrying
 * {@code @Version}, and {@code OptimisticLockerInnerInterceptor} being registered in
 * {@code MyBatisPlusConfig}. Delete either one and every write still compiles, every existing test
 * still passes, and the guarantee is simply gone — the second writer's update starts succeeding and
 * the first writer's change disappears.</p>
 *
 * <p>This exercises the real interceptor against the real schema, so it fails if the annotation,
 * the interceptor, or the {@code version} column stops being wired together.</p>
 */
@RequiresDatabase
class OptimisticLockingIntegrationTest extends BaseIntegrationTest {

    private static final long TENANT_ID = 999999L;
    private static final long OWNER_ACTOR_ID = 888888L;

    @Autowired
    private ProductMapper productMapper;

    @Test
    void aStaleWriteIsRefusedInsteadOfOverwritingTheCommittedValue() {
        Long id = insertProduct("OPTLOCK-STALE");

        // Two callers that both read the row before either of them wrote.
        ProductPO firstReader = productMapper.selectById(id);
        ProductPO secondReader = productMapper.selectById(id);
        assertEquals(0L, firstReader.getVersion());
        assertEquals(0L, secondReader.getVersion());

        firstReader.setName("Written by the first caller");
        assertEquals(1, productMapper.updateById(firstReader));

        secondReader.setName("Written by the second caller");
        assertEquals(0, productMapper.updateById(secondReader),
                "a write from a stale read must affect no rows");

        ProductPO stored = productMapper.selectById(id);
        assertEquals("Written by the first caller", stored.getName());
        assertEquals(1L, stored.getVersion());
    }

    @Test
    void eachSuccessfulWriteAdvancesTheVersion() {
        Long id = insertProduct("OPTLOCK-ADVANCE");

        for (long expected = 1L; expected <= 3L; expected++) {
            ProductPO current = productMapper.selectById(id);
            current.setName("Revision " + expected);
            assertEquals(1, productMapper.updateById(current));
            assertEquals(expected, productMapper.selectById(id).getVersion(),
                    "the version must advance on every committed write");
        }
    }

    /**
     * A caller that never read the row cannot guess its way past the check. This is the shape a
     * replayed or forged request takes, and it must be refused rather than applied.
     */
    @Test
    void aWriteCarryingTheWrongVersionIsRefused() {
        Long id = insertProduct("OPTLOCK-FORGED");
        ProductPO committed = productMapper.selectById(id);
        committed.setName("Committed value");
        assertEquals(1, productMapper.updateById(committed));

        ProductPO forged = productMapper.selectById(id);
        forged.setVersion(99L);
        forged.setName("Forged value");
        assertEquals(0, productMapper.updateById(forged));
        assertEquals("Committed value", productMapper.selectById(id).getName());
    }

    private Long insertProduct(String code) {
        // crm_product's owner is a foreign key into the membership table.
        executeSql("INSERT IGNORE INTO organization_membership (tenant_id, actor_id, status) "
                + "VALUES (" + TENANT_ID + ", " + OWNER_ACTOR_ID + ", 'ACTIVE')");
        ProductPO product = new ProductPO();
        product.setTenantId(TENANT_ID);
        product.setOwnerActorId(OWNER_ACTOR_ID);
        product.setName("Optimistic locking product");
        product.setCode(code);
        product.setCodeKey(code);
        product.setListPrice(new BigDecimal("10.00"));
        product.setStatus("ACTIVE");
        product.setAvailability("AVAILABLE");
        productMapper.insert(product);
        assertNotNull(product.getId());
        return product.getId();
    }
}
