package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_work_task")
public class WorkTaskPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long createdByActorId;
    private Long currentAssigneeActorId;
    private String originKind;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long originSeriesId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long originSeriesGeneration;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Long originOccurrenceSequence;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime originNominalLocalTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String originZoneId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private Integer originOffsetSeconds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime originResolvedDueTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String businessObjectType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long businessRecordId;
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private String priority;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dueTime;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime firstStartedTime;
    private Long worklistProjectionVersion;
    private Long notificationProjectionVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime cancelledTime;
}
