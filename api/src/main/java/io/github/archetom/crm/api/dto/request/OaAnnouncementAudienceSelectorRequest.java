package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OaAnnouncementAudienceSelectorRequest {

    @NotBlank
    @Schema(allowableValues = {"TENANT", "DEPARTMENT_SUBTREE", "MEMBER"})
    private String type;
    private Long departmentId;
    private Long memberActorId;

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException(
                "Unknown OA announcement audience property: " + name);
    }
}
