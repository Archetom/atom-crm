package io.github.archetom.crm.infra.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.BrowserSessionIssuance;
import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetConfirmRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.api.dto.response.AuthenticationSessionResponse;
import io.github.archetom.crm.api.dto.response.PasswordLoginOptionsResponse;
import io.github.archetom.crm.api.dto.response.PasswordLoginTenantResponse;
import io.github.archetom.crm.api.facade.AuthenticationFacade;
import io.github.archetom.crm.application.service.AuthenticationService;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.shared.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationFacadeImpl implements AuthenticationFacade {

    private final AuthenticationService service;

    @Override
    public Result<PasswordLoginOptionsResponse> beginPasswordLogin(PasswordLoginOptionsRequest request) {
        return ResultUtil.map(service.beginPasswordLogin(request), options ->
                new PasswordLoginOptionsResponse()
                        .setLoginChallenge(options.loginChallenge())
                        .setTenants(options.tenants().stream()
                                .map(tenant -> new PasswordLoginTenantResponse()
                                        .setTenantId(tenant.tenantId())
                                        .setDepartmentId(tenant.departmentId()))
                                .toList()));
    }

    @Override
    public Result<BrowserSessionIssuance> createBrowserSession(PasswordSessionCreateRequest request) {
        return ResultUtil.map(service.createBrowserSession(request), AuthenticationFacadeImpl::issuance);
    }

    @Override
    public Optional<AuthenticationSessionResponse> resumeBrowserSession(String sessionToken) {
        return service.resumeBrowserSession(sessionToken).map(AuthenticationFacadeImpl::sessionResponse);
    }

    @Override
    public Result<Void> requestPasswordReset(PasswordResetRequest request) {
        return service.requestPasswordReset(request);
    }

    @Override
    public Result<Void> confirmPasswordReset(PasswordResetConfirmRequest request) {
        return service.confirmPasswordReset(request);
    }

    /** Only a freshly created session carries a token; resuming one presents no new cookie material. */
    static BrowserSessionIssuance issuance(BrowserSessionVO session) {
        return new BrowserSessionIssuance(
                sessionResponse(session), session.token(), session.expiresAt());
    }

    static AuthenticationSessionResponse sessionResponse(BrowserSessionVO session) {
        return new AuthenticationSessionResponse()
                .setActorId(session.actorId())
                .setTenantId(session.tenantId())
                .setExpiresAt(session.expiresAt());
    }
}
