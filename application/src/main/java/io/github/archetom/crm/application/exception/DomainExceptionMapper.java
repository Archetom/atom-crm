package io.github.archetom.crm.application.exception;

import io.github.archetom.crm.domain.exception.DomainException;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;

/**
 * Maps framework-neutral domain failures at the application boundary.
 */
public final class DomainExceptionMapper {

    private DomainExceptionMapper() {
    }

    public static ApplicationErrorCode toApplicationCode(DomainException exception) {
        return switch (exception.getError()) {
            case RULE_VIOLATION -> ApplicationErrorCode.DOMAIN_RULE_VIOLATION;
            case NOT_FOUND -> ApplicationErrorCode.RESOURCE_NOT_FOUND;
            case ALREADY_EXISTS -> ApplicationErrorCode.RESOURCE_ALREADY_EXISTS;
            case VERSION_CONFLICT -> ApplicationErrorCode.VERSION_CONFLICT;
        };
    }
}
