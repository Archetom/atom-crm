package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ReceivablePlanQueryRequest extends QueryRequest {
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
    private List<@jakarta.validation.Valid FilterConditionRequest> conditions = List.of();
}
