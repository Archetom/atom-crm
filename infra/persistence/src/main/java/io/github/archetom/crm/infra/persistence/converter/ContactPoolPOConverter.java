package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.ContactPool;
import io.github.archetom.crm.domain.entity.ContactPoolRule;
import io.github.archetom.crm.domain.model.ContactPoolRuleType;
import io.github.archetom.crm.domain.model.ContactPoolStatus;
import io.github.archetom.crm.domain.valueobject.ContactPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolPO;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class ContactPoolPOConverter {

    public ContactPoolPO toPO(ContactPool pool) {
        ContactPoolPO po = new ContactPoolPO();
        po.setId(pool.getId() == null ? null : pool.getId().getValue());
        po.setTenantId(pool.getTenantId().getValue());
        po.setName(pool.getName());
        po.setStatus(pool.getStatus().name());
        po.setDailyClaimLimit(pool.getDailyClaimLimit());
        po.setPreviousOwnerCooldownDays(pool.getPreviousOwnerCooldownDays());
        po.setAutomaticReleaseEnabled(pool.isAutomaticReleaseEnabled());
        po.setVersion(pool.getVersion());
        po.setCreatedTime(pool.getCreatedTime());
        po.setUpdatedTime(pool.getUpdatedTime());
        return po;
    }

    public ContactPool toDomain(
            ContactPoolPO po,
            Collection<Long> administratorIds,
            Collection<Long> eligibleMemberIds,
            Collection<Long> eligibleDepartmentIds,
            Collection<io.github.archetom.crm.infra.persistence.mysql.po.ContactPoolRulePO> rules) {
        return ContactPool.reconstitute(new ContactPool.Snapshot(
                new ContactPoolId(po.getId()),
                new TenantId(po.getTenantId()),
                po.getName(),
                ContactPoolStatus.valueOf(po.getStatus()),
                administratorIds.stream().map(MemberId::new).collect(java.util.stream.Collectors.toSet()),
                eligibleMemberIds.stream().map(MemberId::new).collect(java.util.stream.Collectors.toSet()),
                eligibleDepartmentIds.stream().map(DepartmentId::new).collect(java.util.stream.Collectors.toSet()),
                po.getDailyClaimLimit(),
                po.getPreviousOwnerCooldownDays(),
                Boolean.TRUE.equals(po.getAutomaticReleaseEnabled()),
                rules.stream().map(value -> new ContactPoolRule(
                        ContactPoolRuleType.valueOf(value.getRuleType()), value.getInactiveDays())).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }
}
