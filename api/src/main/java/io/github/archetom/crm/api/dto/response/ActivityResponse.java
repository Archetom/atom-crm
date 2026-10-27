package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ActivityResponse {
    private Long id;
    private String subjectType;
    private Long subjectId;
    private String kind;
    private Long creatorActorId;
    private String category;
    private String purpose;
    private String content;
    private LocalDateTime occurredTime;
    private LocalDateTime nextContactTime;
    private List<ActivityRelationResponse> relations;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
