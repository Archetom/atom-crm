package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.NavigationPreferenceUpdateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.reference.NavigationCatalog;
import io.github.archetom.crm.application.security.OrganizationAccessGuard;
import io.github.archetom.crm.application.service.NavigationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.NavigationItemVO;
import io.github.archetom.crm.application.vo.NavigationPreferenceVO;
import io.github.archetom.crm.domain.entity.NavigationPreference;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.repository.NavigationPreferenceRepository;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** Navigation filtering remains presentation-only and cannot add or remove runtime authority. */
@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {

    private final NavigationPreferenceRepository preferenceRepository;
    private final NavigationCatalog navigationCatalog;
    private final OrganizationAccessGuard accessGuard;
    private final CommandServiceTemplate commandTemplate;
    private final QueryServiceTemplate queryTemplate;

    @Override
    public Result<List<NavigationItemVO>> getNavigation(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.NAVIGATION_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.NAVIGATION_READ);
                memberId = new MemberId(caller.actorId());
            }

            @Override
            public List<NavigationItemVO> execute() {
                NavigationPreference preference = preferenceRepository.findByMember(tenantId, memberId).orElse(null);
                return navigationCatalog.visibleTree(caller.authorities(), preference);
            }
        });
    }

    @Override
    public Result<List<NavigationItemVO>> getCatalog(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.NAVIGATION_CATALOG_GET, new ServiceOperation<>() {
            @Override
            public void validate() {
                accessGuard.requireActiveMembership(caller, AuthorityCatalog.AUTHORIZATION_READ);
            }

            @Override
            public List<NavigationItemVO> execute() {
                return navigationCatalog.catalogTree();
            }
        });
    }

    @Override
    public Result<NavigationPreferenceVO> getPreference(AuthenticatedCaller caller) {
        return queryTemplate.execute(UseCaseOperation.NAVIGATION_PREFERENCE_GET, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.NAVIGATION_READ);
                memberId = new MemberId(caller.actorId());
            }

            @Override
            public NavigationPreferenceVO execute() {
                return preferenceRepository.findByMember(tenantId, memberId)
                        .map(preference -> toVO(preference, caller.authorities()))
                        .orElseGet(() -> new NavigationPreferenceVO(List.of(), java.util.Set.of(), null,
                                navigationCatalog.visibleTree(caller.authorities(), null)));
            }
        });
    }

    @Override
    public Result<NavigationPreferenceVO> updatePreference(
            AuthenticatedCaller caller, NavigationPreferenceUpdateRequest request) {
        return commandTemplate.execute(UseCaseOperation.NAVIGATION_PREFERENCE_UPDATE, new ServiceOperation<>() {
            private TenantId tenantId;
            private MemberId memberId;

            @Override
            public void validate() {
                tenantId = accessGuard.requireActiveMembership(caller, AuthorityCatalog.NAVIGATION_WRITE);
                memberId = new MemberId(caller.actorId());
                if (request == null) {
                    throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                            "Navigation preference request must not be empty");
                }
            }

            @Override
            public NavigationPreferenceVO execute() {
                NavigationPreference preference = preferenceRepository.findByMember(tenantId, memberId)
                        .map(existing -> updateExisting(existing, request))
                        .orElseGet(() -> createPreference(request));
                return toVO(preferenceRepository.save(tenantId, preference), caller.authorities());
            }

            private NavigationPreference createPreference(NavigationPreferenceUpdateRequest value) {
                if (value.getVersion() != null) {
                    throw versionConflict();
                }
                return NavigationPreference.create(tenantId, memberId, value.getOrderedCodes(),
                        value.getHiddenCodes(), navigationCatalog.codes());
            }

            private NavigationPreference updateExisting(
                    NavigationPreference existing, NavigationPreferenceUpdateRequest value) {
                if (value.getVersion() == null || !value.getVersion().equals(existing.getVersion())) {
                    throw versionConflict();
                }
                existing.change(value.getOrderedCodes(), value.getHiddenCodes(), navigationCatalog.codes());
                return existing;
            }
        });
    }

    private NavigationPreferenceVO toVO(NavigationPreference preference, Set<String> authorities) {
        return new NavigationPreferenceVO(preference.getOrderedCodes(), preference.getHiddenCodes(),
                preference.getVersion(), navigationCatalog.visibleTree(authorities, null));
    }

    private NonRetryableApplicationException versionConflict() {
        return new NonRetryableApplicationException(ApplicationErrorCode.VERSION_CONFLICT,
                "Navigation preference version is stale");
    }
}
