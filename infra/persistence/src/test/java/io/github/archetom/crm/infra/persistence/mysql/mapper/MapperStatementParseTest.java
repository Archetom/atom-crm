package io.github.archetom.crm.infra.persistence.mysql.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses every mapper statement without a database.
 *
 * <p>Malformed dynamic SQL, an unresolvable type alias, a broken result map, and a namespace that
 * no longer matches its interface are all build-time facts, but nothing in the fast suite used to
 * observe them: the statements were first parsed by the Testcontainers suite, which is skipped
 * whenever Docker is absent. This closes that gap for the XML mappers and the annotation SQL
 * alike — it proves the statements parse and resolve, not that they return correct rows.</p>
 */
class MapperStatementParseTest {

    private static final String MAPPER_PACKAGE = "io.github.archetom.crm.infra.persistence.mysql.mapper";
    private static final String TYPE_ALIAS_PACKAGE = "io.github.archetom.crm.infra.persistence.mysql.po";

    @Test
    void everyXmlMapperParsesAndBindsToItsInterface() throws Exception {
        Configuration configuration = configuration();
        List<Path> mappers = xmlMappers();
        assertTrue(mappers.size() >= 14,
                () -> "expected the XML mapper directory on the classpath, found " + mappers.size());

        List<String> failures = new ArrayList<>();
        for (Path mapper : mappers) {
            String resource = "mapper/" + mapper.getFileName();
            try (InputStream stream = Files.newInputStream(mapper)) {
                new XMLMapperBuilder(stream, configuration, resource,
                        configuration.getSqlFragments()).parse();
            } catch (RuntimeException | IOException exception) {
                failures.add(resource + " -> " + rootCause(exception));
            }
        }
        assertTrue(failures.isEmpty(), () -> "XML mappers that do not parse:\n"
                + String.join("\n", failures));

        // A namespace that drifts from its interface silently detaches the statements at runtime.
        for (String namespace : configuration.getMapperRegistry().getMappers().stream()
                .map(Class::getName).toList()) {
            assertTrue(namespace.startsWith(MAPPER_PACKAGE),
                    () -> namespace + " is bound outside the mapper package");
        }
    }

    @Test
    void everyMapperInterfaceParsesItsAnnotationSql() throws Exception {
        Configuration configuration = configuration();
        List<Class<?>> mappers = mapperInterfaces();
        assertTrue(mappers.size() >= 170,
                () -> "expected the compiled mapper interfaces, found " + mappers.size());

        List<String> failures = new ArrayList<>();
        for (Class<?> mapper : mappers) {
            try {
                configuration.addMapper(mapper);
            } catch (RuntimeException exception) {
                failures.add(mapper.getSimpleName() + " -> " + rootCause(exception));
            }
        }
        assertTrue(failures.isEmpty(), () -> "mapper interfaces whose statements do not parse:\n"
                + String.join("\n", failures));
    }

    @Test
    void theWholeMapperSetLoadsTogetherAndEveryStatementResolves() throws Exception {
        Configuration configuration = configuration();
        for (Path mapper : xmlMappers()) {
            try (InputStream stream = Files.newInputStream(mapper)) {
                new XMLMapperBuilder(stream, configuration, "mapper/" + mapper.getFileName(),
                        configuration.getSqlFragments()).parse();
            }
        }
        for (Class<?> mapper : mapperInterfaces()) {
            // Parsing an XML mapper already binds its namespace interface.
            if (!configuration.hasMapper(mapper)) {
                configuration.addMapper(mapper);
            }
        }

        // MyBatis also keys every statement by its short name, so shared names across mappers
        // legitimately resolve to an ambiguity marker. Reading through a wildcard avoids the
        // checked cast the declared element type would otherwise insert on every element.
        Collection<?> registered = configuration.getMappedStatements();
        List<MappedStatement> statements = registered.stream()
                .filter(MappedStatement.class::isInstance)
                .map(MappedStatement.class::cast)
                .toList();

        List<String> failures = new ArrayList<>();
        for (MappedStatement statement : statements) {
            if (statement.getResultMaps() == null || statement.getSqlSource() == null) {
                failures.add(statement.getId() + " did not resolve its result map or SQL source");
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
        assertTrue(statements.size() > 100,
                () -> "expected the full statement set, found " + statements.size());
    }

    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeAliasRegistry().registerAliases(TYPE_ALIAS_PACKAGE);
        return configuration;
    }

    private static List<Path> xmlMappers() throws Exception {
        URL anchor = Thread.currentThread().getContextClassLoader()
                .getResource("mapper/CustomerMapper.xml");
        assertNotNull(anchor, "the XML mapper directory must be on the test classpath");
        try (Stream<Path> files = Files.list(Path.of(anchor.toURI()).getParent())) {
            return files.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }
    }

    private static List<Class<?>> mapperInterfaces() throws Exception {
        URL anchor = Thread.currentThread().getContextClassLoader()
                .getResource(MAPPER_PACKAGE.replace('.', '/') + "/CustomerMapper.class");
        assertNotNull(anchor, "the compiled mapper interfaces must be on the test classpath");
        List<Class<?>> mappers = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of(anchor.toURI()).getParent())) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                Class<?> type = Class.forName(
                        MAPPER_PACKAGE + "." + name.substring(0, name.length() - ".class".length()));
                if (type.isInterface()) {
                    mappers.add(type);
                }
            }
        }
        return mappers;
    }

    private static String rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
