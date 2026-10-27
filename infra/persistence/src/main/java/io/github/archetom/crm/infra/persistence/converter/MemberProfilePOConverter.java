package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.MemberProfile;
import io.github.archetom.crm.domain.model.MemberProfileStatus;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.infra.persistence.mysql.po.MemberProfilePO;
import org.springframework.stereotype.Component;

/** Explicit global profile conversion without a tenant field. */
@Component
public class MemberProfilePOConverter {

    public MemberProfile toDomain(MemberProfilePO po) {
        if (po == null) return null;
        return MemberProfile.reconstitute(new MemberProfile.Snapshot(
                new MemberId(po.getActorId()), po.getDisplayName(), optionalEmail(po.getEmail()),
                optionalPhone(po.getPhoneNumber()), MemberProfileStatus.valueOf(po.getStatus()),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }

    public MemberProfilePO toPO(MemberProfile profile) {
        MemberProfilePO po = new MemberProfilePO();
        po.setActorId(profile.getId().getValue());
        po.setDisplayName(profile.getDisplayName());
        po.setEmail(profile.getEmail() == null ? null : profile.getEmail().getValue());
        po.setPhoneNumber(profile.getPhoneNumber() == null ? null : profile.getPhoneNumber().getValue());
        po.setStatus(profile.getStatus().name());
        po.setVersion(profile.getVersion());
        po.setCreatedTime(profile.getCreatedTime());
        po.setUpdatedTime(profile.getUpdatedTime());
        return po;
    }

    private Email optionalEmail(String value) { return value == null ? null : new Email(value); }
    private PhoneNumber optionalPhone(String value) { return value == null ? null : new PhoneNumber(value); }
}
