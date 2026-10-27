package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** Bounded display-name search across active memberships in the caller's tenant. */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class MemberProfileQueryRequest extends QueryRequest {

    @Size(max = 100, message = "Member search keyword must not exceed 100 characters")
    private String keyword;
}
