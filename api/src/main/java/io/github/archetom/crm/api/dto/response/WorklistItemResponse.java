package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class WorklistItemResponse {
    private String kind;
    private Long sourceId;
    private Long sourceVersion;
    private String subjectType;
    private Long subjectId;
    private LocalDateTime dueTime;
    private String summary;
}
