package io.github.archetom.crm.application.service.template;

/**
 * Type-safe lifecycle for an application use case.
 *
 * @param <T> operation result type
 */
public interface ServiceOperation<T> {

    default void validate() {
    }

    default void prepare() {
    }

    T execute();

    default void onSuccess(T result) {
    }
}
