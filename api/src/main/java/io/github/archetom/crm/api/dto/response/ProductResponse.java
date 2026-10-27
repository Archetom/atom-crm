package io.github.archetom.crm.api.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ProductResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private Long ownerActorId;
    private String name;
    private String code;
    private String unit;
    private BigDecimal listPrice;
    private String description;
    private String availability;
    private String status;
    private Map<String, List<String>> customFields;
    private Map<String, List<AttachmentResponse>> fileFields;
    private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
