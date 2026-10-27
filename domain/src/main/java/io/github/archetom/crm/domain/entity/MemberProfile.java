package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OrganizationDomainException;
import io.github.archetom.crm.domain.model.MemberProfileStatus;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import lombok.Getter;

import java.time.LocalDateTime;

/** Global profile bound to a verified actor rather than to a tenant. */
@Getter
public class MemberProfile extends AggregateRoot<MemberId> {

    private MemberId id;
    private String displayName;
    private Email email;
    private PhoneNumber phoneNumber;
    private MemberProfileStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private MemberProfile() {
    }

    public record Snapshot(MemberId id, String displayName, Email email, PhoneNumber phoneNumber,
                           MemberProfileStatus status, Long version, LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
    }

    public static MemberProfile register(MemberId id, String displayName, Email email, PhoneNumber phoneNumber) {
        MemberProfile profile = new MemberProfile();
        profile.id = requireId(id);
        profile.displayName = requireDisplayName(displayName);
        profile.email = email;
        profile.phoneNumber = phoneNumber;
        profile.status = MemberProfileStatus.ACTIVE;
        LocalDateTime now = LocalDateTime.now();
        profile.createdTime = now;
        profile.updatedTime = now;
        return profile;
    }

    public static MemberProfile reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OrganizationDomainException("member profile snapshot must not be empty");
        }
        MemberProfile profile = new MemberProfile();
        profile.id = requireId(snapshot.id());
        profile.displayName = requireDisplayName(snapshot.displayName());
        profile.email = snapshot.email();
        profile.phoneNumber = snapshot.phoneNumber();
        profile.status = snapshot.status() == null ? MemberProfileStatus.INACTIVE : snapshot.status();
        profile.restoreVersion(snapshot.version());
        profile.createdTime = snapshot.createdTime();
        profile.updatedTime = snapshot.updatedTime();
        return profile;
    }

    public void updateContact(String displayName, Email email, PhoneNumber phoneNumber) {
        ensureActive();
        this.displayName = requireDisplayName(displayName);
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.updatedTime = LocalDateTime.now();
    }

    public void deactivate() {
        status = MemberProfileStatus.INACTIVE;
        updatedTime = LocalDateTime.now();
    }

    /** Restores an existing local profile when its linked tenant user is reactivated. */
    public void activate() {
        status = MemberProfileStatus.ACTIVE;
        updatedTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == MemberProfileStatus.ACTIVE;
    }

    public void onPersisted(Long version, LocalDateTime createdTime, LocalDateTime updatedTime) {
        restoreVersion(version);
        if (createdTime != null) {
            this.createdTime = createdTime;
        }
        if (updatedTime != null) {
            this.updatedTime = updatedTime;
        }
    }

    private void ensureActive() {
        if (!isActive()) {
            throw new OrganizationDomainException("inactive member profiles cannot be updated");
        }
    }

    private static MemberId requireId(MemberId id) {
        if (id == null) {
            throw new OrganizationDomainException("member ID must not be empty");
        }
        return id;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 100) {
            throw new OrganizationDomainException("member display name is invalid");
        }
        return displayName.trim();
    }
}
