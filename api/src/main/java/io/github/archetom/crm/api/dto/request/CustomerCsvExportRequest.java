package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Fixed, bounded criteria for the synchronous customer CSV export. */
@Data
@Accessors(chain = true)
public class CustomerCsvExportRequest {

    @NotEmpty
    @Size(max = 50)
    private List<@NotBlank @Size(min = 2, max = 64) String> fieldCodes;

    @TrimmedCodePointSize(max = 200)
    private String keyword;

    @Size(max = 200)
    private String name;

    @Positive
    private Long ownerActorId;

    /**
     * Restricts export rows to owners in the caller's server-resolved descendant departments.
     * The client cannot supply members or departments for this scene.
     */
    private Boolean subordinateOwnerOnly;

    private Boolean starred;

    @Positive
    private Long savedViewId;

    /** One-use advanced filters that receive the same field-policy checks as saved views. */
    @Size(max = 50)
    private List<@Valid FilterConditionRequest> conditions = List.of();
}
