package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.PerformanceTarget;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.PerformanceTargetPO;
import org.springframework.stereotype.Component;

/** Typed conversion for dashboard performance target persistence. */
@Component
public class PerformanceTargetPOConverter {

    public PerformanceTarget toDomain(PerformanceTargetPO po) {
        if (po == null) {
            return null;
        }
        try {
            return PerformanceTarget.reconstitute(new PerformanceTarget.Snapshot(
                    new PerformanceTargetId(po.getId()), new TenantId(po.getTenantId()),
                    new MemberId(po.getActorId()), PerformanceTargetMetric.valueOf(po.getMetric()),
                    po.getTargetYear(), po.getTargetMonth(), po.getAmount(), po.getVersion(),
                    po.getCreatedTime(), po.getUpdatedTime()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored performance target is invalid", exception);
        }
    }

    public PerformanceTargetPO toPO(PerformanceTarget target) {
        if (target == null) {
            return null;
        }
        PerformanceTargetPO po = new PerformanceTargetPO();
        po.setId(target.getId() == null ? null : target.getId().getValue());
        po.setTenantId(target.getTenantId().getValue());
        po.setActorId(target.getMemberId().getValue());
        po.setMetric(target.getMetric().name());
        po.setTargetYear(target.getTargetYear());
        po.setTargetMonth(target.getTargetMonth());
        po.setAmount(target.getAmount());
        po.setVersion(target.getVersion());
        po.setCreatedTime(target.getCreatedTime());
        po.setUpdatedTime(target.getUpdatedTime());
        return po;
    }
}
