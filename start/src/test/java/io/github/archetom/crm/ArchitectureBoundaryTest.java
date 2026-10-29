package io.github.archetom.crm;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bytecode-level guards for dependency, adapter, entity, and naming boundaries. */
class ArchitectureBoundaryTest {

    private static final String ROOT_PACKAGE = "io.github.archetom.crm";

    @Test
    void compiledArchitectureRespectsBoundaries() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ArchitectureBoundaryTest::isProductionLocation)
                .importPackages(ROOT_PACKAGE);
        List<String> violations = new ArrayList<>();

        assertLayerPresent(classes, ".api.", violations);
        assertLayerPresent(classes, ".application.", violations);
        assertLayerPresent(classes, ".domain.", violations);
        assertLayerPresent(classes, ".shared.", violations);
        assertLayerPresent(classes, ".infra.persistence.", violations);
        assertLayerPresent(classes, ".infra.facade.", violations);
        assertLayerPresent(classes, ".infra.rest.", violations);

        for (JavaClass source : classes) {
            inspectDependencies(source, violations);
            inspectAttachmentDependencies(source, violations);
            inspectEntityMethods(source, violations);
            inspectPersistenceObjects(source, violations);
            inspectNaming(source, violations);
        }

        assertTrue(violations.isEmpty(), () -> "Architecture boundary violations:\n"
                + String.join("\n", violations));
    }

    static boolean isProductionLocation(Location location) {
        // The archetype IT nests the whole reactor below an outer target/test-classes.
        // Only the innermost Maven output directory identifies production vs test code.
        String uri = location.asURI().toString();
        return location.isJar() || uri.lastIndexOf("/target/test-classes/") < 0
                || uri.lastIndexOf("/target/classes/") > uri.lastIndexOf("/target/test-classes/");
    }

    /** B24a files must terminate servlet/filesystem/provider details at their owning adapters. */
    static void inspectAttachmentDependencies(JavaClass source, List<String> violations) {
        String packageName = source.getPackageName();
        boolean attachmentCore = source.getSimpleName().startsWith("Attachment")
                && (inPackage(packageName, ROOT_PACKAGE + ".domain")
                || inPackage(packageName, ROOT_PACKAGE + ".application")
                || inPackage(packageName, ROOT_PACKAGE + ".api"));
        if (!attachmentCore) return;
        List<String> forbidden = List.of("jakarta.servlet.", "javax.servlet.", "java.nio.file.",
                "java.io.File", "java.net.URL", "java.net.URI", "org.springframework.web.multipart.",
                "software.amazon.awssdk.");
        source.getDirectDependenciesFromSelf().forEach(dependency -> {
            String target = dependency.getTargetClass().getName();
            if (forbidden.stream().anyMatch(target::startsWith)) {
                violations.add("Attachment core leaks adapter type: " + source.getName() + " -> " + target);
            }
        });
    }

    static void inspectDependencies(JavaClass source, List<String> violations) {
        String sourcePackage = source.getPackageName();
        List<String> forbidden = forbiddenDependencies(sourcePackage);
        source.getDirectDependenciesFromSelf().forEach(dependency -> {
            String target = dependency.getTargetClass().getName();
            if (forbidden.stream().anyMatch(target::startsWith)) {
                violations.add(source.getName() + " -> " + target);
            }
            if ((inPackage(sourcePackage, ROOT_PACKAGE + ".infra.rest.controller")
                    || source.isAnnotatedWith("org.springframework.stereotype.Controller")
                    || source.isMetaAnnotatedWith("org.springframework.stereotype.Controller"))
                    && (target.startsWith(ROOT_PACKAGE + ".domain.repository.")
                    || target.startsWith(ROOT_PACKAGE + ".domain.entity.")
                    || target.startsWith(ROOT_PACKAGE + ".domain.aggregate.")
                    || target.startsWith(ROOT_PACKAGE + ".infra.persistence.")
                    || target.startsWith(ROOT_PACKAGE + ".application.vo."))) {
                violations.add("REST controller bypasses facade/use-case boundary: "
                        + source.getName() + " -> " + target);
            }
            if (inPackage(sourcePackage, ROOT_PACKAGE + ".infra.grpc")
                    && (target.startsWith(ROOT_PACKAGE + ".domain.")
                    || target.startsWith(ROOT_PACKAGE + ".application.")
                    || target.startsWith(ROOT_PACKAGE + ".infra.persistence.")
                    || target.startsWith(ROOT_PACKAGE + ".infra.rest."))) {
                violations.add("gRPC adapter bypasses the facade boundary: "
                        + source.getName() + " -> " + target);
            }
        });
    }

    private static List<String> forbiddenDependencies(String sourcePackage) {
        if (inPackage(sourcePackage, ROOT_PACKAGE + ".domain")) {
            return List.of(
                    ROOT_PACKAGE + ".api.", ROOT_PACKAGE + ".application.",
                    ROOT_PACKAGE + ".shared.", ROOT_PACKAGE + ".infra.",
                    "org.springframework.", "org.mybatis.", "com.baomidou.",
                    "java.net.http.", "java.sql.", "javax.sql.",
                    "jakarta.persistence.", "javax.persistence.",
                    "jakarta.servlet.", "javax.servlet.",
                    "org.apache.http.", "org.apache.hc.", "okhttp3.",
                    "io.grpc.", "io.netty.", "reactor.netty.",
                    "redis.clients.", "io.lettuce.", "org.redisson.");
        }
        if (inPackage(sourcePackage, ROOT_PACKAGE + ".api")) {
            return List.of(
                    ROOT_PACKAGE + ".application.", ROOT_PACKAGE + ".domain.",
                    ROOT_PACKAGE + ".infra.", "org.springframework.",
                    "org.mybatis.", "com.baomidou.");
        }
        if (inPackage(sourcePackage, ROOT_PACKAGE + ".shared")) {
            return List.of(
                    ROOT_PACKAGE + ".api.", ROOT_PACKAGE + ".application.",
                    ROOT_PACKAGE + ".domain.", ROOT_PACKAGE + ".infra.",
                    "org.springframework.", "org.mybatis.", "com.baomidou.");
        }
        if (inPackage(sourcePackage, ROOT_PACKAGE + ".application")) {
            return List.of(
                    ROOT_PACKAGE + ".infra.", "org.springframework.data.",
                    "org.springframework.web.", "org.mybatis.",
                    "com.baomidou.", "reactor.netty.");
        }
        if (inPackage(sourcePackage, ROOT_PACKAGE + ".infra.facade")) {
            return List.of(ROOT_PACKAGE + ".domain.",
                    ROOT_PACKAGE + ".infra.persistence.");
        }
        return List.of();
    }

    static void inspectEntityMethods(JavaClass source, List<String> violations) {
        if (!inPackage(source.getPackageName(), ROOT_PACKAGE + ".domain.entity")
                && !source.isAssignableTo(ROOT_PACKAGE + ".domain.aggregate.AggregateRoot")) {
            return;
        }
        source.getAllMethods().stream()
                .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                .filter(method -> method.getName().matches("set[A-Z].*"))
                .forEach(method -> violations.add("Domain entity exposes public setter: " + method.getFullName()));
    }

    static void inspectPersistenceObjects(JavaClass source, List<String> violations) {
        if (!inPackage(source.getPackageName(), ROOT_PACKAGE + ".infra.persistence.mysql.po")
                || source.getSimpleName().equals("package-info")) {
            return;
        }
        // CRM child rows, actor-global records and control-plane/query projections deliberately
        // have different identity/version fields; they do not all inherit the tenant BasePO.
        // Their SQL representation must still remain independent of domain objects.
        source.getDirectDependenciesFromSelf().stream()
                .filter(dependency -> dependency.getTargetClass().getName().startsWith(ROOT_PACKAGE + ".domain."))
                .forEach(dependency -> violations.add("Persistence object leaks domain type: "
                        + dependency.getDescription()));
    }

    private static boolean inPackage(String actual, String expected) {
        return actual.equals(expected) || actual.startsWith(expected + ".");
    }

    /**
     * These suffixes identify top-level architecture roles. Nested classes are shaped by their
     * enclosing type rather than named independently, and anonymous ones have no simple name at
     * all, so they are not carriers of the role a package suffix expresses.
     */
    static void inspectNaming(JavaClass source, List<String> violations) {
        if (source.getSimpleName().equals("package-info") || source.isNestedClass()) {
            return;
        }
        requireSuffix(source, ".api.dto.request", violations, "Request");
        requireSuffix(source, ".api.dto.response", violations, "Response");
        requireSuffix(source, ".application.vo", violations, "VO");
        requireSuffix(source, ".infra.persistence.mysql.po", violations, "PO");
        if (source.isInterface()) {
            requireSuffix(source, ".domain.repository", violations, "Repository");
        }
        // A persistence adapter implements either a domain repository port or an application
        // output-port capability. docs/object-layering.md names the latter after the capability
        // (`SystemInstallationStore`, `PasswordResetTokenStore`), so both suffixes are correct here.
        requireSuffix(source, ".infra.persistence.repository", violations, "RepositoryImpl", "StoreImpl");
    }

    private static void requireSuffix(
            JavaClass source, String packageSuffix, List<String> violations, String... nameSuffixes) {
        if (!inPackage(source.getPackageName(), ROOT_PACKAGE + packageSuffix) || source.isEnum()) {
            return;
        }
        if (packageSuffix.equals(".api.dto.response") && source.getSimpleName().endsWith("Result")) {
            return;
        }
        for (String nameSuffix : nameSuffixes) {
            if (source.getSimpleName().endsWith(nameSuffix)) {
                return;
            }
        }
        violations.add(source.getName() + " must end with " + String.join(" or ", nameSuffixes));
    }

    private static void assertLayerPresent(
            JavaClasses classes, String packageFragment, List<String> violations) {
        String packageName = ROOT_PACKAGE + (packageFragment.endsWith(".")
                ? packageFragment.substring(0, packageFragment.length() - 1)
                : packageFragment);
        boolean present = classes.stream()
                .anyMatch(javaClass -> javaClass.getPackageName().equals(packageName)
                        || javaClass.getPackageName().startsWith(packageName + "."));
        if (!present) {
            violations.add("No compiled classes found for layer " + packageFragment);
        }
    }
}
