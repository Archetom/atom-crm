package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ContactHierarchyResponse {
    private Long requestedContactId;
    private List<ContactResponse> contacts;
}
