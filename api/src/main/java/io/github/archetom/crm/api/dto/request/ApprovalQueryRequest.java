package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ApprovalQueryRequest {
    private String bucket = "PENDING";
    private String status;
    private String objectType;
    @Positive
    private Long subjectId;
    @Size(max = 200)
    private String keyword;
    @Min(1)
    private Integer page = 1;
    @Min(1)
    @Max(200)
    private Integer size = 20;
}
