package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_work_task_series")
public class WorkTaskSeriesPO extends BasePO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long createdByActorId;
    private Long currentAssigneeActorId;
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String description;
    private String priority;
    private String recurrenceFrequency;
    private Integer recurrenceInterval;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer monthDay;
    private Boolean lastDayOfMonth;
    private String zoneId;
    private LocalDate startLocalDate;
    private LocalTime localTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDate endLocalDate;
    private String status;
    private Long generation;
    private Long nextOccurrenceSequence;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime nextNominalLocalTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime nextDueAt;
    private Long occurrenceCount;
    private Long skippedCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String lastSafeReason;
}
