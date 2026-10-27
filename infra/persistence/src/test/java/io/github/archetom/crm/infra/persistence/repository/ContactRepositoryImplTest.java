package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.entity.Contact;
import io.github.archetom.crm.domain.exception.ContactDomainException;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ContactPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Proxy;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactRepositoryImplTest {

    @Test
    void returnVisitCustomerForeignKeyRaceMapsToDomainError() {
        ContactMapper mapper = mapperThrowing(new DataIntegrityViolationException(
                "contact update failed", new SQLIntegrityConstraintViolationException(
                "Cannot add or update a child row: CONSTRAINT `fk_crm_return_visit_contact_customer`")));
        ContactRepositoryImpl repository = new ContactRepositoryImpl(mapper, null, null, null,
                null, null, null, new ContactPOConverter(new ObjectMapper()), null, null);

        assertThrows(ContactDomainException.class,
                () -> repository.save(new TenantId(9L), contact(), List.of()));
    }

    private Contact contact() {
        return Contact.create(new TenantId(9L), new MemberId(7L), "Concurrent contact", null,
                null, null, "contact@example.test", null, null, null, List.of(), List.of());
    }

    private ContactMapper mapperThrowing(RuntimeException exception) {
        return (ContactMapper) Proxy.newProxyInstance(
                ContactMapper.class.getClassLoader(), new Class<?>[]{ContactMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert") && arguments != null
                            && arguments.length == 1 && arguments[0] instanceof ContactPO) {
                        throw exception;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });
    }
}
