package io.github.archetom.crm.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.archetom.crm.api.validation.TrimmedCodePointSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Fixed, bounded criteria for the synchronous Product CSV export. */
@Data
@Accessors(chain = true)
public class ProductCsvExportRequest {

    @NotEmpty
    @Size(max = 50)
    private List<@NotBlank @Size(min = 2, max = 64) String> fieldCodes;

    @TrimmedCodePointSize(max = 200)
    private String keyword;

    @Size(max = 200)
    private String name;

    @Size(max = 100)
    private String code;

    @Positive
    private Long categoryId;

    @Positive
    private Long ownerActorId;

    @Size(max = 20)
    private String availability;

    @Schema(allowableValues = {"UPDATED_TIME", "CREATED_TIME", "NAME", "OWNER_ACTOR"})
    @Pattern(regexp = "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR")
    private String sortField;

    @Schema(pattern = "[a-z][a-z0-9_]{1,63}")
    @Pattern(regexp = "[a-z][a-z0-9_]{1,63}")
    private String customSortFieldCode;

    @Schema(allowableValues = {"ASC", "DESC"})
    @Pattern(regexp = "ASC|DESC")
    private String sortDirection;

    @Positive
    private Long savedViewId;

    /** One-use advanced filters receive the same policy checks as saved views. */
    @Size(max = 50)
    private List<@Valid FilterConditionRequest> conditions = List.of();

    /** The fixed export contract deliberately has no extensibility parameters. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown Product CSV export property");
    }
}
