package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
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
public class ActivityCreateRequest {
    @NotBlank @Size(max = 30)
    private String subjectType;
    @NotNull @Positive
    private Long subjectId;
    @Size(max = 100)
    private String category;
    @Size(max = 200)
    private String purpose;
    @NotBlank @Size(max = 10_000)
    private String content;
    @NotNull
    private LocalDateTime occurredTime;
    private LocalDateTime nextContactTime;
    @Size(max = 50)
    private List<@Valid @NotNull ActivityRelationRequest> relations;
}
