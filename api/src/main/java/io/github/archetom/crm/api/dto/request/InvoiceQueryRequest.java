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
public class InvoiceQueryRequest extends QueryRequest {
    @TrimmedCodePointSize(max = 200)
    private String keyword;
    @Positive
    private Long ownerActorId;
    private Boolean subordinateOwnerOnly;
    @Positive
    private Long customerId;
    @Positive
    private Long contractId;
    @Size(max = 20)
    private String status;
    @Schema(allowableValues = {"UPDATED_TIME", "CREATED_TIME", "REQUESTED_DATE", "ISSUED_DATE", "APPLICATION_NUMBER", "INVOICE_NUMBER", "OWNER_ACTOR"})
    @Pattern(regexp = "UPDATED_TIME|CREATED_TIME|REQUESTED_DATE|ISSUED_DATE|APPLICATION_NUMBER|INVOICE_NUMBER|OWNER_ACTOR")
    private String sortField;
    /** Server-catalog custom field code; static and custom sorting are mutually exclusive. */
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
