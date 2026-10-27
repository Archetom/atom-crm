package io.github.archetom.crm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The container-backed suite is now gated on this condition, so the condition itself must never be
 * the reason a build fails: a machine without Docker has to skip cleanly rather than error.
 */
class DatabaseTestConditionTest {

    @AfterEach
    void clearSkipProperty() {
        System.clearProperty(DatabaseTestCondition.SKIP_PROPERTY);
    }

    @Test
    void theExplicitOptOutWinsOverDockerAvailability() {
        System.setProperty(DatabaseTestCondition.SKIP_PROPERTY, "true");

        assertFalse(DatabaseTestCondition.databaseTestsCanRun(),
                "an explicit skip must disable the database tests even where Docker is present");
    }

    @Test
    void probingForDockerNeverThrows() {
        System.clearProperty(DatabaseTestCondition.SKIP_PROPERTY);

        // The answer depends on the machine; that it is answered without escaping does not.
        assertDoesNotThrow(DatabaseTestCondition::databaseTestsCanRun);
    }
}
