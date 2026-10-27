package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class CustomerCreateRequest {
    @NotBlank
    @Size(max = 200)
    private String name;
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$")
    private String phoneNumber;
    @Size(max = 50)
    private String telephone;
    @Email
    @Size(max = 254)
    private String email;
    @Size(max = 500)
    private String website;
    @Size(max = 2000)
    private String description;
    @Positive
    private Long ownerActorId;
    @Positive
    private Long parentCustomerId;
    @Size(max = 50)
    private List<@NotBlank @Size(max = 50) String> tags;
    @Size(max = 20)
    private List<@Valid CustomerAddressRequest> addresses;
    @Size(max = 100)
    private List<@Valid CustomerCollaboratorRequest> collaborators;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Long fileFieldDraftId;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;
}
