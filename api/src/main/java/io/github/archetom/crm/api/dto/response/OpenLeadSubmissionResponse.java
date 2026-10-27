package io.github.archetom.crm.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Minimal acknowledgement for the public intake boundary. */
@Getter
@AllArgsConstructor
public class OpenLeadSubmissionResponse {
    private final boolean accepted;
}
