package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class WorkTaskCommentResponse {

    private Long id;
    private Long taskId;
    private Long authorActorId;
    private String content;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
