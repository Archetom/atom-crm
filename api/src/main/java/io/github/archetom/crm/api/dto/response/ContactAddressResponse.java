package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ContactAddressResponse {
    private String key;
    private String label;
    private String country;
    private String region;
    private String city;
    private String postalCode;
    private String addressLine;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Boolean primary;
}
