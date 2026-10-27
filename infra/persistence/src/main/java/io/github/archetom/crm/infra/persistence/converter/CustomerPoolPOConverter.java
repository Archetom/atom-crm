package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.CustomerPool;
import io.github.archetom.crm.domain.entity.CustomerPoolRule;
import io.github.archetom.crm.domain.model.CustomerPoolRuleType;
import io.github.archetom.crm.domain.model.CustomerPoolStatus;
import io.github.archetom.crm.domain.valueobject.CustomerPoolId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolPO;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class CustomerPoolPOConverter {

    public CustomerPoolPO toPO(CustomerPool pool) {
        CustomerPoolPO po = new CustomerPoolPO();
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

    public CustomerPool toDomain(
            CustomerPoolPO po,
            Collection<Long> administratorIds,
            Collection<Long> eligibleMemberIds,
            Collection<Long> eligibleDepartmentIds,
            Collection<io.github.archetom.crm.infra.persistence.mysql.po.CustomerPoolRulePO> rules) {
        return CustomerPool.reconstitute(new CustomerPool.Snapshot(
                new CustomerPoolId(po.getId()),
                new TenantId(po.getTenantId()),
                po.getName(),
                CustomerPoolStatus.valueOf(po.getStatus()),
                administratorIds.stream().map(MemberId::new).collect(java.util.stream.Collectors.toSet()),
                eligibleMemberIds.stream().map(MemberId::new).collect(java.util.stream.Collectors.toSet()),
                eligibleDepartmentIds.stream().map(DepartmentId::new).collect(java.util.stream.Collectors.toSet()),
                po.getDailyClaimLimit(),
                po.getPreviousOwnerCooldownDays(),
                Boolean.TRUE.equals(po.getAutomaticReleaseEnabled()),
                rules.stream().map(value -> new CustomerPoolRule(
                        CustomerPoolRuleType.valueOf(value.getRuleType()), value.getInactiveDays())).toList(),
                po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
    }
}
