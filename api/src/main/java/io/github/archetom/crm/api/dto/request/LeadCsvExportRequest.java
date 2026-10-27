package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Fixed, bounded criteria for the synchronous Lead CSV export. */
@Data
@Accessors(chain = true)
public class LeadCsvExportRequest {

    @NotEmpty
    @Size(max = 50)
    private List<@NotBlank @Size(min = 2, max = 64) String> fieldCodes;

    @TrimmedCodePointSize(max = 200)
    private String keyword;

    @Size(max = 200)
    private String name;

    @Positive
    private Long ownerActorId;

    private Boolean subordinateOwnerOnly;

    private Boolean starred;

    private Boolean converted;

    @Positive
    private Long savedViewId;

    /** One-use advanced filters that receive the same field-policy checks as saved views. */
    @Size(max = 50)
    private List<@Valid FilterConditionRequest> conditions = List.of();

    /** The fixed export contract deliberately has no extensibility parameters. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown Lead CSV export property");
    }
}
