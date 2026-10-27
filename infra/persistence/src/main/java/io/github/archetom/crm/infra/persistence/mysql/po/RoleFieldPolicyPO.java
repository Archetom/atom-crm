package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role_field_policy")
public class RoleFieldPolicyPO extends BasePO {
    private Long roleId;
    private String objectType;
}
