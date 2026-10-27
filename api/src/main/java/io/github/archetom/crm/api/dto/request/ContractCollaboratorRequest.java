package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

@Data
@Accessors(chain = true)
public class ContractCollaboratorRequest {
    @NotNull @Positive
    private Long actorId;
    @NotBlank @Size(max = 20)
    private String access;
    /** Null means the team member remains effective without a deadline. */
    @FutureOrPresent
    @Schema(nullable = true, description = "Inclusive local date after which collaboration no longer grants access")
    private LocalDate effectiveUntil;
}
