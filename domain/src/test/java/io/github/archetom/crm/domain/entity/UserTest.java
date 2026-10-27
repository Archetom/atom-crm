package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.event.UserCreatedEvent;
import io.github.archetom.crm.domain.event.UserStatusChangedEvent;
import io.github.archetom.crm.domain.exception.UserDomainException;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.Username;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("User aggregate")
class UserTest {

    private static final TenantId TENANT_ID = new TenantId(1L);
    private static final PasswordHash PASSWORD_HASH =
            PasswordHash.fromTrustedHash("$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");

    @Test
    void createsTenantOwnedUserFromTrustedHash() {
        User user = newUser();

        assertEquals(TENANT_ID, user.getTenantId());
        assertEquals("testuser", user.getUsernameValue());
        assertEquals("test@example.com", user.getEmailValue());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.getDomainEvents().isEmpty());

        user.onPersisted(new UserId(100L), 0L, user.getCreatedTime(), user.getUpdatedTime());

        UserCreatedEvent event = (UserCreatedEvent) user.getDomainEvents().getFirst();
        assertEquals(100L, event.getUserId());
        assertEquals(1L, event.getTenantId());
        assertEquals("100", event.getAggregateId());
    }

    @Test
    void rejectsMissingCreationValues() {
        assertAll(
                () -> assertThrows(UserDomainException.class, () -> User.createWithPasswordHash(
                        null, new Username("testuser"), new Email("test@example.com"), PASSWORD_HASH, "Test User")),
                () -> assertThrows(UserDomainException.class, () -> User.createWithPasswordHash(
                        TENANT_ID, null, new Email("test@example.com"), PASSWORD_HASH, "Test User")),
                () -> assertThrows(UserDomainException.class, () -> User.createWithPasswordHash(
                        TENANT_ID, new Username("testuser"), null, PASSWORD_HASH, "Test User")),
                () -> assertThrows(UserDomainException.class, () -> User.createWithPasswordHash(
                        TENANT_ID, new Username("testuser"), new Email("test@example.com"), null, "Test User")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PasswordHash.fromTrustedHash(" "))
        );
    }

    @Test
    void createsUserWithE164PhoneNumber() {
        PhoneNumber phoneNumber = new PhoneNumber("+8613800138000");
        User user = User.createWithPasswordHash(
                TENANT_ID,
                new Username("testuser"),
                new Email("test@example.com"),
                phoneNumber,
                PASSWORD_HASH,
                "Test User");

        assertEquals("+8613800138000", user.getPhoneNumberValue());
        assertEquals("+861******8000", user.getMaskedPhoneNumber());
        assertEquals("+861******8000", phoneNumber.toString());
        assertThrows(IllegalArgumentException.class, () -> new PhoneNumber("13800138000"));
    }

    @Test
    void changesStatusAndRaisesEvent() {
        User user = persistedUser();

        user.changeStatus(UserStatus.LOCKED, "security review");

        assertTrue(user.isLocked());
        UserStatusChangedEvent event = (UserStatusChangedEvent) user.getDomainEvents().getFirst();
        assertEquals(UserStatus.ACTIVE, event.getOldStatus());
        assertEquals(UserStatus.LOCKED, event.getNewStatus());
    }

    @Test
    void onlyDeleteOperationCanEnterDeletedState() {
        User user = persistedUser();

        assertThrows(UserDomainException.class,
                () -> user.changeStatus(UserStatus.DELETED, "bypass delete policy"));
        assertFalse(user.isDeleted());

        user.delete();
        assertTrue(user.isDeleted());
        assertThrows(UserDomainException.class,
                () -> user.changeStatus(UserStatus.ACTIVE, "reactivate"));
    }

    @Test
    void changesContactDetails() {
        User user = persistedUser();

        user.changeEmail("new@example.com");
        user.changePhoneNumber("+8613900139000");

        assertEquals("new@example.com", user.getEmailValue());
        assertEquals("+861******9000", user.getMaskedPhoneNumber());
    }

    @Test
    void unchangedPhoneNumberDoesNotChangeAuditTime() {
        User user = persistedUser();
        user.changePhoneNumber("+8613900139000");
        var changedTime = user.getUpdatedTime();
        user.changePhoneNumber("+8613900139000");

        assertEquals(changedTime, user.getUpdatedTime());
    }

    @Test
    void passwordHashIsExplicitlyRedacted() {
        User user = newUser();

        assertEquals("[REDACTED]", user.getPasswordHash().toString());
        assertFalse(user.toString().contains(PASSWORD_HASH.valueForPersistence()));
    }

    private User newUser() {
        return User.createWithPasswordHash(
                TENANT_ID,
                new Username("testuser"),
                new Email("test@example.com"),
                PASSWORD_HASH,
                "Test User");
    }

    private User persistedUser() {
        User user = newUser();
        user.onPersisted(new UserId(100L), 0L, user.getCreatedTime(), user.getUpdatedTime());
        user.clearDomainEvents();
        return user;
    }
}
