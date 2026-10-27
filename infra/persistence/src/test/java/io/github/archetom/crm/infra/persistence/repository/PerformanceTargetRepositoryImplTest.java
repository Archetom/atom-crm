package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.entity.PerformanceTarget;
import io.github.archetom.crm.domain.exception.DomainError;
import io.github.archetom.crm.domain.exception.PerformanceTargetAlreadyExistsException;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.PerformanceTargetPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.PerformanceTargetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceTargetRepositoryImplTest {

    @Test
    void translatesTheMonthlyNaturalKeyRaceToAlreadyExists() {
        PerformanceTargetMapper mapper = (PerformanceTargetMapper) Proxy.newProxyInstance(
                PerformanceTargetMapper.class.getClassLoader(),
                new Class<?>[]{PerformanceTargetMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert")) {
                        throw new DuplicateKeyException("Duplicate performance target",
                                new SQLIntegrityConstraintViolationException());
                    }
                    throw new UnsupportedOperationException(
                            "Unexpected mapper call: " + method.getName());
                });
        PerformanceTargetRepositoryImpl repository = new PerformanceTargetRepositoryImpl(
                mapper, new PerformanceTargetPOConverter());
        PerformanceTarget target = PerformanceTarget.create(
                new TenantId(1L), new MemberId(2L), PerformanceTargetMetric.ACTIVE_CONTRACT,
                2026, 8, new BigDecimal("100.00"));

        PerformanceTargetAlreadyExistsException exception = assertThrows(
                PerformanceTargetAlreadyExistsException.class,
                () -> repository.save(target.getTenantId(), target));

        assertEquals(DomainError.ALREADY_EXISTS, exception.getError());
    }
}
