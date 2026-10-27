package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class FieldLayoutUpdateRequest {
    @Min(0)
    private Long version;
    @Size(max = 200)
    private List<String> orderedCodes = List.of();
    @Size(max = 200)
    private List<String> hiddenCodes = List.of();
    @Size(max = 200)
    private List<String> pinnedCodes = List.of();
    @Size(max = 200)
    private Map<String, Integer> columnWidths = Map.of();
}
