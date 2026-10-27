package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** Relational initialization marker for one local tenant. */
@Data
@TableName("organization_tenant")
public class OrganizationTenantPO {

    @TableId(value = "tenant_id", type = IdType.INPUT)
    private Long tenantId;
    private Long initializedByActorId;
    @Version
    private Long version = 0L;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
