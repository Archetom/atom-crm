package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Deployment-global counter state only; it intentionally records neither tenant nor query data. */
@Data
@TableName("crm_enterprise_data_source_quota")
public class EnterpriseDataSourceQuotaPO {
    @TableId
    private String sourceKey;
    private LocalDateTime windowStartedAt;
    private Integer requestCount;
    private Long version;
    private LocalDateTime updatedTime;
}
