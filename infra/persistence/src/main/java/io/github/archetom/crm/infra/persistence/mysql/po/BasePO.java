package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author hanfeng
 */
@Data
public class BasePO implements Serializable {
    @Serial
    private static final long serialVersionUID = -7330573630298592450L;

    /**
     * tenant, SaaS tenant
     */
    @TableField(fill = FieldFill.INSERT, value = "tenant_id")
    private Long tenantId;

    /**
     * created time
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     *
     */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     *,
     */
    @Version
    @TableField("version")
    private Long version = 0L;
}
