package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskCreateRequest {

    @NotBlank
    @TrimmedCodePointSize(max = 200)
    private String title;

    @TrimmedCodePointSize(max = 10_000)
    private String description;

    @Size(max = 20)
    private String priority;

    private LocalDateTime dueTime;

    @NotNull
    @Positive
    private Long assigneeActorId;

    @Size(max = 50)
    private List<@NotNull @Positive Long> followerActorIds;

    @Size(max = 30)
    private String businessObjectType;

    @Positive
    private Long businessRecordId;
}
