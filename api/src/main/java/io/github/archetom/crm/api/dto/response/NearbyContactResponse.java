package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NearbyContactResponse {
    private ContactResponse contact;
    private Long distanceMeters;
}
