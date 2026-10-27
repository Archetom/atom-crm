package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class LeadDuplicateCheckRequest {
    @Positive
    private Long excludedLeadId;
    @Size(max = 200)
    private String name;
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$")
    private String phoneNumber;
    @Size(max = 200)
    private Map<@Size(max = 64) String,
            @Size(max = 20) List<@Size(max = 500) String>> customFields;
}
