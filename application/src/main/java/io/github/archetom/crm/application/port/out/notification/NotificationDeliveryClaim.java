package io.github.archetom.crm.application.port.out.notification;

import java.util.Optional;

/** One bounded claim plus terminal recoveries observed before the next claim candidate. */
public record NotificationDeliveryClaim(Optional<NotificationDelivery> delivery, int deadCount, int ambiguousCount) {
    public NotificationDeliveryClaim {
        delivery = delivery == null ? Optional.empty() : delivery;
        if (deadCount < 0 || ambiguousCount < 0) throw new IllegalArgumentException("notification delivery claim is invalid");
    }
}
