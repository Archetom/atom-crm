package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer_tag")
public class CustomerTagPO {
    private Long tenantId;
    private Long customerId;
    private String tagName;
    private String tagKey;
    private LocalDateTime createdTime;
}
