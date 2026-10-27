package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("worklist_acknowledgement")
public class WorklistAcknowledgementPO extends BasePO {
    private Long actorId;
    private String kind;
    private Long sourceId;
    private Long sourceVersion;
    private LocalDateTime dueTime;
}
