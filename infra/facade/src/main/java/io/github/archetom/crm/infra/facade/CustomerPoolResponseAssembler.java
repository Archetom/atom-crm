package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.dto.response.CustomerPoolResponse;
import io.github.archetom.crm.api.dto.response.CustomerPoolRuleResponse;
import io.github.archetom.crm.application.vo.CustomerPoolVO;

final class CustomerPoolResponseAssembler {

    private CustomerPoolResponseAssembler() {
    }

    static CustomerPoolResponse toResponse(CustomerPoolVO value) {
        if (value == null) return null;
        return new CustomerPoolResponse().setId(value.id()).setName(value.name())
                .setStatus(value.status()).setAdministratorActorIds(value.administratorActorIds())
                .setEligibleActorIds(value.eligibleActorIds())
                .setEligibleDepartmentIds(value.eligibleDepartmentIds())
                .setDailyClaimLimit(value.dailyClaimLimit())
                .setPreviousOwnerCooldownDays(value.previousOwnerCooldownDays())
                .setAutomaticReleaseEnabled(value.automaticReleaseEnabled())
                .setRules(value.rules().stream().map(rule ->
                        new CustomerPoolRuleResponse(rule.type(), rule.inactiveDays())).toList())
                .setCustomerCount(value.customerCount()).setAdministrator(value.administrator())
                .setVersion(value.version()).setCreatedTime(value.createdTime())
                .setUpdatedTime(value.updatedTime());
    }
}
