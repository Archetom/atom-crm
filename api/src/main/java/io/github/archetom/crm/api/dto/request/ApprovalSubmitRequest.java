package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ApprovalSubmitRequest {
    @NotNull
    @Positive
    private Long definitionId;
    @NotNull
    @Positive
    private Long subjectId;
    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
    /**
     * Kept source-compatible for older Java clients, but never serialized or
     * consumed.  The server derives the display title from the approved
     * definition and authoritative business record.
     */
    @JsonIgnore
    private String title;
    /** Client supplied facts are deliberately ignored; routing is authoritative. */
    @JsonIgnore
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 20) List<@NotNull @Size(max = 500) String>> facts;
    @Size(max = 50)
    private Map<@NotBlank @Size(max = 64) String,
            @Size(max = 100) List<@NotNull @Positive Long>> selectedApprovers;
    /** Watchers are definition-owned; callers cannot add hidden participants. */
    @JsonIgnore
    private java.util.List<Long> watcherActorIds;
}
