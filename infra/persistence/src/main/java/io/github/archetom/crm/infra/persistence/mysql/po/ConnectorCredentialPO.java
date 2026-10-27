package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("crm_connector_credential")
public class ConnectorCredentialPO {
    private Long tenantId;
    private Long connectorId;
    private String connectorRef;
    private String keyId;
    @ToString.Exclude
    private String secretVerifier;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime revokedTime;
}
