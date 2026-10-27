package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** Relational row for one configured member monthly performance target. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dashboard_performance_target")
public class PerformanceTargetPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorId;
    private String metric;
    private Integer targetYear;
    private Integer targetMonth;
    private BigDecimal amount;
}
