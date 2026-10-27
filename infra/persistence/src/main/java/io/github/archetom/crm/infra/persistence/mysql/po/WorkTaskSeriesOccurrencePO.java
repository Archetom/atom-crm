package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_work_task_series_occurrence")
public class WorkTaskSeriesOccurrencePO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long workTaskSeriesId;
    private Long seriesGeneration;
    private Long occurrenceSequence;
    private String status;
    private LocalDateTime nominalLocalTime;
    private String zoneId;
    private Integer resolvedOffsetSeconds;
    private LocalDateTime resolvedDueTime;
    private Long generatedWorkTaskId;
    private String safeReason;
    private LocalDateTime createdTime;
}
