package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.api.dto.request.PasswordLoginOptionsRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetConfirmRequest;
import io.github.archetom.crm.api.dto.request.PasswordResetRequest;
import io.github.archetom.crm.api.dto.request.PasswordSessionCreateRequest;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.port.out.authentication.AuthenticationTokenCodec;
import io.github.archetom.crm.application.port.out.authentication.PasswordLoginChallengeStore;
import io.github.archetom.crm.application.port.out.authentication.PasswordResetDelivery;
import io.github.archetom.crm.application.port.out.authentication.PasswordResetTokenStore;
import io.github.archetom.crm.application.security.BrowserSession;
import io.github.archetom.crm.application.security.PasswordLoginChallenge;
import io.github.archetom.crm.application.service.AuthenticationService;
import io.github.archetom.crm.application.service.template.CommandServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.transaction.AfterCommitExecutor;
import io.github.archetom.crm.application.vo.BrowserSessionVO;
import io.github.archetom.crm.application.vo.PasswordLoginOptionsVO;
import io.github.archetom.crm.application.vo.PasswordLoginTenantVO;
import io.github.archetom.crm.domain.entity.OrganizationMembership;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.policy.PasswordPolicy;
import io.github.archetom.crm.domain.repository.OrganizationMembershipRepository;
import io.github.archetom.crm.domain.repository.UserRepository;
import io.github.archetom.crm.domain.service.PasswordVerifier;
import io.github.archetom.crm.domain.service.UserDomainService;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.PasswordHash;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.Username;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import io.github.archetom.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Password authentication owns the narrow transition from a tenant-scoped account to a verified
 * actor. A client can select only an active membership returned after credential verification.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final PasswordVerifier passwordVerifier;
    private final UserDomainService userDomainService;
    private final PasswordPolicy passwordPolicy;
    private final AuthenticationTokenCodec tokenCodec;
    private final PasswordLoginChallengeStore passwordLoginChallengeStore;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final Optional<PasswordResetDelivery> passwordResetDelivery;
    private final CommandServiceTemplate commandTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    @Value("${atom.authentication.session-ttl:PT8H}")
    private Duration sessionTtl;

    @Value("${atom.authentication.login-challenge-ttl:PT5M}")
    private Duration loginChallengeTtl;

    @Value("${atom.authentication.password-reset-ttl:PT15M}")
    private Duration passwordResetTtl;

    @Value("${atom.authentication.password-reset-url:}")
    private String passwordResetUrl;

    @Value("${atom.authentication.password-reset.mail.from:}")
    private String passwordResetFrom;

    @Override
    public Result<PasswordLoginOptionsVO> beginPasswordLogin(PasswordLoginOptionsRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHENTICATION_PASSWORD_LOGIN_OPTIONS,
                new ServiceOperation<>() {
                    private TenantId sourceTenantId;
                    private User user;

                    @Override
                    public void validate() {
                        requireLoginRequest(request);
                        sourceTenantId = new TenantId(request.getTenantId());
                    }

                    @Override
                    public void prepare() {
                        Username loginName = new Username(request.getLoginName());
                        user = userRepository.findByUsername(sourceTenantId, loginName.getValue())
                                .filter(User::isActive)
                                .orElseThrow(AuthenticationServiceImpl::rejectedCredentials);
                        if (!passwordVerifier.matches(request.getPassword(), user.getPasswordHash())) {
                            throw rejectedCredentials();
                        }
                    }

                    @Override
                    public PasswordLoginOptionsVO execute() {
                        List<PasswordLoginTenantVO> tenants = activeTenants(user.getId());
                        boolean sourceTenantAccessible = tenants.stream()
                                .anyMatch(tenant -> Objects.equals(tenant.tenantId(), sourceTenantId.getValue()));
                        if (!sourceTenantAccessible) {
                            throw rejectedCredentials();
                        }
                        Instant expiresAt = Instant.now().plus(validDuration(loginChallengeTtl, "login challenge"));
                        String challenge = tokenCodec.issueLoginChallenge(new PasswordLoginChallenge(
                                user.getId().getValue(), sourceTenantId.getValue(), user.getVersion(), expiresAt));
                        passwordLoginChallengeStore.issue(challenge, expiresAt);
                        return new PasswordLoginOptionsVO(challenge, tenants);
                    }
                });
    }

    @Override
    public Result<BrowserSessionVO> createBrowserSession(PasswordSessionCreateRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHENTICATION_SESSION_CREATE, new ServiceOperation<>() {
            private PasswordLoginChallenge challenge;
            private TenantId targetTenantId;
            private User user;

            @Override
            public void validate() {
                if (request == null || request.getLoginChallenge() == null || request.getLoginChallenge().isBlank()
                        || request.getTenantId() == null || request.getTenantId() <= 0) {
                    throw rejectedCredentials();
                }
                challenge = tokenCodec.readLoginChallenge(request.getLoginChallenge())
                        .orElseThrow(AuthenticationServiceImpl::rejectedCredentials);
                targetTenantId = new TenantId(request.getTenantId());
            }

            @Override
            public void prepare() {
                user = userRepository.findById(new TenantId(challenge.sourceTenantId()),
                                new UserId(challenge.actorId()))
                        .filter(User::isActive)
                        .filter(candidate -> candidate.getVersion() == challenge.userVersion())
                        .orElseThrow(AuthenticationServiceImpl::rejectedCredentials);
                membershipRepository.findByMember(targetTenantId, new MemberId(challenge.actorId()))
                        .filter(OrganizationMembership::isActive)
                        .orElseThrow(AuthenticationServiceImpl::rejectedCredentials);
            }

            @Override
            public BrowserSessionVO execute() {
                if (!passwordLoginChallengeStore.consume(request.getLoginChallenge(), Instant.now())) {
                    throw rejectedCredentials();
                }
                Instant expiresAt = Instant.now().plus(validDuration(sessionTtl, "session"));
                String sessionToken = tokenCodec.issueBrowserSession(new BrowserSession(
                        user.getId().getValue(), challenge.sourceTenantId(), targetTenantId.getValue(),
                        user.getVersion(), expiresAt));
                return new BrowserSessionVO(user.getId().getValue(), targetTenantId.getValue(), expiresAt,
                        sessionToken);
            }
        });
    }

    @Override
    public Optional<BrowserSessionVO> resumeBrowserSession(String sessionToken) {
        return tokenCodec.readBrowserSession(sessionToken)
                .flatMap(this::validateBrowserSession);
    }

    @Override
    public Result<Void> requestPasswordReset(PasswordResetRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHENTICATION_PASSWORD_RESET_REQUEST,
                new ServiceOperation<>() {
                    private TenantId sourceTenantId;
                    private User user;
                    private PasswordResetTokenStore.IssuedPasswordResetToken token;

                    @Override
                    public void validate() {
                        if (passwordResetDelivery.isEmpty() || passwordResetUrl == null || passwordResetUrl.isBlank()
                                || passwordResetFrom == null || passwordResetFrom.isBlank()) {
                            throw resetUnavailable();
                        }
                        requireResetRequest(request);
                        sourceTenantId = new TenantId(request.getTenantId());
                    }

                    @Override
                    public void prepare() {
                        user = userRepository.findByUsername(sourceTenantId, request.getLoginName())
                                .filter(User::isActive)
                                .orElse(null);
                    }

                    @Override
                    public Void execute() {
                        if (user != null) {
                            token = passwordResetTokenStore.issue(sourceTenantId, user.getId(),
                                    Instant.now().plus(validDuration(passwordResetTtl, "password reset")));
                        }
                        return null;
                    }

                    @Override
                    public void onSuccess(Void ignored) {
                        if (token == null || user == null) {
                            return;
                        }
                        PasswordResetDelivery delivery = passwordResetDelivery.orElseThrow();
                        String email = user.getEmailValue();
                        String resetUrl = buildPasswordResetUrl(token.value());
                        afterCommitExecutor.execute("password reset email delivery", () -> delivery.deliver(
                                new PasswordResetDelivery.PasswordResetMessage(email, resetUrl, token.expiresAt())));
                    }
                });
    }

    @Override
    public Result<Void> confirmPasswordReset(PasswordResetConfirmRequest request) {
        return commandTemplate.execute(UseCaseOperation.AUTHENTICATION_PASSWORD_RESET_CONFIRM,
                new ServiceOperation<>() {
                    private PasswordResetTokenStore.ActivePasswordResetToken token;
                    private User user;
                    private PasswordHash newPasswordHash;

                    @Override
                    public void validate() {
                        if (request == null || request.getResetToken() == null || request.getResetToken().isBlank()
                                || request.getNewPassword() == null) {
                            throw rejectedCredentials();
                        }
                        passwordPolicy.validate(request.getNewPassword());
                    }

                    /**
                     * Hashing costs hundreds of milliseconds of CPU and depends on nothing the
                     * transaction reads, so it runs before the transaction opens rather than while
                     * a pooled connection holds the reset-token row lock.
                     */
                    @Override
                    public void prepare() {
                        newPasswordHash = userDomainService.encryptPassword(request.getNewPassword());
                    }

                    @Override
                    public Void execute() {
                        token = passwordResetTokenStore.findActiveForUpdate(request.getResetToken(), Instant.now())
                                .orElseThrow(AuthenticationServiceImpl::rejectedCredentials);
                        user = userRepository.findById(token.sourceTenantId(), token.actorId())
                                .filter(User::isActive)
                                .orElseThrow(AuthenticationServiceImpl::rejectedCredentials);
                        user.changePasswordHash(newPasswordHash);
                        userRepository.save(token.sourceTenantId(), user);
                        passwordResetTokenStore.markConsumed(token.tokenId(), Instant.now());
                        return null;
                    }
                });
    }

    private Optional<BrowserSessionVO> validateBrowserSession(BrowserSession session) {
        if (session.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return userRepository.findById(new TenantId(session.sourceTenantId()), new UserId(session.actorId()))
                .filter(User::isActive)
                .filter(user -> user.getVersion() == session.userVersion())
                .flatMap(user -> membershipRepository.findByMember(new TenantId(session.tenantId()),
                                new MemberId(session.actorId()))
                        .filter(OrganizationMembership::isActive)
                        .map(ignored -> new BrowserSessionVO(user.getId().getValue(), session.tenantId(),
                                session.expiresAt(), null)));
    }

    private List<PasswordLoginTenantVO> activeTenants(UserId actorId) {
        if (actorId == null) {
            throw rejectedCredentials();
        }
        return membershipRepository.findActiveByMember(new MemberId(actorId.getValue())).stream()
                .map(membership -> new PasswordLoginTenantVO(membership.getTenantId().getValue(),
                        membership.getDepartmentId() == null ? null : membership.getDepartmentId().getValue()))
                .toList();
    }

    private static void requireLoginRequest(PasswordLoginOptionsRequest request) {
        if (request == null || request.getTenantId() == null || request.getTenantId() <= 0
                || request.getLoginName() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Password login request is invalid");
        }
    }

    private static void requireResetRequest(PasswordResetRequest request) {
        if (request == null || request.getTenantId() == null || request.getTenantId() <= 0
                || request.getLoginName() == null) {
            throw new IllegalArgumentException("Password reset request is invalid");
        }
        new Username(request.getLoginName());
    }

    private static Duration validDuration(Duration value, String label) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalStateException(label + " duration must be positive");
        }
        return value;
    }

    private String buildPasswordResetUrl(String rawToken) {
        String separator = passwordResetUrl.contains("?") ? "&" : "?";
        return passwordResetUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private static NonRetryableApplicationException rejectedCredentials() {
        return new NonRetryableApplicationException(ApplicationErrorCode.AUTHENTICATION_REQUIRED,
                "Authentication credentials were not accepted");
    }

    private static NonRetryableApplicationException resetUnavailable() {
        return new NonRetryableApplicationException(ApplicationErrorCode.AUTHENTICATION_DELIVERY_UNAVAILABLE,
                "Password reset delivery is unavailable");
    }
}
