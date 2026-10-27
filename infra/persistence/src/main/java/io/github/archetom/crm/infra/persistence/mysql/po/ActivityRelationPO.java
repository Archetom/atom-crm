package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_activity_relation")
public class ActivityRelationPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long activityId;
    private String objectType;
    private Long recordId;
    private LocalDateTime createdTime;
}
