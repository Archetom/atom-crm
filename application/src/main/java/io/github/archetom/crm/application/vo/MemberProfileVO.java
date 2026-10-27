package io.github.archetom.crm.application.vo;

import lombok.Value;

/** Safe local profile projection for organization member lists. */
@Value
public class MemberProfileVO {
    Long actorId;
    String displayName;
    String email;
    String maskedPhoneNumber;
    boolean active;
}
