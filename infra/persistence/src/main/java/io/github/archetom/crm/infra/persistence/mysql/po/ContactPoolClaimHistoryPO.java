package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact_pool_claim_history")
public class ContactPoolClaimHistoryPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long poolId;
    private Long contactId;
    private Long actorId;
    @TableField("is_assigned")
    private Boolean assigned;
    private LocalDateTime claimedTime;
}
