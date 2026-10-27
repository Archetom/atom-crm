package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Relational representation of a tenant membership. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization_membership")
public class OrganizationMembershipPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorId;
    private Long departmentId;
    private String status;
}
