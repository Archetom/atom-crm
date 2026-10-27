package io.github.archetom.crm.domain.repository;

import java.util.List;

/**
 * Framework-neutral repository page result.
 */
public record PageResult<T>(long page, long size, long total, List<T> items) {

    public PageResult {
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException("Invalid page metadata");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }
}
