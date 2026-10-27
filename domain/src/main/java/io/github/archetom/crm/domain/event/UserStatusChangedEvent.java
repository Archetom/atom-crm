package io.github.archetom.crm.domain.event;

import io.github.archetom.crm.domain.model.UserStatus;
import lombok.Getter;

import java.util.Objects;

/**
 * user status event
 * @author hanfeng
 */
@Getter
public class UserStatusChangedEvent extends BaseDomainEvent {

    private final Long userId;
    private final Long tenantId;
    private final UserStatus oldStatus;
    private final UserStatus newStatus;
    private final String reason;

    public UserStatusChangedEvent(Long userId, Long tenantId, UserStatus oldStatus,
                                  UserStatus newStatus, String reason) {
        super();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.oldStatus = Objects.requireNonNull(oldStatus, "oldStatus");
        this.newStatus = Objects.requireNonNull(newStatus, "newStatus");
        this.reason = reason;
    }

    @Override
    public String getAggregateId() {
        return userId.toString();
    }
}
