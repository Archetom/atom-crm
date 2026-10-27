package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ReturnVisitOptionResponse {
    private Long id;
    private String visitNumber;
    private LocalDateTime visitTime;
}
