package io.github.archetom.crm.application.port.out.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/** Owns one source's root-lock order, current authorization, and fixed dispatch content. */
public interface NotificationDeliverySourceAdmission {
    NotificationDeliverySourceKind sourceKind();

    NotificationDeliveryAdmission admit(NotificationDelivery claim, NotificationChannelAdapter adapter,
                                        UUID leaseOwner, LocalDateTime now);
}
