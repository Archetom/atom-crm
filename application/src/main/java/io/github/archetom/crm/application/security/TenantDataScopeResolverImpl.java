package io.github.archetom.crm.application.security;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.valueobject.AuthorityCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Relational implementation that never accepts a target actor or tenant from normal request data. */
@Component
@RequiredArgsConstructor
public class TenantDataScopeResolverImpl implements TenantDataScopeResolver {

    private final TenantAuthorizationEvaluator evaluator;

    @Override
    public Optional<DataScope> resolve(AuthenticatedCaller caller, String authority) {
        if (caller == null || authority == null || !caller.hasAuthority(authority)) {
            return Optional.empty();
        }
        AuthorityCode code;
        try {
            code = new AuthorityCode(authority);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (AuthorityCatalog.find(code).filter(definition -> definition.tenantAssignable()
                && definition.supportsDataScope()).isEmpty()) {
            return Optional.empty();
        }
        TenantAuthorizationEvaluator.Evaluation evaluation = evaluator.evaluate(
                new TenantId(caller.tenantId()), new MemberId(caller.actorId()));
        if (!evaluation.activeMember()) {
            return Optional.empty();
        }
        return evaluation.effectiveGrants().stream()
                .filter(grant -> grant.authorityCode().equals(code))
                .map(RoleGrant::dataScope)
                .findFirst();
    }
}
