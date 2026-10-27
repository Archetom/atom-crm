package io.github.archetom.crm.domain.valueobject;

import java.io.Serializable;

/** Marker contract for immutable domain values with value-based equality semantics. */
public interface ValueObject<T> extends Serializable {

    /** Return whether another instance represents the same domain value. */
    boolean sameValueAs(T other);
}
