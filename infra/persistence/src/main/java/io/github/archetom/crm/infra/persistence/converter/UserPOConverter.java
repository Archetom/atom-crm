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
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.stream.Collectors;

/** Explicit conversion between User aggregates and relational persistence objects. */
@Mapper(componentModel = "spring")
public abstract class UserPOConverter {

    /** Reconstitute a User without raising new domain events. */
    public User toDomain(UserPO userPO) {
        if (userPO == null) {
            return null;
        }

        return User.reconstitute(toSnapshot(userPO));
    }

    @Mapping(target = "id", expression = "java(longToUserId(userPO.getId()))")
    @Mapping(target = "username", expression = "java(stringToUsername(userPO.getUsername()))")
    @Mapping(target = "email", expression = "java(stringToEmail(userPO.getEmail()))")
    @Mapping(target = "phoneNumber", expression = "java(stringToPhoneNumber(userPO.getPhoneNumber()))")
    @Mapping(target = "passwordHash", expression = "java(stringToPasswordHash(userPO.getPasswordHash()))")
    @Mapping(target = "status", expression = "java(codeToStatus(userPO.getStatus()))")
    @Mapping(target = "tenantId", expression = "java(longToTenantId(userPO.getTenantId()))")
    @Mapping(target = "externalUser", expression = "java(Boolean.TRUE.equals(userPO.getExternalUser()))")
    @Mapping(target = "admin", expression = "java(Boolean.TRUE.equals(userPO.getAdmin()))")
    protected abstract User.UserSnapshot toSnapshot(UserPO userPO);

    /**
     * User -> UserPO
     */
    @Mapping(target = "id", expression = "java(userIdToLong(user.getId()))")
    @Mapping(target = "username", expression = "java(usernameToString(user.getUsername()))")
    @Mapping(target = "email", expression = "java(emailToString(user.getEmail()))")
    @Mapping(target = "phoneNumber", expression = "java(phoneNumberToString(user.getPhoneNumber()))")
    @Mapping(target = "status", expression = "java(statusToCode(user.getStatus()))")
    @Mapping(target = "externalUser", source = "externalUser")
    @Mapping(target = "admin", source = "admin")
    @Mapping(target = "externalId", source = "externalId")
    @Mapping(target = "passwordHash", expression = "java(passwordHashToString(user.getPasswordHash()))")
    @Mapping(target = "realName", source = "realName")
    @Mapping(target = "tenantId", expression = "java(tenantIdToLong(user.getTenantId()))")
    @Mapping(target = "createdTime", source = "createdTime")
    @Mapping(target = "updatedTime", source = "updatedTime")
    @Mapping(target = "version", source = "version")
    public abstract UserPO toPO(User user);

    /**
     * UserPO List -> User List
     */
    public List<User> toDomainList(List<UserPO> userPOs) {
        if (userPOs == null) {
            return null;
        }
        return userPOs.stream().map(this::toDomain).collect(Collectors.toList());
    }

    /**
     * User List -> UserPO List
     */
    public abstract List<UserPO> toPOList(List<User> users);

    // ========== Value conversions ==========

    /**
     * Long -> UserId
     */
    protected UserId longToUserId(Long id) {
        return id != null ? new UserId(id) : null;
    }

    /**
     * UserId -> Long
     */
    protected Long userIdToLong(UserId userId) {
        return userId != null ? userId.getValue() : null;
    }

    protected TenantId longToTenantId(Long tenantId) {
        return tenantId != null ? new TenantId(tenantId) : null;
    }

    protected Long tenantIdToLong(TenantId tenantId) {
        return tenantId != null ? tenantId.getValue() : null;
    }

    protected PasswordHash stringToPasswordHash(String passwordHash) {
        return passwordHash != null ? PasswordHash.fromTrustedHash(passwordHash) : null;
    }

    protected String passwordHashToString(PasswordHash passwordHash) {
        return passwordHash != null ? passwordHash.valueForPersistence() : null;
    }

    /**
     * String -> Username
     */
    protected Username stringToUsername(String username) {
        return StringUtils.isNotBlank(username) ? new Username(username) : null;
    }

    /**
     * Username -> String
     */
    protected String usernameToString(Username username) {
        return username != null ? username.getValue() : null;
    }

    /**
     * String -> Email
     */
    protected Email stringToEmail(String email) {
        return StringUtils.isNotBlank(email) ? new Email(email) : null;
    }

    /**
     * Email -> String
     */
    protected String emailToString(Email email) {
        return email != null ? email.getValue() : null;
    }

    /**
     * String -> PhoneNumber
     */
    protected PhoneNumber stringToPhoneNumber(String phoneNumber) {
        return StringUtils.isNotBlank(phoneNumber) ? new PhoneNumber(phoneNumber) : null;
    }

    /**
     * PhoneNumber -> String
     */
    protected String phoneNumberToString(PhoneNumber phoneNumber) {
        return phoneNumber != null ? phoneNumber.getValue() : null;
    }

    /**
     * String -> UserStatus
     */
    protected UserStatus codeToStatus(String code) {
        return code != null ? UserStatus.fromCode(code) : null;
    }

    /**
     * UserStatus -> String
     */
    protected String statusToCode(UserStatus status) {
        return status != null ? status.getCode() : null;
    }
}
