package io.github.archetom.crm.application.vo;

import java.util.List;

public record WorkTaskReminderStepVO(
        String stepKey, String timing, Integer offsetMinutes, List<String> recipientRoles) {
}
