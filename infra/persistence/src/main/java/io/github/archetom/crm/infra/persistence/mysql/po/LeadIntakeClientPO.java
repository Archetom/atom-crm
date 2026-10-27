package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_lead_intake_client")
public class LeadIntakeClientPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerActorId;
    private String keyId;
    private String apiKeyHash;
    @TableField(value = "is_active", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean active;
}
