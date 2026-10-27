package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class InvoiceProfileResponse {
    private Long id;
    private Long customerId;
    private String titleType;
    private String title;
    private String taxNumber;
    private String bankName;
    private String bankAccount;
    private String registeredAddress;
    private String registeredPhone;
    private String remark;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
