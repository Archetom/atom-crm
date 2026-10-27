package io.github.archetom.crm.domain.service;

import io.github.archetom.crm.domain.valueobject.PasswordHash;

/** Verifies a candidate password against an existing one-way hash. */
public interface PasswordVerifier {

    boolean matches(String plainText, PasswordHash passwordHash);
}
