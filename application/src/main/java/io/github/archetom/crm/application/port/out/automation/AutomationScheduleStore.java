package io.github.archetom.crm.application.port.out.automation;

import io.github.archetom.crm.application.automation.AutomationExecutionContext;
import io.github.archetom.crm.application.automation.AutomationClaimResult;
import io.github.archetom.crm.application.automation.AutomationKind;
import io.github.archetom.crm.application.automation.AutomationMaterialization;
import io.github.archetom.crm.application.automation.AutomationOutcome;
import io.github.archetom.crm.application.automation.AutomationPolicy;
import io.github.archetom.crm.application.automation.AutomationRevisionReconciliation;
import io.github.archetom.crm.application.automation.AutomationRunTransition;
import io.github.archetom.crm.application.automation.AutomationSchedulingReadiness;
import io.github.archetom.crm.application.automation.AutomationSchedulingSnapshot;
import io.github.archetom.crm.domain.valueobject.TenantId;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Durable scheduled-automation control plane. */
public interface AutomationScheduleStore {
    /**
     * Takes the schedule row before a pool-root mutation. A missing row is only
     * created when that mutation can make the derived schedule active.
     */
    void preparePoolConfiguration(TenantId tenantId, AutomationKind kind,
                                  boolean createIfMissing, AutomationPolicy policy);

    List<AutomationRunTransition> synchronize(TenantId tenantId, AutomationKind kind,
                                               boolean active, AutomationPolicy policy);

    /**
     * Settles a schedule from inside its currently leased effect without cancelling
     * that exact run before the pump can record its success.
     */
    List<AutomationRunTransition> synchronizeCurrentRun(AutomationExecutionContext context,
                                                          boolean active, AutomationPolicy policy);

    AutomationRevisionReconciliation reconcileCadenceRevision(AutomationPolicy policy);

    AutomationSchedulingReadiness schedulingReadiness(AutomationPolicy policy);

    /** Database UTC sampled after the caller has acquired its business locks. */
    LocalDateTime currentUtc();

    default Optional<AutomationMaterialization> materializeOne(AutomationPolicy policy) {
        return materializeOne(policy, EnumSet.allOf(AutomationKind.class));
    }

    /** Select only kinds whose handler is currently able to accept a new run. */
    Optional<AutomationMaterialization> materializeOne(AutomationPolicy policy, Set<AutomationKind> eligibleKinds);

    default AutomationClaimResult claimOne(AutomationPolicy policy, UUID leaseOwner) {
        return claimOne(policy, leaseOwner, EnumSet.allOf(AutomationKind.class));
    }

    /** Claim only kinds whose handler is currently able to execute an effect. */
    AutomationClaimResult claimOne(AutomationPolicy policy, UUID leaseOwner, Set<AutomationKind> eligibleKinds);

    /** Locks and verifies the schedule/run fence in the current business transaction. */
    LocalDateTime requireCurrent(AutomationExecutionContext context);

    AutomationRunTransition succeed(AutomationExecutionContext context, AutomationOutcome outcome);

    AutomationRunTransition recover(AutomationExecutionContext context, AutomationPolicy policy);

    AutomationSchedulingSnapshot schedulingSnapshot();

    int purgeTerminal(AutomationPolicy policy);
}
