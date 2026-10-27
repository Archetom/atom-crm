package io.github.archetom.crm;

import org.testcontainers.DockerClientFactory;

/** Decides whether the container-backed tests can run in the current environment. */
final class DatabaseTestCondition {

    /** Explicit opt-out for a deliberately fast local run. */
    static final String SKIP_PROPERTY = "atom.test.skip-database-tests";

    /**
     * Probing the daemon is not free, so the answer is resolved once per JVM. Availability does
     * not change meaningfully within a single test run.
     */
    private static volatile Boolean dockerAvailable;

    private DatabaseTestCondition() {
    }

    static boolean databaseTestsCanRun() {
        if (Boolean.parseBoolean(System.getProperty(SKIP_PROPERTY))) {
            return false;
        }
        return dockerAvailable();
    }

    private static boolean dockerAvailable() {
        Boolean cached = dockerAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (DatabaseTestCondition.class) {
            if (dockerAvailable == null) {
                boolean available;
                try {
                    available = DockerClientFactory.instance().isDockerAvailable();
                } catch (RuntimeException | LinkageError probeFailure) {
                    // An unreachable or unusable daemon means these tests cannot run; it must not
                    // fail the build for someone who simply has no Docker installed.
                    available = false;
                }
                dockerAvailable = available;
            }
            return dockerAvailable;
        }
    }
}
