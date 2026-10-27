package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NearbyCustomerResponse {
    private CustomerResponse customer;
    private Long distanceMeters;
}
