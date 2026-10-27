package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Value-free selected fact metadata attached to one B23 adoption header. */
@Data
@TableName("crm_enterprise_data_adoption_selection")
public class EnterpriseDataAdoptionSelectionPO {
    private Long adoptionId;
    private Integer lineNo;
    private String factCode;
    private String targetLocator;
    private Boolean changedValue;
}
