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
@TableName("oa_request")
public class OaRequestPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long formId;
    private Long formVersion;
    private String formCode;
    private String requestTitle;
    private Long applicantActorId;
    private Long applicantDepartmentId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long approvalInstanceId;
    private String objectType;
    private String idempotencyKey;
    private String submissionDigest;
    private LocalDateTime submittedTime;
}
