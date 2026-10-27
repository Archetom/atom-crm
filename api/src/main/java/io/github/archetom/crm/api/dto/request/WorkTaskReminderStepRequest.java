package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Fixed Work reminder step transport shape; it accepts no text or external recipient. */
@Data
@Accessors(chain = true)
public class WorkTaskReminderStepRequest {
    @NotNull @Size(max = 16)
    private String timing;
    @NotNull
    private Integer offsetMinutes;
    @NotNull @Size(min = 1, max = 3)
    private List<@NotNull @Size(max = 16) String> recipientRoles;
}
