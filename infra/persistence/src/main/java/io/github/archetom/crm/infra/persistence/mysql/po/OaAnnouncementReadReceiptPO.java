package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oa_announcement_read_receipt")
public class OaAnnouncementReadReceiptPO {
    private Long tenantId;
    private Long announcementId;
    private Long actorId;
    private LocalDateTime readTime;
}
