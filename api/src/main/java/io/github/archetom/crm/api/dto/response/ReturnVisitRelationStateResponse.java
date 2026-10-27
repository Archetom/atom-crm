package io.github.archetom.crm.api.dto.response;

/**
 * Current caller's projection of an optional return-visit relation.
 *
 * <p>{@link #VISIBLE} includes its ID, {@link #HIDDEN} deliberately withholds it,
 * and {@link #EMPTY} means no relation is stored.</p>
 */
public enum ReturnVisitRelationStateResponse {
    VISIBLE,
    HIDDEN,
    EMPTY
}
