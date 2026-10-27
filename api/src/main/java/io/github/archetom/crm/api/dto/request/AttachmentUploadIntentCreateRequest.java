package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.experimental.Accessors;

/** The deliberately small target-first upload declaration. */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = false)
public class AttachmentUploadIntentCreateRequest {
    @NotBlank
    private String displayName;
    @NotBlank
    private String declaredMediaType;
    @NotNull
    @Positive
    private Long expectedBytes;
    private String expectedSha256;

    /** Only valid for a marketing-activity target; omitted values are ordinary attachments. */
    @Schema(allowableValues = {"COVER", "DETAIL"},
            description = "Marketing activity image group; only PNG or JPEG files are allowed")
    private String marketingActivityImageType;

    /** Only valid for a Product target; omitted values are ordinary attachments. */
    @Schema(allowableValues = {"PRIMARY", "DETAIL"},
            description = "Product image group; only PNG or JPEG files are allowed")
    private String productImageType;
}
