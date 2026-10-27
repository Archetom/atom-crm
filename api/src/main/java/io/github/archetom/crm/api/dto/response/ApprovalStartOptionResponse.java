package io.github.archetom.crm.api.dto.response;

import java.util.List;

/** One definition the current member may start for the specified business record. */
public record ApprovalStartOptionResponse(Long definitionId, String name,
                                          List<ApprovalStartSelectionResponse> selections) {
}
