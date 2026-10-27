package io.github.archetom.crm.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class CustomerAddressRequest {
    @Size(max = 64)
    private String key;
    @Size(max = 100)
    private String label;
    @Size(max = 100)
    private String country;
    @Size(max = 100)
    private String region;
    @Size(max = 100)
    private String city;
    @Size(max = 30)
    private String postalCode;
    @NotBlank
    @Size(max = 500)
    private String addressLine;
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;
    private Boolean primary;
}
