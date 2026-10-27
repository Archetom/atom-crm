package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_return_visit_unique_field_value")
public class ReturnVisitUniqueFieldValuePO {
    private Long tenantId;
    private Long returnVisitId;
    private String objectType;
    private Long fieldId;
    private String normalizedHash;
    private String normalizedValue;
    private LocalDateTime createdTime;
}
