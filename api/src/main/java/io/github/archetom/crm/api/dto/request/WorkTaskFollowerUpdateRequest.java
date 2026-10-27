package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskFollowerUpdateRequest {

    @NotNull
    @Size(max = 50)
    private List<@NotNull @Positive Long> followerActorIds;

    @NotNull
    @Min(0)
    private Long version;
}
