package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.ContactPoolResponse;
import io.github.archetom.crm.api.dto.response.ContactPoolRuleResponse;
import io.github.archetom.crm.application.vo.ContactPoolVO;

final class ContactPoolResponseAssembler {

    private ContactPoolResponseAssembler() {
    }

    static ContactPoolResponse toResponse(ContactPoolVO value) {
        if (value == null) return null;
        return new ContactPoolResponse().setId(value.id()).setName(value.name())
                .setStatus(value.status()).setAdministratorActorIds(value.administratorActorIds())
                .setEligibleActorIds(value.eligibleActorIds())
                .setEligibleDepartmentIds(value.eligibleDepartmentIds())
                .setDailyClaimLimit(value.dailyClaimLimit())
                .setPreviousOwnerCooldownDays(value.previousOwnerCooldownDays())
                .setAutomaticReleaseEnabled(value.automaticReleaseEnabled())
                .setRules(value.rules().stream().map(rule ->
                        new ContactPoolRuleResponse(rule.type(), rule.inactiveDays())).toList())
                .setContactCount(value.contactCount()).setAdministrator(value.administrator())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
