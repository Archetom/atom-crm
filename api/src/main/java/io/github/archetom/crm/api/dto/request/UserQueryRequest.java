package io.github.archetom.crm.api.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Validated filters for the tenant-scoped User query. */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class UserQueryRequest extends QueryRequest {

    /**
     * username
     */
    @Size(max = 50, message = "Username filter length must not exceed 50 characters")
    private String username;

    /**
     * email
     */
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email filter length must not exceed 254 characters")
    private String email;

    /**
     * status
     */
    private String status;
}
