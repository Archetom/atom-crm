package io.github.archetom.crm.infra.persistence.mysql.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

/** Relational representation of a global member profile. */
@Data
@TableName("organization_member_profile")
public class MemberProfilePO {

    @TableId(value = "actor_id", type = IdType.INPUT)
    private Long actorId;
    private String displayName;
    private String email;
    private String phoneNumber;
    private String status;
    @Version
    private Long version;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
