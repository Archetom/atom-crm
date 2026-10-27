package io.github.archetom.crm.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** Fixed permission-aware business cards for one already visible customer. */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerBusinessOverviewResponse {
    private Long customerId;
    private List<CardResponse> cards;

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CardResponse {
        private String source;
        private String status;
        private Long count;
        private BigDecimal amount;
    }
}
