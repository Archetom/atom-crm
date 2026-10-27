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

/** Fixed, bounded criteria for the synchronous receivable-plan CSV export. */
@Data
@Accessors(chain = true)
public class ReceivablePlanCsvExportRequest {

    @NotEmpty
    @Size(max = 15)
    private List<@NotBlank @Size(min = 2, max = 64) String> fieldCodes;

    @TrimmedCodePointSize(max = 200)
    private String keyword;

    @Positive
    private Long ownerActorId;

    /** A server-resolved downline scene; it cannot be combined with a specific owner. */
    private Boolean subordinateOwnerOnly;

    @Positive
    private Long customerId;

    @Positive
    private Long contractId;

    @Positive
    private Long savedViewId;

    /** One-use advanced filters are validated again against the caller's field policy. */
    @Size(max = 50)
    private List<@Valid FilterConditionRequest> conditions = List.of();

    /** The fixed export contract deliberately has no extensibility parameters. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown receivable-plan CSV export property");
    }
}
