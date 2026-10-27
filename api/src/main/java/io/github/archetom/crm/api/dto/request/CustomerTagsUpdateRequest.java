package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class CustomerTagsUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @Size(max = 50)
    private List<@NotBlank @Size(max = 50) String> tags;
}
