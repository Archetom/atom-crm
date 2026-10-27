package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_customer_pool_eligible_department")
public class CustomerPoolEligibleDepartmentPO {
    private Long tenantId;
    private Long poolId;
    private Long departmentId;
    private LocalDateTime createdTime;
}
