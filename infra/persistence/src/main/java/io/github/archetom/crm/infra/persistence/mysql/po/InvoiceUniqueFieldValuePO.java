package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_invoice_unique_field_value")
public class InvoiceUniqueFieldValuePO {
    private Long tenantId;
    private Long invoiceId;
    private String objectType;
    private Long fieldId;
    private String normalizedHash;
    private String normalizedValue;
    private LocalDateTime createdTime;
}
