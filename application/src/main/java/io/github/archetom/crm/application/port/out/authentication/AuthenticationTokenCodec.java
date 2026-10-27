package io.github.archetom.crm.application.port.out.authentication;

import io.github.archetom.crm.application.security.BrowserSession;
import io.github.archetom.crm.application.security.PasswordLoginChallenge;

import java.util.Optional;

/** Issues and verifies purpose-separated, signed browser authentication tokens. */
public interface AuthenticationTokenCodec {

    String issueLoginChallenge(PasswordLoginChallenge challenge);

    Optional<PasswordLoginChallenge> readLoginChallenge(String value);

    String issueBrowserSession(BrowserSession session);

    Optional<BrowserSession> readBrowserSession(String value);
}
