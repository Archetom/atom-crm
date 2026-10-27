package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Bounded navigation preference containing only code-defined item identifiers. */
@Data
@Accessors(chain = true)
public class NavigationPreferenceUpdateRequest {

    @Size(max = 200)
    private List<String> orderedCodes = List.of();

    @Size(max = 200)
    private List<String> hiddenCodes = List.of();

    @Min(0)
    private Long version;
}
