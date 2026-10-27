package io.github.archetom.crm.application.port.out.notification;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Explicit exhaustive source registry; a default fallback would weaken source ownership. */
@Component
public class NotificationDeliverySourceAdmissionRegistry {
    private final Map<NotificationDeliverySourceKind, NotificationDeliverySourceAdmission> admissions;

    public NotificationDeliverySourceAdmissionRegistry(List<NotificationDeliverySourceAdmission> admissions) {
        EnumMap<NotificationDeliverySourceKind, NotificationDeliverySourceAdmission> values =
                new EnumMap<>(NotificationDeliverySourceKind.class);
        for (NotificationDeliverySourceAdmission admission : admissions) {
            if (admission == null || admission.sourceKind() == null
                    || values.putIfAbsent(admission.sourceKind(), admission) != null) {
                throw new IllegalStateException("notification delivery source registry is invalid");
            }
        }
        if (values.size() != NotificationDeliverySourceKind.values().length) {
            throw new IllegalStateException("notification delivery source registry is incomplete");
        }
        this.admissions = Map.copyOf(values);
    }

    public NotificationDeliverySourceAdmission require(NotificationDeliverySourceKind sourceKind) {
        NotificationDeliverySourceAdmission admission = admissions.get(sourceKind);
        if (admission == null) throw new IllegalStateException("notification delivery source admission is missing");
        return admission;
    }
}
