package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** Public intake payload; tenant, owner, roles, and custom fields are never caller controlled. */
@Data
@Accessors(chain = true)
public class OpenLeadSubmissionRequest {
    @NotBlank
    @Size(max = 200)
    private String name;
    @Pattern(regexp = "^(?:\\+[1-9]\\d{7,14})?$")
    private String phoneNumber;
    @Size(max = 50)
    private String telephone;
    @Email
    @Size(max = 254)
    private String email;
    @Size(max = 2000)
    private String description;

    public LeadCreateRequest toInternalRequest() {
        return new LeadCreateRequest().setName(name).setPhoneNumber(phoneNumber)
                .setTelephone(telephone).setEmail(email).setDescription(description);
    }
}
