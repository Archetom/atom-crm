package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/** Self-inbox paging request. */
@Data
@Accessors(chain = true)
public class NotificationQueryRequest extends QueryRequest {
    @NotNull
    private Boolean unreadOnly = false;
}
