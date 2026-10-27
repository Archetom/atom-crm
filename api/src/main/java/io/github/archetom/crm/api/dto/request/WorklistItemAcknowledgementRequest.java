package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class WorklistItemAcknowledgementRequest {
    @NotBlank
    @Size(max = 40)
    private String kind;
    @NotNull
    @Positive
    private Long sourceId;
    @NotNull
    @Min(0)
    private Long sourceVersion;
    @NotNull
    private LocalDateTime dueTime;
}
