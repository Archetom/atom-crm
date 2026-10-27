package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.entity.Contract;
import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ContractPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContractMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContractPO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
class ContractRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(9L);

    @ParameterizedTest
    @ValueSource(strings = {
            "fk_crm_contract_opportunity_customer",
            "fk_crm_contract_contact_customer",
            "fk_crm_return_visit_contract_customer"
    })
    void relationCustomerForeignKeyRaceMapsToDomainError(String constraint) {
        ContractMapper mapper = mapperThrowing(new DataIntegrityViolationException(
                "contract insert failed", new SQLIntegrityConstraintViolationException(
                "Cannot add or update a child row: CONSTRAINT `" + constraint + "`")));
        ContractRepositoryImpl repository = new ContractRepositoryImpl(
                mapper, null, null, null, null, null,
                new ContractPOConverter(new ObjectMapper()), null, null);

        assertThrows(ContractDomainException.class,
                () -> repository.save(TENANT, contract(), List.of()));
    }

    private Contract contract() {
        return Contract.create(TENANT, new MemberId(7L), new CustomerId(5L),
                null, null, "Concurrent relation contract", "C-RACE",
                null, null, null, null, null, null, BigDecimal.ZERO,
                List.of(), List.of(), List.of());
    }

    private ContractMapper mapperThrowing(RuntimeException exception) {
        return (ContractMapper) Proxy.newProxyInstance(
                ContractMapper.class.getClassLoader(), new Class<?>[]{ContractMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert") && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof ContractPO) {
                        throw exception;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });
    }
}
