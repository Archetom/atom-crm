package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class WorkTaskSeriesOccurrenceResponse {
    private Long id;
    private Long seriesId;
    private Long seriesGeneration;
    private Long occurrenceSequence;
    private String status;
    private LocalDateTime nominalLocalTime;
    private String zoneId;
    private Integer resolvedOffsetSeconds;
    private LocalDateTime resolvedDueTime;
    private Long generatedWorkTaskId;
    private String safeReason;
    private LocalDateTime createdTime;
}
