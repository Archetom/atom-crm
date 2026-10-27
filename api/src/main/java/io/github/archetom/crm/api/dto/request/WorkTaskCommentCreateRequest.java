package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WorkTaskCommentCreateRequest {

    @NotBlank
    @TrimmedCodePointSize(max = 5_000)
    private String content;
}
