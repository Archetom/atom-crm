package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ReceivableQueryRequest extends QueryRequest {
    @TrimmedCodePointSize(max = 200)
    private String keyword;
    @Positive
    private Long ownerActorId;
    private Boolean subordinateOwnerOnly;
    @Positive
    private Long customerId;
    @Positive
    private Long contractId;
    @Positive
    private Long receivablePlanId;
    @Schema(allowableValues = {"RECEIVED_DATE", "CREATED_TIME", "UPDATED_TIME", "RECEIPT_NUMBER", "OWNER_ACTOR"})
    @Pattern(regexp = "RECEIVED_DATE|CREATED_TIME|UPDATED_TIME|RECEIPT_NUMBER|OWNER_ACTOR")
    private String sortField;
    /**
     * An active, non-built-in RECEIVABLE field code selected from the server-published field
     * catalog. It is mutually exclusive with {@link #sortField}; persistence receives only the
     * server-resolved field definition identifier and scalar type.
     */
    @Schema(pattern = "[a-z][a-z0-9_]{1,63}")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}")
    private String customSortFieldCode;
    @Schema(allowableValues = {"ASC", "DESC"})
    @Pattern(regexp = "ASC|DESC")
    private String sortDirection;
    @Positive
    private Long savedViewId;
    /** One-use advanced filters are validated again against the caller's field policy. */
    @Size(max = 50)
    private List<@jakarta.validation.Valid FilterConditionRequest> conditions = List.of();
}
