package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_product_field_value")
public class ProductFieldValuePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long productId;
    private String objectType;
    private Long fieldId;
    private String valuesJson;
    private String scalarValue;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
