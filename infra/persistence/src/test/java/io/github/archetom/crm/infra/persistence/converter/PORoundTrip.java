package io.github.archetom.crm.infra.persistence.converter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fills a persistent object with per-field distinct values and checks that a round trip preserves
 * every one of them.
 *
 * <p>The converters this supports rebuild a domain object through a wide positional constructor —
 * the widest carries 28 components, ten of them consecutive {@code String}s. Swapping two arguments
 * of the same type there compiles, passes every hand-written test that happens to assert other
 * fields, and silently moves one customer's bank account onto another field. Asserting a handful of
 * fields cannot catch it; giving every field a value unique to that field can.</p>
 *
 * <p>The check runs {@code PO -> domain -> PO}. A transposition inside the positional constructor
 * lands the value on the wrong domain component, and the return leg — which assigns by name — puts
 * it back on the wrong column, so the comparison fails. The return leg cannot hide the swap because
 * it never uses argument position.</p>
 */
final class PORoundTrip {

    private PORoundTrip() {
    }

    /**
     * Assigns every field a value derived from its own position, so no two fields of the same type
     * ever hold the same value.
     *
     * @param overrides values for fields the converter constrains, such as a column parsed as an
     *                  enum, which cannot take an arbitrary string
     */
    static <T> T filled(T target, Map<String, Object> overrides) {
        int position = 0;
        List<String> unsupported = new ArrayList<>();
        for (Field field : declaredFields(target.getClass())) {
            position++;
            try {
                if (overrides.containsKey(field.getName())) {
                    field.set(target, overrides.get(field.getName()));
                    continue;
                }
                Object value = distinctValue(field, position);
                if (value == null) {
                    unsupported.add(field.getName() + " (" + field.getType().getSimpleName() + ")");
                    continue;
                }
                field.set(target, value);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("cannot populate " + field.getName(), exception);
            }
        }
        assertTrue(unsupported.isEmpty(),
                () -> "PORoundTrip cannot produce a distinct value for: " + unsupported
                        + "; extend distinctValue or pass an override so the field is still checked");
        return target;
    }

    /** Compares every field, so a value that lands on the wrong one is reported by name. */
    static void assertEveryFieldSurvives(Object original, Object restored, Set<String> notCarried) {
        List<String> differences = new ArrayList<>();
        for (Field field : declaredFields(original.getClass())) {
            if (notCarried.contains(field.getName())) {
                continue;
            }
            try {
                Object before = field.get(original);
                Object after = field.get(restored);
                if (!Objects.equals(before, after)) {
                    differences.add(field.getName() + ": " + before + " -> " + after);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("cannot read " + field.getName(), exception);
            }
        }
        assertTrue(differences.isEmpty(),
                () -> original.getClass().getSimpleName()
                        + " lost or misplaced values on a round trip:\n  "
                        + String.join("\n  ", differences));
    }

    static Map<String, Object> overrides(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            values.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return values;
    }

    private static Object distinctValue(Field field, int position) {
        Class<?> type = field.getType();
        if (type == String.class) {
            return field.getName() + "-" + position;
        }
        if (type == Long.class || type == long.class) {
            return 1_000L + position;
        }
        if (type == Integer.class || type == int.class) {
            return position;
        }
        if (type == Boolean.class || type == boolean.class) {
            return position % 2 == 0;
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(position + ".00");
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 1, 1).plusDays(position);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(position);
        }
        if (type == LocalTime.class) {
            return LocalTime.of(0, 0).plusMinutes(position);
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[position % type.getEnumConstants().length];
        }
        return null;
    }

    private static List<Field> declaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }
}
