package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.archetom.crm.domain.entity.ReturnVisit;
import io.github.archetom.crm.domain.exception.ReturnVisitDomainException;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.valueobject.ContactId;
import io.github.archetom.crm.domain.valueobject.CustomerId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.converter.ReturnVisitPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReturnVisitMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitPO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Proxy;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnVisitRepositoryImplTest {

    private static final TenantId TENANT = new TenantId(9L);

    @Test
    void optionKeywordTreatsSqlWildcardsAsLiterals() {
        ReturnVisitMapper mapper = mock(ReturnVisitMapper.class);
        java.util.concurrent.atomic.AtomicReference<QueryWrapper<ReturnVisitPO>> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return List.of();
        });
        ReturnVisitRepositoryImpl repository = new ReturnVisitRepositoryImpl(
                mapper, null, null, null, new ReturnVisitPOConverter(null), null, null);

        repository.findOptions(TENANT, new ReturnVisitVisibility(DataScope.TENANT,
                new MemberId(7L), Set.of(), ReturnVisitAccessMode.READ), "%_", 20);

        assertTrue(captured.get().getCustomSqlSegment().contains("ESCAPE '!'"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("!%!_"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "fk_crm_return_visit_contact_customer",
            "fk_crm_return_visit_contract_customer"
    })
    void relationCustomerForeignKeyRaceMapsToDomainError(String constraint) {
        ReturnVisitMapper mapper = mapperThrowing(new DataIntegrityViolationException(
                "return visit insert failed", new SQLIntegrityConstraintViolationException(
                "Cannot add or update a child row: CONSTRAINT `" + constraint + "`")));
        ReturnVisitRepositoryImpl repository = new ReturnVisitRepositoryImpl(
                mapper, null, null, null, new ReturnVisitPOConverter(null), null, null);

        assertThrows(ReturnVisitDomainException.class,
                () -> repository.save(TENANT, returnVisit(), List.of()));
    }

    private ReturnVisit returnVisit() {
        return ReturnVisit.create(TENANT, new MemberId(7L), new CustomerId(5L),
                new ContactId(6L), null, "RV-RACE", LocalDateTime.of(2026, 7, 18, 10, 0),
                null, null, null, List.of());
    }

    private ReturnVisitMapper mapperThrowing(RuntimeException exception) {
        return (ReturnVisitMapper) Proxy.newProxyInstance(
                ReturnVisitMapper.class.getClassLoader(), new Class<?>[]{ReturnVisitMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert") && arguments != null
                            && arguments.length == 1 && arguments[0] instanceof ReturnVisitPO) {
                        throw exception;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });
    }
}
