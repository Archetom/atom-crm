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
public class ContractResponse {
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    private Long opportunityId;
    private Long contactId;
    private String name;
    private String number;
    private LocalDate orderDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String contractType;
    private String paymentType;
    private String remark;
    private BigDecimal subtotal;
    private BigDecimal discountRate;
    private BigDecimal totalAmount;
    private String status;
    private List<ContractProductLineResponse> products;
    private List<ContractCollaboratorResponse> collaborators;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
