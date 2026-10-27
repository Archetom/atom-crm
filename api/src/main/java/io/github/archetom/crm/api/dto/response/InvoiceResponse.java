package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class InvoiceResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long contractId;
    private String applicationNumber;
    private BigDecimal amount;
    private LocalDate requestedDate;
    private String invoiceType;
    private String titleType;
    private String title;
    private String taxNumber;
    private String bankName;
    private String bankAccount;
    private String registeredAddress;
    private String registeredPhone;
    private String contactName;
    private String contactPhone;
    private String shippingAddress;
    private String remark;
    private String status;
    private String invoiceNumber;
    private LocalDate issuedDate;
    private String logisticsNumber;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
