package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_product_image_draft_file")
public class ProductImageDraftFilePO {
    private Long tenantId;
    private Long draftId;
    private String imageType;
    private Long fileId;
    private Integer ordinal;
    private String state;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
