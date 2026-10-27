package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Relational representation of an explicit member-role assignment. */
@Data
@TableName("authorization_role_assignment")
public class RoleAssignmentPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long actorId;
    private Long roleId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
