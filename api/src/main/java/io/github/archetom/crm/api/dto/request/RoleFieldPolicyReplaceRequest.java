package io.github.archetom.crm.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class RoleFieldPolicyReplaceRequest {
    @Min(0)
    private Long version;
    @Size(max = 200)
    private List<@Valid RoleFieldAccessRequest> accesses = List.of();
}
