package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer_field_value")
public class CustomerFieldValuePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long customerId;
    private String objectType;
    private Long fieldId;
    private String valuesJson;
    private String scalarValue;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
