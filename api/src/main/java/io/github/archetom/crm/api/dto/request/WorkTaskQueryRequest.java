package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class WorkTaskQueryRequest extends QueryRequest {

    @Size(max = 20)
    private String participantRole;

    @Size(max = 4)
    private List<@Size(min = 1, max = 20) String> statuses;

    @Size(max = 4)
    private List<@Size(min = 1, max = 20) String> priorities;

    private LocalDateTime dueFromInclusive;

    private LocalDateTime dueBefore;

    @Size(max = 30)
    private String businessObjectType;

    @Positive
    private Long businessRecordId;
}
