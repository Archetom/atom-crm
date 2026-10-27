package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Accessors(chain = true)
public class StageAdvanceRequest {
    @NotNull
    @Min(0)
    private Long version;
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @Size(max = 500) String>> fieldValues;
    /** Typed IDs for USER fields. Values from the scalar fieldValues map are rejected. */
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @jakarta.validation.constraints.Positive Long>> actorFieldValues;
    /** Typed IDs for DEPARTMENT fields. Values from the scalar fieldValues map are rejected. */
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @jakarta.validation.constraints.Positive Long>> departmentFieldValues;
    /** Typed IDs for RELATION fields. Values from the scalar fieldValues map are rejected. */
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @jakarta.validation.constraints.Positive Long>> relationFieldValues;
    /** Structured ADDRESS values. Values from the scalar fieldValues map are rejected. */
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String, @NotNull @Valid StageAddressValueRequest> addressFieldValues;
    /** One-shot draft created by the record- and field-scoped file-draft endpoint. */
    @jakarta.validation.constraints.Positive
    private Long fileFieldDraftId;
    /** Exact FILE IDs from fileFieldDraftId, keyed by writable FILE field code. */
    @Size(max = 100)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 10) List<@NotNull @jakarta.validation.constraints.Positive Long>> fileFields;
    @Size(max = 100)
    private Set<@NotBlank @Size(max = 64) String> completedTaskCodes;
    @Size(max = 50)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 100) List<@NotNull @jakarta.validation.constraints.Positive Long>> selectedApprovers;
}
