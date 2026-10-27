package io.github.archetom.crm.api.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** Typed page of server-visible customers within one accessible customer pool. */
@Data
@Accessors(chain = true)
public class CustomerPoolCustomerPageResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private long pageNum;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private long pageSize;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
    private long totalNum;
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = CustomerResponse.class))
    private List<CustomerResponse> objectList;
}
