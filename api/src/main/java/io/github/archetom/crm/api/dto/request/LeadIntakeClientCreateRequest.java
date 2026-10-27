package io.github.archetom.crm.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** The server generates a high-entropy intake credential for this client. */
@Data
@Accessors(chain = true)
@Schema(description = "An empty request; the server generates the intake credential and owner binding")
public class LeadIntakeClientCreateRequest {
}
