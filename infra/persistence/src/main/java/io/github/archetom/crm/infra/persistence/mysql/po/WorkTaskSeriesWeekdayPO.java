package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("crm_work_task_series_weekday")
public class WorkTaskSeriesWeekdayPO {
    private Long tenantId;
    private Long workTaskSeriesId;
    private Integer weekday;
}
