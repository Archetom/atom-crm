package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_lead_address")
public class LeadAddressPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long leadId;
    private String addressKey;
    private String label;
    private String country;
    private String region;
    private String city;
    private String postalCode;
    private String addressLine;
    private BigDecimal longitude;
    private BigDecimal latitude;
    @TableField("is_primary")
    private Boolean primaryAddress;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
