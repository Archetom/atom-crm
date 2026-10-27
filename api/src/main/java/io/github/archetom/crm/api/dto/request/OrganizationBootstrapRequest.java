package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Initial local profile for the first verified member of a tenant. */
@Data
@Accessors(chain = true)
public class OrganizationBootstrapRequest {

    @NotBlank(message = "Display name must not be empty")
    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email length must not exceed 254 characters")
    private String email;

    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must use E.164 format")
    private String phoneNumber;
}
