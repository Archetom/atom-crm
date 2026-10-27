package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** Hash-only persistence record for a single-use password reset capability. */
@Data
@TableName("authentication_password_reset_token")
public class PasswordResetTokenPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_tenant_id")
    private Long sourceTenantId;

    @TableField("actor_id")
    private Long actorId;

    @ToString.Exclude
    @TableField("token_digest")
    private String tokenDigest;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("consumed_at")
    private LocalDateTime consumedAt;

    @TableField("created_time")
    private LocalDateTime createdTime;
}
