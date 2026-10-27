package io.github.archetom.crm.domain.service;

/**
 * Domain port for one-way password hashing.
 */
public interface PasswordHasher {

    String hash(String plainText);
}
