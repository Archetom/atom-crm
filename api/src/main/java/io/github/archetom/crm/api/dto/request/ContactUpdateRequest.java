package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.github.archetom.crm.api.json.ContactUpdateRequestSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
@JsonSerialize(using = ContactUpdateRequestSerializer.class)
public class ContactUpdateRequest {
    @NotNull
    @PositiveOrZero
    private Long version;
    @Size(max = 200)
    private String name;
    @Pattern(regexp = "^(?:\\+[1-9]\\d{7,14})?$")
    private String phoneNumber;
    @Size(max = 50)
    private String telephone;
    @Size(max = 200)
    private String jobTitle;
    @Email
    @Size(max = 254)
    private String email;
    @Size(max = 500)
    private String website;
    @Size(max = 2000)
    private String description;
    @Schema(nullable = true, description = "Optional next contact time; send null to clear it")
    private LocalDateTime nextContactTime;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private boolean nextContactTimeSpecified;
    @Schema(nullable = true, description = "Optional customer relation; send null to clear it")
    @Positive
    private Long customerId;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private boolean customerIdSpecified;
    @Size(max = 20)
    private List<@Valid ContactAddressRequest> addresses;
    @Size(max = 200)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
    private Map<@NotBlank @Size(max = 64) String, @Size(max = 10) List<@Positive Long>> fileFields;

    @JsonSetter("customerId")
    public ContactUpdateRequest setCustomerId(Long customerId) {
        this.customerId = customerId;
        this.customerIdSpecified = true;
        return this;
    }

    @JsonSetter("nextContactTime")
    public ContactUpdateRequest setNextContactTime(LocalDateTime nextContactTime) {
        this.nextContactTime = nextContactTime;
        this.nextContactTimeSpecified = true;
        return this;
    }

    @JsonIgnore
    public boolean customerIdSpecified() {
        return customerIdSpecified;
    }

    @JsonIgnore
    public boolean nextContactTimeSpecified() {
        return nextContactTimeSpecified;
    }
}
