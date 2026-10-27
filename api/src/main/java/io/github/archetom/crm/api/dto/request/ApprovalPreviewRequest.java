package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ApprovalPreviewRequest {
    @NotNull
    @Positive
    private Long subjectId;
    /** Preview routing facts are resolved from the business record, not accepted from the client. */
    @JsonIgnore
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @Size(max = 500) String>> facts;
    @Size(max = 50)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 100) List<@NotNull @Positive Long>> selectedApprovers;
}
