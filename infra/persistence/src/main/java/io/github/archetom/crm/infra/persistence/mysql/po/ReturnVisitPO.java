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
@TableName("crm_return_visit")
public class ReturnVisitPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerActorId;
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contractId;
    private String visitNumber;
    private LocalDateTime visitTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String visitForm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String satisfaction;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private String status;
}
