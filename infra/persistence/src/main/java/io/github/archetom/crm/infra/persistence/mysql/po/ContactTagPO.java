package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_contact_tag")
public class ContactTagPO {
    private Long tenantId;
    private Long contactId;
    private String tagName;
    private String tagKey;
    private LocalDateTime createdTime;
}
