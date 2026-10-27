package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/** A saved-view condition may be exposed as a browser-controlled filter. */
@Getter
@Setter
@Accessors(chain = true)
public class SavedViewConditionRequest extends FilterConditionRequest {

    @Schema(defaultValue = "false")
    private Boolean exposed = false;

    @Override
    public SavedViewConditionRequest setFieldCode(String fieldCode) {
        super.setFieldCode(fieldCode);
        return this;
    }

    @Override
    public SavedViewConditionRequest setOperator(String operator) {
        super.setOperator(operator);
        return this;
    }

    @Override
    public SavedViewConditionRequest setValues(List<String> values) {
        super.setValues(values);
        return this;
    }
}
