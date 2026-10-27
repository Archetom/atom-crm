package io.github.archetom.crm.application.security.attachment;

import io.github.archetom.crm.domain.model.AttachmentTargetType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Explicit registrations only; a missing B24b target can never fall back to a tenant guard. */
@Component
public class AttachmentTargetAccessRegistry {
    private final Map<AttachmentTargetType, AttachmentTargetAccess> values;

    public AttachmentTargetAccessRegistry(List<AttachmentTargetAccess> values) {
        Map<AttachmentTargetType, AttachmentTargetAccess> registered =
                new EnumMap<>(AttachmentTargetType.class);
        for (AttachmentTargetAccess value : values) {
            if (value == null || value.targetType() == null
                    || registered.putIfAbsent(value.targetType(), value) != null) {
                throw new IllegalStateException("attachment target access registration is invalid");
            }
        }
        if (registered.size() != AttachmentTargetType.values().length) {
            throw new IllegalStateException("attachment target access registration is incomplete");
        }
        this.values = Map.copyOf(registered);
    }

    public AttachmentTargetAccess require(AttachmentTargetType type) {
        AttachmentTargetAccess result = values.get(type);
        if (result == null) {
            throw new UnsupportedOperationException("attachment target is unsupported");
        }
        return result;
    }
}
