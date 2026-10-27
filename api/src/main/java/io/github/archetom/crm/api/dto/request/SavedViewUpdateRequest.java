package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@Data
@Accessors(chain = true)
public class SavedViewUpdateRequest {
    @NotNull
    @Min(0)
    private Long version;
    @NotBlank
    @Size(max = 100)
    private String name;
    @Size(max = 50)
    @ArraySchema(schema = @Schema(implementation = SavedViewConditionRequest.class))
    @JsonDeserialize(contentAs = SavedViewConditionRequest.class)
    private List<@Valid SavedViewConditionRequest> conditions = List.of();
    @Min(0)
    @Max(10000)
    private Integer sortOrder = 0;
    private Boolean hidden = false;

    /** Retains source compatibility for callers that construct ordinary filter conditions. */
    public SavedViewUpdateRequest setConditions(
            java.util.Collection<? extends FilterConditionRequest> conditions) {
        this.conditions = (conditions == null ? List.<FilterConditionRequest>of() : conditions).stream()
                .map(SavedViewUpdateRequest::asSavedViewCondition).toList();
        return this;
    }

    private static SavedViewConditionRequest asSavedViewCondition(FilterConditionRequest condition) {
        if (condition == null) {
            return null;
        }
        if (condition instanceof SavedViewConditionRequest savedCondition) {
            return savedCondition;
        }
        SavedViewConditionRequest savedCondition = new SavedViewConditionRequest();
        savedCondition.setFieldCode(condition.getFieldCode())
                .setOperator(condition.getOperator()).setValues(condition.getValues());
        return savedCondition;
    }
}
