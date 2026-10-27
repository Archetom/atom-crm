package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class MarketingFormUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotBlank
    @Size(max = 200)
    private String title;
    @Size(max = 2000)
    private String description;
    @Size(max = 50)
    private String submitButtonText;
    @Size(max = 500)
    private String successMessage;
    @Positive
    private Long ownerActorId;
    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid MarketingFormFieldRequest> fields;
}
