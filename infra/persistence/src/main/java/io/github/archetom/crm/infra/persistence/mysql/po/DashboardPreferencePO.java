package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** Relational representation of current-member dashboard defaults. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dashboard_preference")
public class DashboardPreferencePO extends BasePO {

    private Long actorId;
    private String timeRange;
    private LocalDate customStartDate;
    private LocalDate customEndDate;
    private String dataScope;
    private String hiddenModules;
}
