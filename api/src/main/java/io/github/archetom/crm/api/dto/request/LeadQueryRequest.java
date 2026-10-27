package io.github.archetom.crm.api.dto.request;

import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
public class LeadQueryRequest extends QueryRequest {
    @TrimmedCodePointSize(max = 200)
    private String keyword;
    @Size(max = 200)
    private String name;
    @Positive
    private Long ownerActorId;
    /**
     * Restricts results to owners in the caller's server-resolved descendant departments.
     * The client cannot supply members or departments for this scene.
     */
    private Boolean subordinateOwnerOnly;
    private Boolean starred;
    /**
     * When present, restricts the visible list to active (false) or converted (true) leads.
     * Visibility and field access are still resolved from the authenticated caller.
     */
    private Boolean converted;
    @Schema(allowableValues = {"UPDATED_TIME", "CREATED_TIME", "NAME", "OWNER_ACTOR"})
    @Pattern(regexp = "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR")
    private String sortField;
    /**
     * An active, non-built-in LEAD field code selected from the server-published field catalog.
     * It is mutually exclusive with {@link #sortField}; the server resolves it to a field definition
     * before any persistence query is built.
     */
    @Schema(pattern = "[a-z][a-z0-9_]{1,63}")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}")
    private String customSortFieldCode;
    @Schema(allowableValues = {"ASC", "DESC"})
    @Pattern(regexp = "ASC|DESC")
    private String sortDirection;
    @Positive
    private Long savedViewId;
    @Size(max = 50)
    private List<@Valid FilterConditionRequest> conditions = List.of();
}
