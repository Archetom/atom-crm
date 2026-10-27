package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class WorkTaskUpdateRequest {

    @NotBlank
    @TrimmedCodePointSize(max = 200)
    private String title;

    @TrimmedCodePointSize(max = 10_000)
    private String description;

    @NotBlank
    @Size(max = 20)
    private String priority;

    private LocalDateTime dueTime;

    @NotNull
    @Min(0)
    private Long version;
}
