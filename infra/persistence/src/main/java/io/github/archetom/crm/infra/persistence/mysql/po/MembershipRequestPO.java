package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** Relational representation of a request to join a target tenant. */
@Data
@TableName("organization_membership_request")
public class MembershipRequestPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantActorId;
    private Long targetTenantId;
    private Long targetDepartmentId;
    private String message;
    private String status;
    private Long reviewerActorId;
    @Version
    private Long version;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
