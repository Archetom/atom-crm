package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ActivityUpdateRequest {
    @NotNull @PositiveOrZero
    private Long version;
    @Size(max = 100)
    private String category;
    @Size(max = 200)
    private String purpose;
    @NotBlank @Size(max = 10_000)
    private String content;
    @NotNull
    private LocalDateTime occurredTime;
    @Schema(nullable = true, description = "Optional next contact time; send null to clear it")
    private LocalDateTime nextContactTime;
    @Size(max = 50)
    private List<@Valid @NotNull ActivityRelationRequest> relations;
}
