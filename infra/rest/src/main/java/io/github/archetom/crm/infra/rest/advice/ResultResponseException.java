package io.github.archetom.crm.infra.rest.advice;

import io.github.archetom.common.result.Result;

import java.util.Objects;

/**
 * Bridges an already-classified application result to REST without replacing its operation
 * scene in the public error code.
 */
public final class ResultResponseException extends RuntimeException {

    private final Result<?> result;

    public ResultResponseException(Result<?> result) {
        super("REST result response");
        this.result = Objects.requireNonNull(result, "result must not be null");
        if (result.isSuccess()) {
            throw new IllegalArgumentException("result response exception requires a failure");
        }
    }

    public Result<?> result() {
        return result;
    }
}
