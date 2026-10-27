package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Relational persistence representation of the User aggregate. */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_user")
public class UserPO extends BasePO {

    /**
     * user ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * username
     */
    @TableField("username")
    private String username;

    /**
     * email
     */
    @TableField("email")
    private String email;

    /**
     * phone number
     */
    @TableField("phone_number")
    private String phoneNumber;

    /** One-way password hash stored in the legacy {@code password} column. */
    @TableField("password")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String passwordHash;

    /**
     * real name
     */
    @TableField("real_name")
    private String realName;

    /**
     * status
     */
    @TableField("status")
    private String status;

    /**
     * external system ID
     */
    @TableField("external_id")
    private String externalId;

    /** Whether this record originated from an external system. */
    @TableField("is_external_user")
    private Boolean externalUser;

    /** Whether domain deletion rules classify this user as an administrator. */
    @TableField("is_admin")
    private Boolean admin;
}
