package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Accessors(chain = true)
public class OaAnnouncementAudienceSelectorResponse {
    @Schema(allowableValues = {"TENANT", "DEPARTMENT_SUBTREE", "MEMBER"})
    private String type;
    private Long departmentId;
    private Long memberActorId;
    private String displayName;
}
