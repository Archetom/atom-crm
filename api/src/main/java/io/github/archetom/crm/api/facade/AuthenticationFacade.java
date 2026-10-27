package io.github.archetom.crm.api.facade;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.BrowserSessionIssuance;
import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetConfirmRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.api.dto.response.AuthenticationSessionResponse;
import io.github.archetom.crm.api.dto.response.PasswordLoginOptionsResponse;

import java.util.Optional;

/** Public contract boundary for browser password authentication. */
public interface AuthenticationFacade {

    Result<PasswordLoginOptionsResponse> beginPasswordLogin(PasswordLoginOptionsRequest request);

    Result<BrowserSessionIssuance> createBrowserSession(PasswordSessionCreateRequest request);

    /**
     * Reads the session an existing token already stands for.
     *
     * <p>This mints no token and returns none: the caller is presenting one it already holds, so
     * there is no new cookie material to hand back — only the session's public view, whose expiry
     * is the one the presented token already carries.</p>
     */
    Optional<AuthenticationSessionResponse> resumeBrowserSession(String sessionToken);

    Result<Void> requestPasswordReset(PasswordResetRequest request);

    Result<Void> confirmPasswordReset(PasswordResetConfirmRequest request);
}
