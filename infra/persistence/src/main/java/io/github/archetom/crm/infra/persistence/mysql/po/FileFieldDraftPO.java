package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_file_field_draft")
public class FileFieldDraftPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long uploaderActorId;
    private String objectType;
    private String targetType;
    private Long targetId;
    private Long stageWorkflowRecordId;
    private Long oaFormId;
    private Long oaFormVersion;
    private byte[] purposeDigest;
    private LocalDateTime deadline;
    private String state;
    @Version private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
