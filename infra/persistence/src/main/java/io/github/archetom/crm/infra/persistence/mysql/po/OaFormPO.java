package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_form_definition")
public class OaFormPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long createdByActorId;
    private String code;
    private String normalizedCode;
    private String name;
    private String description;
    private Long approvalDefinitionId;
    private String status;
}
