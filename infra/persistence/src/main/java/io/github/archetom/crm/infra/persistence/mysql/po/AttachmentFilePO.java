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
@TableName("crm_attachment_file")
public class AttachmentFilePO extends BasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadOrigin;
    private Long uploaderActorId;
    private Long publicMarketingDraftId;
    private String displayName;
    private String declaredMediaType;
    private Long expectedBytes;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private byte[] expectedSha256;
    private String byteState;
    private String inspectionState;
    private String publicationState;
    private Long contentVersion;
    private Long actualBytes;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mediaType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String controlledReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime availableTime;
}
