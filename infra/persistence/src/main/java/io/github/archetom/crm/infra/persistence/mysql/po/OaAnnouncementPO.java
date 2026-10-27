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
@TableName("oa_announcement")
public class OaAnnouncementPO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long createdByActorId;
    private String title;
    private String body;
    private Boolean tenantAudience;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime scheduledPublishTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long scheduledByActorId;
    private Long publicationGeneration;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastPublicationSafeReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishedTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime withdrawnTime;
}
