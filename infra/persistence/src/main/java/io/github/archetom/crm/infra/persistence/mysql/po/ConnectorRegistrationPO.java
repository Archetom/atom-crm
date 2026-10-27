package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_connector_registration")
public class ConnectorRegistrationPO extends BasePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String connectorRef;
    private String status;
    private LocalDateTime retiredTime;
}
