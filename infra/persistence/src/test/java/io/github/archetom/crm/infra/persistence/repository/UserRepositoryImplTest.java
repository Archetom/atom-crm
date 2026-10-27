package io.github.archetom.crm.infra.persistence.repository;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.exception.DomainError;
import io.github.archetom.crm.domain.exception.UserAlreadyExistsException;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.Username;
import io.github.archetom.crm.infra.persistence.converter.UserPOConverter;
import io.github.archetom.crm.infra.persistence.mysql.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Proxy;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserRepositoryImplTest {

    private final UserPOConverter converter = Mappers.getMapper(UserPOConverter.class);

    @Test
    void translatesUsernameUniqueConstraintToDomainConflict() {
        User user = user("alice", "alice@example.com");
        UserRepositoryImpl repository = repositoryRejecting("uk_t_user_tenant_username");

        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> repository.save(user.getTenantId(), user));

        assertEquals(DomainError.ALREADY_EXISTS, exception.getError());
        assertEquals("Username already exists", exception.getMessage());
    }

    @Test
    void translatesEmailUniqueConstraintToDomainConflict() {
        User user = user("alice", "alice@example.com");
        UserRepositoryImpl repository = repositoryRejecting("uk_t_user_tenant_email");

        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> repository.save(user.getTenantId(), user));

        assertEquals(DomainError.ALREADY_EXISTS, exception.getError());
        assertEquals("Email already exists", exception.getMessage());
    }

    private User user(String username, String email) {
        return User.createWithPasswordHash(
                new TenantId(1L),
                new Username(username),
                new Email(email),
                PasswordHash.fromTrustedHash("password-hash"),
                "Test User");
    }

    private DuplicateKeyException duplicateKey(String constraint) {
        return new DuplicateKeyException(
                "Database uniqueness violation",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key '" + constraint + "'"));
    }

    private UserRepositoryImpl repositoryRejecting(String constraint) {
        UserMapper mapper = (UserMapper) Proxy.newProxyInstance(
                UserMapper.class.getClassLoader(),
                new Class<?>[]{UserMapper.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("insert")) {
                        throw duplicateKey(constraint);
                    }
                    throw new UnsupportedOperationException("Unexpected mapper call: " + method.getName());
                });
        return new UserRepositoryImpl(mapper, converter);
    }
}
