package io.github.archetom.crm.application.port.out.notification;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Explicit closed-channel registry; the sample welcome logging adapter is intentionally not included. */
@Component
public class NotificationChannelAdapterRegistry {
    private final Map<NotificationChannel, NotificationChannelAdapter> adapters;

    public NotificationChannelAdapterRegistry(List<NotificationChannelAdapter> adapters) {
        EnumMap<NotificationChannel, NotificationChannelAdapter> values = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelAdapter adapter : adapters) {
            if (adapter == null || adapter.channel() == null || values.putIfAbsent(adapter.channel(), adapter) != null) {
                throw new IllegalStateException("notification channel adapter registry is invalid");
            }
        }
        this.adapters = Map.copyOf(values);
    }

    public NotificationChannelAdapter require(NotificationChannel channel) {
        NotificationChannelAdapter adapter = adapters.get(channel);
        if (adapter == null) throw new IllegalStateException("notification channel adapter is missing");
        return adapter;
    }
}
