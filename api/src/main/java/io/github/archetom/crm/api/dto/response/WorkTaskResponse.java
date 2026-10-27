package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class WorkTaskResponse {

    private Long id;
    private Long createdByActorId;
    private Long currentAssigneeActorId;
    private List<Long> followerActorIds;
    private String originKind;
    private Long originSeriesId;
    private Long originSeriesGeneration;
    private Long originOccurrenceSequence;
    private LocalDateTime originNominalLocalTime;
    private String originZoneId;
    private Integer originOffsetSeconds;
    private LocalDateTime originResolvedDueTime;
    private String businessObjectType;
    private Long businessRecordId;
    private String title;
    private String description;
    private String priority;
    private LocalDateTime dueTime;
    private boolean overdue;
    private String status;
    private LocalDateTime firstStartedTime;
    private Long worklistProjectionVersion;
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
