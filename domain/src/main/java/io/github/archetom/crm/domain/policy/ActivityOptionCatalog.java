package io.github.archetom.crm.domain.policy;

import io.github.archetom.crm.domain.entity.ActivityOption;
import io.github.archetom.crm.domain.model.ActivityOptionType;

import java.util.List;

public final class ActivityOptionCatalog {
    private ActivityOptionCatalog() {
    }

    public static List<ActivityOption> defaults() {
        return List.of(
                new ActivityOption(ActivityOptionType.METHOD, "CALL", "Call", 10),
                new ActivityOption(ActivityOptionType.METHOD, "MEETING", "Meeting", 20),
                new ActivityOption(ActivityOptionType.METHOD, "EMAIL", "Email", 30),
                new ActivityOption(ActivityOptionType.TEMPLATE, "GENERAL_FOLLOW_UP",
                        "General follow-up", 10));
    }
}
