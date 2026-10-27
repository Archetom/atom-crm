package io.github.archetom.crm.application.service;

import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetConfirmRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.application.vo.PasswordLoginOptionsVO;
import io.github.archetom.common.result.Result;

import java.util.Optional;

/** Password authentication, tenant selection, browser-session, and reset use cases. */
public interface AuthenticationService {

    Result<PasswordLoginOptionsVO> beginPasswordLogin(PasswordLoginOptionsRequest request);

    Result<BrowserSessionVO> createBrowserSession(PasswordSessionCreateRequest request);

    Optional<BrowserSessionVO> resumeBrowserSession(String sessionToken);

    Result<Void> requestPasswordReset(PasswordResetRequest request);

    Result<Void> confirmPasswordReset(PasswordResetConfirmRequest request);
}
