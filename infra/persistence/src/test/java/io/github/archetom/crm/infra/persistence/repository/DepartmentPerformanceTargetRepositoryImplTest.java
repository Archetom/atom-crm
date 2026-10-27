package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.entity.DepartmentPerformanceTarget;
import io.github.archetom.crm.domain.exception.DepartmentPerformanceTargetAlreadyExistsException;
import io.github.archetom.crm.domain.exception.DomainError;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetPeriod;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.DepartmentPerformanceTargetPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.DepartmentPerformanceTargetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepartmentPerformanceTargetRepositoryImplTest {

    @Test
    void translatesTheDepartmentPeriodNaturalKeyRaceToAlreadyExists() {
        DepartmentPerformanceTargetMapper mapper = (DepartmentPerformanceTargetMapper) Proxy.newProxyInstance(
                DepartmentPerformanceTargetMapper.class.getClassLoader(),
                new Class<?>[]{DepartmentPerformanceTargetMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert")) {
                        throw new DuplicateKeyException("Duplicate department performance target",
                                new SQLIntegrityConstraintViolationException());
                    }
                    throw new UnsupportedOperationException("Unexpected mapper call: " + method.getName());
                });
        DepartmentPerformanceTargetRepositoryImpl repository = new DepartmentPerformanceTargetRepositoryImpl(
                mapper, new DepartmentPerformanceTargetPOConverter());
        DepartmentPerformanceTarget target = DepartmentPerformanceTarget.create(
                new TenantId(1L), new DepartmentId(2L), PerformanceTargetMetric.ACTIVE_CONTRACT,
                2026, PerformanceTargetPeriod.QUARTER, 3, new BigDecimal("100.00"));

        DepartmentPerformanceTargetAlreadyExistsException exception = assertThrows(
                DepartmentPerformanceTargetAlreadyExistsException.class,
                () -> repository.save(target.getTenantId(), target));

        assertEquals(DomainError.ALREADY_EXISTS, exception.getError());
    }
}
