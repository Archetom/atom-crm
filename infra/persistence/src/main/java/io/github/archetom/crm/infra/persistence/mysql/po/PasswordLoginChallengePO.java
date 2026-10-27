package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** Hash-only persistence record for one issued, one-use password-login challenge. */
@Data
@TableName("authentication_password_login_challenge")
public class PasswordLoginChallengePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ToString.Exclude
    @TableField("challenge_digest")
    private String challengeDigest;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("created_time")
    private LocalDateTime createdTime;
}
