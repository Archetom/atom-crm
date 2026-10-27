package io.github.archetom.crm;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that needs the MySQL container from {@link BaseIntegrationTest}.
 *
 * <p>These tests carry the tenant-isolation, transaction, mapper, migration and concurrency
 * coverage, so they should run whenever they can. Their real precondition is a working Docker
 * daemon, not a particular environment variable: gating them on {@code CI} meant a developer with
 * Docker running still executed none of them, and the invariants went unchecked until CI.</p>
 *
 * <p>Skip them deliberately with {@code -Datom.test.skip-database-tests=true}.</p>
 *
 * @see DatabaseTestCondition
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@EnabledIf(value = "io.github.archetom.crm.DatabaseTestCondition#databaseTestsCanRun",
        disabledReason = "Docker is unavailable or database tests were skipped explicitly")
public @interface RequiresDatabase {
}
