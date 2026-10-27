package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("embedded_page")
public class EmbeddedPagePO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long createdByActorId;
    private String objectType;
    private String platform;
    private String name;
    private String description;
    private String urlTemplate;
    private Integer sortOrder;
    private String status;
}
