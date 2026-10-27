package io.github.archetom.crm.infra.facade;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value must reach the response field that carries its name.
 *
 * <p>This module is the only place where an internal value object becomes a public response, and it
 * does that with about 1,250 hand-written {@code .setX(value.x())} calls. Every one of them is a
 * chance to write {@code .setTelephone(value.phoneNumber())}: both sides are {@code String}, it
 * compiles, and the response still looks well-formed — the wrong number is simply published under
 * the wrong field. The module had no tests at all, so nothing anywhere noticed.</p>
 *
 * <p>Rather than assert field lists by hand for 69 files, this builds each value object with a
 * value unique to each of its components, runs the real mapper, and checks that a response property
 * sharing a component's name carries that component's value. A transposition puts one field's value
 * on another and is reported by name.</p>
 *
 * <p>Only directly comparable, scalar-typed pairs are checked. Collections and nested value objects
 * are mapped into different response types, and a deliberate override such as an emptied map is not
 * a mismatch to report — including them would make this a gate that cannot be acted on.</p>
 *
 * <p>Some mappers deliberately withhold values: an editable baseline returns only the identity and
 * the flag when the caller may not edit, which is a permission decision rather than a mapping bug.
 * Each mapping therefore runs twice, once with every boolean {@code true} and once with every
 * boolean {@code false}, and only a mismatch that survives both is reported — a withheld field
 * appears under one setting, a value on the wrong field under both. The cost is that a swap between
 * two booleans is not detected, which a two-valued type could never reveal reliably anyway.</p>
 */
class ResponseMappingFidelityTest {

    private static final String PACKAGE = "io.github.archetom.crm.infra.facade";
    private static final Path CLASSES = Path.of("target/classes/io/github/archetom/crm/infra/facade");

    /**
     * The mappings must actually run. Without a floor, a harness that silently skipped everything
     * would report success while checking nothing. 741 pairs are compared today; the floor sits
     * just under that so a large drop in what is reachable fails rather than passes quietly.
     */
    private static final int MINIMUM_CHECKED_PAIRS = 700;

    @Test
    void everyMappedValueLandsOnTheResponseFieldOfTheSameName() {
        List<String> mismatches = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        int checked = 0;

        for (Method mapper : mappers()) {
            Class<?> valueType = mapper.getParameterTypes()[0];
            List<String> permissive = new ArrayList<>();
            List<String> restrictive = new ArrayList<>();
            int checkedHere;
            try {
                mapper.setAccessible(true);
                checkedHere = run(mapper, valueType, true, permissive);
                run(mapper, valueType, false, restrictive);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                unmapped.add(mapper.getDeclaringClass().getSimpleName() + "#" + valueType.getSimpleName());
                continue;
            }
            checked += checkedHere;
            // Only a mismatch under both boolean settings is a mapping fault; one that appears
            // under a single setting is a field the mapper deliberately withholds.
            permissive.stream().filter(restrictive::contains).forEach(mismatches::add);
        }

        assertTrue(mismatches.isEmpty(), () -> "a value reached the wrong response field:\n  "
                + String.join("\n  ", mismatches));
        int total = checked;
        assertTrue(total >= MINIMUM_CHECKED_PAIRS,
                () -> "only " + total + " field pairs were checked, expected at least "
                        + MINIMUM_CHECKED_PAIRS + "; value objects that could not be built: " + unmapped);
    }

    private static int run(Method mapper, Class<?> valueType, boolean booleans,
                           List<String> mismatches) throws ReflectiveOperationException {
        Object value = populated(valueType, booleans);
        Object response = mapper.invoke(null, value);
        return response == null ? 0 : compare(valueType, value, response, mapper, mismatches);
    }

    private static int compare(Class<?> valueType, Object value, Object response,
                               Method mapper, List<String> mismatches) {
        int checked = 0;
        for (RecordComponent component : valueType.getRecordComponents()) {
            if (!comparable(component.getType())) {
                continue;
            }
            Method getter = getter(response.getClass(), component.getName(), component.getType());
            if (getter == null) {
                continue;
            }
            try {
                Object expected = component.getAccessor().invoke(value);
                Object actual = getter.invoke(response);
                checked++;
                if (!Objects.equals(expected, actual)) {
                    mismatches.add(mapper.getDeclaringClass().getSimpleName() + ": "
                            + valueType.getSimpleName() + "." + component.getName()
                            + " = " + expected + " but " + response.getClass().getSimpleName()
                            + "." + component.getName() + " = " + actual);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("cannot read " + component.getName(), exception);
            }
        }
        return checked;
    }

    /** The mapping methods: one value object in, one response out. */
    private static List<Method> mappers() {
        List<Method> mappers = new ArrayList<>();
        for (Class<?> type : facadeClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter.isRecord() && parameter.getSimpleName().endsWith("VO")
                        && method.getReturnType().getSimpleName().endsWith("Response")) {
                    mappers.add(method);
                }
            }
        }
        return mappers;
    }

    private static List<Class<?>> facadeClasses() {
        if (!Files.isDirectory(CLASSES)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(CLASSES)) {
            List<Class<?>> types = new ArrayList<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String name = PACKAGE + "." + CLASSES.relativize(file).toString()
                        .replace(".class", "").replace('/', '.');
                try {
                    types.add(Class.forName(name, false, ResponseMappingFidelityTest.class.getClassLoader()));
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // A class the test classpath cannot resolve simply contributes no mappings.
                }
            }
            return types;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Builds a record with a value unique to each component, so a swap cannot go unnoticed. */
    private static Object populated(Class<?> type, boolean booleans) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            parameterTypes[index] = components[index].getType();
            arguments[index] = distinctValue(components[index].getType(), index + 1, booleans);
        }
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("cannot build " + type.getSimpleName(), exception);
        }
    }

    private static Object distinctValue(Class<?> type, int position, boolean booleans) {
        if (type == String.class) {
            return "value-" + position;
        }
        if (type == Long.class || type == long.class) {
            return 1_000L + position;
        }
        if (type == Integer.class || type == int.class) {
            return position;
        }
        if (type == Boolean.class || type == boolean.class) {
            return booleans;
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
        if (type == Instant.class) {
            return Instant.parse("2026-01-01T00:00:00Z").plusSeconds(position);
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[position % type.getEnumConstants().length];
        }
        if (type == List.class) {
            return List.of();
        }
        if (type == Set.class) {
            return Set.of();
        }
        if (type == Map.class) {
            return Map.of();
        }
        // Anything else stays absent; only comparable scalars are asserted on.
        return null;
    }

    private static boolean comparable(Class<?> type) {
        return type == String.class || type == Long.class || type == long.class
                || type == Integer.class || type == int.class
                || type == Boolean.class || type == boolean.class
                || type == BigDecimal.class || type == LocalDate.class
                || type == LocalDateTime.class || type == LocalTime.class || type == Instant.class;
    }

    private static Method getter(Class<?> type, String property, Class<?> expectedType) {
        String capitalized = property.substring(0, 1).toUpperCase(Locale.ROOT) + property.substring(1);
        for (String candidate : List.of("get" + capitalized, "is" + capitalized, property)) {
            try {
                Method method = type.getMethod(candidate);
                if (method.getParameterCount() == 0 && method.getReturnType() == expectedType) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Not every component has a same-named response property; those are not compared.
            }
        }
        return null;
    }
}
