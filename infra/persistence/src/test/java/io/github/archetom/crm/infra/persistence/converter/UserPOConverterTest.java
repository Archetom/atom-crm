package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.Username;
import io.github.archetom.crm.infra.persistence.mysql.po.UserPO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPOConverterTest {

    private final UserPOConverter converter = Mappers.getMapper(UserPOConverter.class);

    @Test
    void roundTripsAllPersistenceFieldsWithoutRaisingEvents() {
        LocalDateTime created = LocalDateTime.now().minusDays(1);
        LocalDateTime updated = LocalDateTime.now();
        User source = User.reconstitute(new User.UserSnapshot(
                new UserId(10L),
                new Username("alice"),
                new Email("alice@example.com"),
                new PhoneNumber("+8613800138000"),
                PasswordHash.fromTrustedHash("password-hash"),
                "Alice",
                UserStatus.ACTIVE,
                new TenantId(2L),
                "EXT-10",
                true,
                true,
                7L,
                created,
                updated));

        UserPO po = converter.toPO(source);
        User restored = converter.toDomain(po);

        assertAll(
                () -> assertEquals(10L, po.getId()),
                () -> assertEquals(2L, po.getTenantId()),
                () -> assertEquals("+8613800138000", po.getPhoneNumber()),
                () -> assertEquals("password-hash", po.getPasswordHash()),
                () -> assertEquals("Alice", po.getRealName()),
                () -> assertFalse(po.toString().contains("password-hash")),
                () -> assertEquals(
                        new UserPO().setPasswordHash("first"),
                        new UserPO().setPasswordHash("second")),
                () -> assertEquals("EXT-10", po.getExternalId()),
                () -> assertTrue(po.getExternalUser()),
                () -> assertTrue(po.getAdmin()),
                () -> assertEquals(7L, po.getVersion()),
                () -> assertEquals(7L, restored.getVersion()),
                () -> assertEquals("alice", restored.getUsernameValue()),
                () -> assertEquals("alice@example.com", restored.getEmailValue()),
                () -> assertEquals("+8613800138000", restored.getPhoneNumberValue()),
                () -> assertEquals("Alice", restored.getRealName()),
                () -> assertEquals(UserStatus.ACTIVE, restored.getStatus()),
                () -> assertEquals("EXT-10", restored.getExternalId()),
                () -> assertTrue(restored.isExternalUser()),
                () -> assertTrue(restored.isAdmin()),
                () -> assertTrue(restored.getPasswordHash().sameValueAs(
                        PasswordHash.fromTrustedHash("password-hash"))),
                () -> assertEquals(new TenantId(2L), restored.getTenantId()),
                () -> assertEquals(created, restored.getCreatedTime()),
                () -> assertEquals(updated, restored.getUpdatedTime()),
                () -> assertFalse(restored.hasDomainEvents())
        );
    }
}
