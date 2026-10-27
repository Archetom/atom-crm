package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_marketing_form")
public class MarketingFormPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long createdByActorId;
    private Long ownerActorId;
    private String targetType;
    private String publicKey;
    private String name;
    private String title;
    private String description;
    private String submitButtonText;
    private String successMessage;
    private String status;
}
