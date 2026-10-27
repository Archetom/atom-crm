package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_import_draft")
public class CrmImportDraftPO {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long uploaderActorId;
    private String objectType;
    private LocalDateTime deadline;
    private String state;
    private Integer importedRows;
    @Version private Long version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
