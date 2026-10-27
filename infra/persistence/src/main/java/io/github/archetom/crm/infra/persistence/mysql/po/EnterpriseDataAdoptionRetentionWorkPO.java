package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** One value-free, tenant-carrying deletion task for one adoption header. */
@Data
@TableName("crm_enterprise_data_adoption_retention_work")
public class EnterpriseDataAdoptionRetentionWorkPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long adoptionId;
    private LocalDateTime deleteAfter;
    private String workState;
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;
    private Long leaseFence;
    private LocalDateTime createdTime;
}
