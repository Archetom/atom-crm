package io.github.archetom.crm.application.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** Application result for User use cases; sensitive fields remain excluded. */
@Data
@Accessors(chain = true)
public class UserVO {

    /**
     * user ID
     */
    private Long id;

    /**
     * username
     */
    private String username;

    /**
     * email
     */
    private String email;

    /**
     * masked phone number
     */
    private String maskedPhoneNumber;

    /**
     * real name
     */
    private String realName;

    /**
     * status
     */
    private String status;

    /**
     * status
     */
    private String statusName;

    /**
     * whether active
     */
    private Boolean active;

    /**
     * tenant ID
     */
    private Long tenantId;

    /**
     * created time
     */
    private LocalDateTime createdTime;

    /**
     * updated time
     */
    private LocalDateTime updatedTime;
}
