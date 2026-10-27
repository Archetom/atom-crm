package io.github.archetom.crm.infra.persistence.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/** Applies only aggregate-child inserts, updates, and deletes that changed. */
public final class ChildCollectionSynchronizer {

    private ChildCollectionSynchronizer() {
    }

    public static <K, T> void synchronize(
            List<T> existing,
            List<T> desired,
            Function<T, K> key,
            BiPredicate<T, T> unchanged,
            Consumer<T> insert,
            Consumer<T> delete,
            BiConsumer<T, T> update) {
        Map<K, T> existingByKey = index(existing, key);
        Map<K, T> desiredByKey = index(desired, key);
        existingByKey.forEach((valueKey, value) -> {
            if (!desiredByKey.containsKey(valueKey)) {
                delete.accept(value);
            }
        });
        desiredByKey.forEach((valueKey, value) -> {
            T stored = existingByKey.get(valueKey);
            if (stored == null) {
                insert.accept(value);
            } else if (!unchanged.test(stored, value)) {
                update.accept(stored, value);
            }
        });
    }

    private static <K, T> Map<K, T> index(List<T> values, Function<T, K> key) {
        Map<K, T> indexed = new LinkedHashMap<>();
        if (values != null) {
            for (T value : values) {
                K valueKey = key.apply(value);
                if (valueKey == null || indexed.putIfAbsent(valueKey, value) != null) {
                    throw new IllegalStateException("Aggregate child keys must be present and unique");
                }
            }
        }
        return indexed;
    }
}
