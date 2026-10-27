package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class AttachmentResponse {
    private Long fileId;
    private String displayName;
    private String mediaType;
    private Long actualBytes;
    private Long uploaderActorId;
    private LocalDateTime createdTime;
    private LocalDateTime availableTime;
    private Long version;
}
