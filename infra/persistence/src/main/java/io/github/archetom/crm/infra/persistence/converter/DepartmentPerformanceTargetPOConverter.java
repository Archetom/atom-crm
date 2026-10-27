package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.entity.DepartmentPerformanceTarget;
import io.github.archetom.crm.domain.model.PerformanceTargetMetric;
import io.github.archetom.crm.domain.model.PerformanceTargetPeriod;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.DepartmentPerformanceTargetId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.DepartmentPerformanceTargetPO;
import org.springframework.stereotype.Component;

/** Typed conversion for department performance target persistence. */
@Component
public class DepartmentPerformanceTargetPOConverter {

    public DepartmentPerformanceTarget toDomain(DepartmentPerformanceTargetPO po) {
        if (po == null) {
            return null;
        }
        try {
            return DepartmentPerformanceTarget.reconstitute(new DepartmentPerformanceTarget.Snapshot(
                    new DepartmentPerformanceTargetId(po.getId()), new TenantId(po.getTenantId()),
                    new DepartmentId(po.getDepartmentId()), PerformanceTargetMetric.valueOf(po.getMetric()),
                    po.getTargetYear(), PerformanceTargetPeriod.valueOf(po.getPeriod()), po.getPeriodNumber(),
                    po.getAmount(), po.getVersion(), po.getCreatedTime(), po.getUpdatedTime()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored department performance target is invalid", exception);
        }
    }

    public DepartmentPerformanceTargetPO toPO(DepartmentPerformanceTarget target) {
        if (target == null) {
            return null;
        }
        DepartmentPerformanceTargetPO po = new DepartmentPerformanceTargetPO();
        po.setId(target.getId() == null ? null : target.getId().getValue());
        po.setTenantId(target.getTenantId().getValue());
        po.setDepartmentId(target.getDepartmentId().getValue());
        po.setMetric(target.getMetric().name());
        po.setTargetYear(target.getTargetYear());
        po.setPeriod(target.getPeriod().name());
        po.setPeriodNumber(target.getPeriodNumber());
        po.setAmount(target.getAmount());
        po.setVersion(target.getVersion());
        po.setCreatedTime(target.getCreatedTime());
        po.setUpdatedTime(target.getUpdatedTime());
        return po;
    }
}
