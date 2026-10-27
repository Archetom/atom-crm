package io.github.archetom.crm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Lifecycle state owned by the user domain model.
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    ACTIVE("ACTIVE", "Active"),
    INACTIVE("INACTIVE", "Inactive"),
    LOCKED("LOCKED", "Locked"),
    DELETED("DELETED", "Deleted");

    private final String code;
    private final String displayName;

    public static UserStatus fromCode(String code) {
        for (UserStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown user status code");
    }
}
