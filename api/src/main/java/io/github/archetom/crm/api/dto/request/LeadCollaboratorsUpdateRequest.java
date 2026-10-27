package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class LeadCollaboratorsUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @Size(max = 100)
    private List<@Valid LeadCollaboratorRequest> collaborators;
}
