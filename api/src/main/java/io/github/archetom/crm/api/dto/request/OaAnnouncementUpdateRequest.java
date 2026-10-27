package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class OaAnnouncementUpdateRequest {

    @NotBlank
    @Size(max = 400)
    private String title;
    @NotBlank
    @Size(max = 40_000)
    private String body;
    @NotNull
    @Size(min = 1, max = 10_000)
    private List<@Valid OaAnnouncementAudienceSelectorRequest> audience;
    @NotNull
    @Min(0)
    private Long version;

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException(
                "Unknown OA announcement update property: " + name);
    }
}
