package io.github.archetom.crm;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
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
            inspectNaming(source, violations);
        }

        assertTrue(violations.isEmpty(), () -> "Architecture boundary violations:\n"
                + String.join("\n", violations));
    }

    /** B24a files must terminate servlet/filesystem/provider details at their owning adapters. */
    private static void inspectAttachmentDependencies(JavaClass source, List<String> violations) {
        String packageName = source.getPackageName();
        boolean attachmentCore = source.getSimpleName().startsWith("Attachment")
                && (packageName.startsWith(ROOT_PACKAGE + ".domain")
                || packageName.startsWith(ROOT_PACKAGE + ".application")
                || packageName.startsWith(ROOT_PACKAGE + ".api"));
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

    private static void inspectDependencies(JavaClass source, List<String> violations) {
        String sourcePackage = source.getPackageName();
        List<String> forbidden = forbiddenDependencies(sourcePackage);
        source.getDirectDependenciesFromSelf().forEach(dependency -> {
            String target = dependency.getTargetClass().getName();
            if (forbidden.stream().anyMatch(target::startsWith)) {
                violations.add(source.getName() + " -> " + target);
            }
            if (sourcePackage.startsWith(ROOT_PACKAGE + ".infra.rest.controller")
                    && (target.startsWith(ROOT_PACKAGE + ".domain.repository.")
                    || target.startsWith(ROOT_PACKAGE + ".infra.persistence.repository.")
                    || target.startsWith(ROOT_PACKAGE + ".application.vo."))) {
                violations.add("REST controller bypasses facade/use-case boundary: "
                        + source.getName() + " -> " + target);
            }
        });
    }

    private static List<String> forbiddenDependencies(String sourcePackage) {
        if (sourcePackage.startsWith(ROOT_PACKAGE + ".domain")) {
            return List.of(
                    ROOT_PACKAGE + ".api.", ROOT_PACKAGE + ".application.",
                    ROOT_PACKAGE + ".shared.", ROOT_PACKAGE + ".infra.",
                    "org.springframework.", "org.mybatis.", "com.baomidou.");
        }
        if (sourcePackage.startsWith(ROOT_PACKAGE + ".api")) {
            return List.of(
                    ROOT_PACKAGE + ".application.", ROOT_PACKAGE + ".domain.",
                    ROOT_PACKAGE + ".infra.", "org.springframework.",
                    "org.mybatis.", "com.baomidou.");
        }
        if (sourcePackage.startsWith(ROOT_PACKAGE + ".shared")) {
            return List.of(
                    ROOT_PACKAGE + ".api.", ROOT_PACKAGE + ".application.",
                    ROOT_PACKAGE + ".domain.", ROOT_PACKAGE + ".infra.",
                    "org.springframework.", "org.mybatis.", "com.baomidou.");
        }
        if (sourcePackage.startsWith(ROOT_PACKAGE + ".application")) {
            return List.of(
                    ROOT_PACKAGE + ".infra.", "org.springframework.data.",
                    "org.springframework.web.", "org.mybatis.",
                    "com.baomidou.", "reactor.netty.");
        }
        if (sourcePackage.startsWith(ROOT_PACKAGE + ".infra.facade")) {
            return List.of(ROOT_PACKAGE + ".domain.",
                    ROOT_PACKAGE + ".infra.persistence.");
        }
        return List.of();
    }

    private static void inspectEntityMethods(JavaClass source, List<String> violations) {
        if (!source.getPackageName().startsWith(ROOT_PACKAGE + ".domain.entity")) {
            return;
        }
        source.getMethods().stream()
                .filter(method -> method.getOwner().equals(source))
                .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                .filter(method -> method.getName().matches("set[A-Z].*"))
                .forEach(method -> violations.add("Domain entity exposes public setter: " + method.getFullName()));
    }

    /**
     * These suffixes identify top-level architecture roles. Nested classes are shaped by their
     * enclosing type rather than named independently, and anonymous ones have no simple name at
     * all, so they are not carriers of the role a package suffix expresses.
     */
    private static void inspectNaming(JavaClass source, List<String> violations) {
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
        if (!source.getPackageName().startsWith(ROOT_PACKAGE + packageSuffix) || source.isEnum()) {
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
