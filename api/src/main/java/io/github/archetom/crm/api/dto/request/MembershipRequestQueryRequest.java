package io.github.archetom.crm.api.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** Bounded query for membership requests targeting the caller's active tenant. */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class MembershipRequestQueryRequest extends QueryRequest {
}
