package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Relational representation of a tenant-owned department. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization_department")
public class DepartmentPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentDepartmentId;
    private String name;
    private String status;
}
