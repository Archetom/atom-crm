package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskSeriesUpdateRequest {
    @NotBlank @TrimmedCodePointSize(max = 200) private String title;
    @TrimmedCodePointSize(max = 10_000) private String description;
    @NotBlank @Size(max = 20) private String priority;
    @NotNull @Positive private Long assigneeActorId;
    @Size(max = 50) private List<@NotNull @Positive Long> followerActorIds;
    @NotBlank @Size(max = 20) private String recurrenceFrequency;
    @NotNull @Positive private Integer recurrenceInterval;
    @Size(max = 7) private List<@NotBlank @Size(max = 12) String> weekdays;
    private Integer monthDay;
    private Boolean lastDayOfMonth;
    @NotBlank @Size(max = 100) private String zoneId;
    @NotNull private LocalDate startLocalDate;
    @NotNull private LocalTime localTime;
    private LocalDate endLocalDate;
    @NotNull @Min(0) private Long version;
}
