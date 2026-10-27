package io.github.archetom.crm.infra.rest.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/** Issues host-only session and CSRF cookies without exposing the session value to JavaScript. */
@Component
public class AuthenticationCookieService {

    public static final String SESSION_COOKIE = "atom_crm_session";
    public static final String CSRF_COOKIE = "atom_crm_csrf";
    public static final String CSRF_HEADER = "X-Atom-CSRF";
    public static final String SESSION_REQUEST_ATTRIBUTE =
            AuthenticationCookieService.class.getName() + ".authenticated";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean secure;

    public AuthenticationCookieService(
            @Value("${atom.authentication.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    public void establish(HttpServletResponse response, String sessionToken, Duration maxAge) {
        write(response, SESSION_COOKIE, sessionToken, true, maxAge);
        write(response, CSRF_COOKIE, randomCsrfToken(), false, maxAge);
    }

    public void ensureCsrfCookie(HttpServletRequest request, HttpServletResponse response, Duration maxAge) {
        if (cookie(request, CSRF_COOKIE).isEmpty()) {
            write(response, CSRF_COOKIE, randomCsrfToken(), false, maxAge);
        }
    }

    public void clear(HttpServletResponse response) {
        write(response, SESSION_COOKIE, "", true, Duration.ZERO);
        write(response, CSRF_COOKIE, "", false, Duration.ZERO);
    }

    public Optional<String> sessionToken(HttpServletRequest request) {
        return cookie(request, SESSION_COOKIE);
    }

    public Optional<String> csrfToken(HttpServletRequest request) {
        return cookie(request, CSRF_COOKIE);
    }

    private Optional<String> cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private void write(HttpServletResponse response, String name, String value, boolean httpOnly, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String randomCsrfToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
