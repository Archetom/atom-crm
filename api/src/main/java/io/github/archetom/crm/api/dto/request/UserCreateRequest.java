package io.github.archetom.crm.api.dto.request;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public request for creating a tenant-scoped user. */
@Data
@Accessors(chain = true)
public class UserCreateRequest {

    /**
     * username
     */
    @NotBlank(message = "Username must not be empty")
    @Size(min = 3, max = 50, message = "Username length must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,50}$",
            message = "Username may contain only letters, digits, and underscores")
    private String username;

    /**
     * email
     */
    @NotBlank(message = "Email must not be empty")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email length must not exceed 254 characters")
    private String email;

    /**
     * phone number
     */
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must use E.164 format")
    private String phoneNumber;

    /**
     * password
     */
    @NotBlank(message = "Password must not be empty")
    @Size(min = 12, max = 64, message = "Password length must be between 12 and 64 characters")
    @ToString.Exclude
    private String password;

    /**
     * real name
     */
    @Size(max = 100, message = "Real name length must not exceed 100 characters")
    private String realName;
}
