package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Value-free, append-only B23 adoption header. */
@Data
@TableName("crm_enterprise_data_adoption")
public class EnterpriseDataAdoptionPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String operationId;
    private Long tenantId;
    private Long customerId;
    private Long actorId;
    private Long primaryIdentityAssociationId;
    private String sourceKind;
    private String sourceBindingRef;
    private String adapterEvidenceRef;
    private String policyVersion;
    private String attributionVersion;
    private String mappingVersion;
    private LocalDateTime observedAt;
    private LocalDateTime expiresAt;
    private Long expectedCustomerVersion;
    private Long committedCustomerVersion;
    private LocalDateTime adoptedTime;
    private LocalDateTime deleteAfter;
}
