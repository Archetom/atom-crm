package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Public, tenant-safe User representation with masked contact data. */
@Data
@Accessors(chain = true)
public class UserResponse {

    /**
     * user ID
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    /**
     * username
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * email
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    /**
     * phone number (masked)
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String maskedPhoneNumber;

    /**
     * real name
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String realName;

    /**
     * status
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"ACTIVE", "INACTIVE", "LOCKED"})
    private String status;

    /**
     * status
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusName;

    /**
     * whether active
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean active;

    /**
     * created time
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdTime;

    /**
     * updated time
     */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updatedTime;
}
