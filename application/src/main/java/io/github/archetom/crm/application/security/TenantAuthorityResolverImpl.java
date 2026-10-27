package io.github.archetom.crm.application.security;

import io.github.archetom.crm.domain.entity.RoleGrant;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Relational source-of-truth resolver used by authentication adapters. */
@Component
@RequiredArgsConstructor
public class TenantAuthorityResolverImpl implements TenantAuthorityResolver {

    private final TenantAuthorizationEvaluator evaluator;
    private final TenantAuthorityCache cache;

    @Override
    public Set<String> resolve(long actorId, long tenantId) {
        TenantId verifiedTenantId = new TenantId(tenantId);
        MemberId verifiedMemberId = new MemberId(actorId);
        TenantAuthorityCache.Lookup lookup = cache.find(verifiedTenantId, verifiedMemberId);
        if (lookup.hit()) {
            return lookup.authorities();
        }

        TenantAuthorizationEvaluator.Evaluation evaluation = evaluator.evaluate(verifiedTenantId, verifiedMemberId);
        Set<String> authorities = evaluation.activeMember()
                ? evaluation.effectiveGrants().stream()
                .map(RoleGrant::authorityCode)
                .map(code -> code.getValue())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                : Set.of();
        Set<String> immutable = Set.copyOf(authorities);
        cache.putIfCurrent(verifiedTenantId, verifiedMemberId, lookup.revision(), immutable);
        return immutable;
    }
}
