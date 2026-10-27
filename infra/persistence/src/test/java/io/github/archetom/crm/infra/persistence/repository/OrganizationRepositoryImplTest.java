package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.entity.Department;
import io.github.archetom.crm.domain.entity.MembershipRequest;
import io.github.archetom.crm.domain.exception.DomainError;
import io.github.archetom.crm.domain.exception.OrganizationAlreadyExistsException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.DepartmentPOConverter;
import io.github.archetom.crm.infra.persistence.converter.MembershipRequestPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.DepartmentMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.MembershipRequestMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationRepositoryImplTest {

    @Test
    void translatesDepartmentUniquenessConflictToTheStableOrganizationError() {
        DepartmentMapper mapper = (DepartmentMapper) Proxy.newProxyInstance(
                DepartmentMapper.class.getClassLoader(),
                new Class<?>[]{DepartmentMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert")) {
                        throw new DuplicateKeyException("Duplicate department", new SQLIntegrityConstraintViolationException());
                    }
                    throw new UnsupportedOperationException("Unexpected mapper call: " + method.getName());
                });
        DepartmentRepositoryImpl repository = new DepartmentRepositoryImpl(mapper, new DepartmentPOConverter());

        OrganizationAlreadyExistsException exception = assertThrows(OrganizationAlreadyExistsException.class,
                () -> repository.save(new TenantId(1L), Department.create(new TenantId(1L), null, "Sales")));

        assertEquals(DomainError.ALREADY_EXISTS, exception.getError());
        assertEquals("Organization resource already exists", exception.getMessage());
    }

    @Test
    void translatesThePendingRequestUniqueKeyToTheStableOrganizationError() {
        MembershipRequestMapper mapper = (MembershipRequestMapper) Proxy.newProxyInstance(
                MembershipRequestMapper.class.getClassLoader(),
                new Class<?>[]{MembershipRequestMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert")) {
                        throw new DuplicateKeyException("Duplicate pending request", new SQLIntegrityConstraintViolationException());
                    }
                    throw new UnsupportedOperationException("Unexpected mapper call: " + method.getName());
                });
        MembershipRequestRepositoryImpl repository = new MembershipRequestRepositoryImpl(
                mapper, new MembershipRequestPOConverter());

        OrganizationAlreadyExistsException exception = assertThrows(OrganizationAlreadyExistsException.class,
                () -> repository.save(MembershipRequest.submit(
                        new MemberId(2L), new TenantId(1L), null, "Join")));

        assertEquals(DomainError.ALREADY_EXISTS, exception.getError());
    }
}
