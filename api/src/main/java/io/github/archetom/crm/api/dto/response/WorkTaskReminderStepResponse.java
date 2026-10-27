package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskReminderStepResponse {
    private String stepKey;
    private String timing;
    private Integer offsetMinutes;
    private List<String> recipientRoles;
}
