package io.github.archetom.crm.domain.event;

import lombok.Getter;

import java.util.Objects;

/** Domain fact raised after a new user receives its persisted identity. */
@Getter
public class UserCreatedEvent extends BaseDomainEvent {

    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final String email;

    public UserCreatedEvent(Long userId, Long tenantId, String username, String email) {
        super();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
