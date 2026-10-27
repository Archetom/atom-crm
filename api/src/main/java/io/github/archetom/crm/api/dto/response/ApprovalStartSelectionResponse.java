package io.github.archetom.crm.api.dto.response;

/** A route step for which the applicant must select server-validated candidates. */
public record ApprovalStartSelectionResponse(String code, String name) {
}
