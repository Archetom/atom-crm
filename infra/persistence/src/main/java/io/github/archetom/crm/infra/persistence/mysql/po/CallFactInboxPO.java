package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_call_fact_inbox")
public class CallFactInboxPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long connectorId;
    private byte[] deliveryRef;
    private byte[] factDigest;
    private String outcome;
    private LocalDateTime createdTime;
    private LocalDateTime settledTime;
}
