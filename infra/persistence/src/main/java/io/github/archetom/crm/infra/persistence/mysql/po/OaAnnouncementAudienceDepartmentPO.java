package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("oa_announcement_audience_department")
public class OaAnnouncementAudienceDepartmentPO {
    private Long tenantId;
    private Long announcementId;
    private Long departmentId;
}
