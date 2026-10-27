package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Current tenant-scoped registration-identity reservation; never contains provider payload. */
@Data
@TableName("crm_customer_registered_identity")
public class CustomerRegisteredIdentityPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long customerId;
    private String identityScopeCode;
    private String registrySchemeCode;
    private String canonicalValue;
    private LocalDateTime createdTime;
    private LocalDateTime releasedTime;
}
