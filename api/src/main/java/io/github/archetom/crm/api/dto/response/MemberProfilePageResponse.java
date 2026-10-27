package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Typed page of tenant-visible member profiles. */
@Data
@Accessors(chain = true)
public class MemberProfilePageResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long pageNum;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long pageSize;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalNum;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MemberProfileResponse> objectList;
}
