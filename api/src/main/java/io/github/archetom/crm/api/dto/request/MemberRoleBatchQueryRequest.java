package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** POST-only bounded member-role batch query input. */
@Data
@Accessors(chain = true)
public class MemberRoleBatchQueryRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> actorIds;
}
