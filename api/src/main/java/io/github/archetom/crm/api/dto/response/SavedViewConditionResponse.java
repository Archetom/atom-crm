package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** A returned saved-view condition and its browser exposure setting. */
@Getter
@Setter
@Accessors(chain = true)
public class SavedViewConditionResponse extends FilterConditionResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean exposed;

    @Override
    public SavedViewConditionResponse setFieldCode(String fieldCode) {
        super.setFieldCode(fieldCode);
        return this;
    }

    @Override
    public SavedViewConditionResponse setOperator(String operator) {
        super.setOperator(operator);
        return this;
    }

    @Override
    public SavedViewConditionResponse setValues(java.util.List<String> values) {
        super.setValues(values);
        return this;
    }
}
