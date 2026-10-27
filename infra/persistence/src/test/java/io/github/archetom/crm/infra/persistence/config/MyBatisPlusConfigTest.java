package io.github.archetom.crm.infra.persistence.config;

import io.github.archetom.crm.infra.persistence.mysql.po.WorkTaskPO;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MyBatisPlusConfigTest {
    @Test
    void preservesExplicitDatabaseUtcEvidenceWhileFillingMissingAuditTimes() {
        MyBatisPlusConfig.MyMetaObjectHandler handler = new MyBatisPlusConfig.MyMetaObjectHandler();
        LocalDateTime databaseUtc = LocalDateTime.of(2026, 7, 22, 14, 30, 0, 123_000);
        WorkTaskPO explicit = new WorkTaskPO();
        explicit.setCreatedTime(databaseUtc);
        explicit.setUpdatedTime(databaseUtc);

        handler.insertFill(SystemMetaObject.forObject(explicit));
        handler.updateFill(SystemMetaObject.forObject(explicit));

        assertEquals(databaseUtc, explicit.getCreatedTime());
        assertEquals(databaseUtc, explicit.getUpdatedTime());
        WorkTaskPO missing = new WorkTaskPO();
        handler.insertFill(SystemMetaObject.forObject(missing));
        assertNotNull(missing.getCreatedTime());
        assertNotNull(missing.getUpdatedTime());
    }
}
