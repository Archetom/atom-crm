package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** Relational row for one configured department performance target. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dashboard_department_performance_target")
public class DepartmentPerformanceTargetPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long departmentId;
    private String metric;
    private Integer targetYear;
    private String period;
    private Integer periodNumber;
    private BigDecimal amount;
}
