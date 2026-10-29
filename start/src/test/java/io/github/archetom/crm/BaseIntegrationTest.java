package io.github.archetom.crm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test scaffold backed by MySQL Testcontainers.
 *
 * <ul>
 * <li>MySQL managed by Testcontainers</li>
 * <li>dynamic Spring Boot datasource configuration</li>
 * <li>MockMvc HTTP helpers</li>
 * <li>real transaction commits and explicit database cleanup</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {io.github.archetom.crm.Bootstrap.class}
        )
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // ======= Shared Testcontainers configuration =======

    protected static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:26.10.0"))
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_password")
            // Test data lives only for this JVM; avoid host-disk flushes for disposable fixtures.
            .withTmpFs(Map.of("/var/lib/mysql", "rw,size=1g"))
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        // One JVM-wide container keeps Spring's cached DataSource valid across test classes.
        // Testcontainers' resource reaper stops it when the test JVM exits.
        MYSQL.start();
    }

    /** Return the shared MySQL test container for advanced assertions. */
    protected static MySQLContainer mysqlContainer() { return MYSQL; }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL configuration
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("atom.redis.enabled", () -> "false");
        registry.add("atom.lead-intake.max-auth-attempts-per-minute", () -> "10000");
        registry.add("atom.lead-intake.max-submissions-per-minute", () -> "2");
        registry.add("atom.marketing-form.max-submissions-per-minute", () -> "2");
        registry.add("atom.marketing-form.max-submissions-per-source-per-minute", () -> "10000");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    // ======= =======
    @Autowired(required = false)
    protected MockMvc mockMvc;

    @Autowired(required = false)
    protected ObjectMapper objectMapper;

    @Autowired(required = false)
    protected DataSource dataSource;

    // ======= =======
    /**
     * Control rows that Flyway seeds once and no fixture recreates. Everything else is test data.
     */
    private static final Set<String> PRESERVED_TABLES = Set.of(
            "flyway_schema_history", "crm_automation_control",
            "crm_work_recurrence_control", "system_installation");

    /**
     * Whether the schema is emptied before every test.
     *
     * <p>Override to {@code false} in a class that seeds once and shares that data across its test
     * methods; such a class is still isolated from other classes, it just keeps its own fixture for
     * the duration. Leave the default wherever each test builds what it needs.</p>
     */
    protected boolean resetsBetweenTests() {
        return true;
    }

    /** The class whose reset has already run; the container is shared by every class. */
    private static volatile String resetForClass;

    @BeforeEach
    void setUpIntegration() {
        // Every class cleans only the tables it knows about, so a row written by one test can
        // outlive it and block a later cleanup on a foreign key — within the class or in whichever
        // class runs next, since ordering is not guaranteed. Resetting here makes each test start
        // from the same empty schema regardless of what ran before it.
        String currentClass = getClass().getName();
        if (resetsBetweenTests() || !currentClass.equals(resetForClass)) {
            resetBusinessTables();
            resetForClass = currentClass;
        }
        // Personal reports reference memberships and must be removed before legacy fixtures
        // clear or recreate organization rows in their per-class setup hooks.
        executeSql("DELETE FROM report_definition");
        initTestData();
    }

    /**
     * Empties every business table on one connection, so the foreign-key toggle applies to all of
     * the deletes. This makes a class independent of whichever class ran before it.
     */
    private void resetBusinessTables() {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            List<String> tables = new java.util.ArrayList<>();
            // information_schema is unambiguous for MySQL; JDBC catalog metadata is not.
            try (var rows = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
                while (rows.next()) {
                    String table = rows.getString(1);
                    if (!PRESERVED_TABLES.contains(table)) tables.add(table);
                }
            }
            if (tables.isEmpty()) {
                throw new IllegalStateException("no business tables were found to reset");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) statement.execute("DELETE FROM " + table);
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to reset integration test data", exception);
        }
    }

    @AfterEach
    void tearDownIntegration() {
        executeSql("DELETE FROM report_definition");
        clearTestData();
    }

    // ======= HTTP request utility =======
    protected ResultActions performGet(String url, Object... params) throws Exception {
        return mockMvc.perform(withTestActor(get(url, params).contentType(MediaType.APPLICATION_JSON)));
    }

    protected ResultActions performGetWithParams(String url, Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        params.forEach(request::param);
        return mockMvc.perform(withTestActor(request.contentType(MediaType.APPLICATION_JSON)));
    }

    protected ResultActions performPost(String url, Object requestBody) throws Exception {
        return mockMvc.perform(withTestActor(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(requestBody))));
    }

    protected ResultActions performPut(String url, Object requestBody) throws Exception {
        return mockMvc.perform(withTestActor(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(requestBody))));
    }

    protected ResultActions performDelete(String url, Object... params) throws Exception {
        return mockMvc.perform(withTestActor(delete(url, params).contentType(MediaType.APPLICATION_JSON)));
    }

    // ======= response utility =======
    protected ResultActions assertSuccess(ResultActions resultActions) throws Exception {
        return resultActions.andExpect(status().isOk());
    }

    protected ResultActions assertValidationError(ResultActions resultActions) throws Exception {
        return resultActions.andExpect(status().isBadRequest());
    }

    /**
     * Wait for a {@code StreamingResponseBody} to finish writing to the initial response.
     *
     * <p>Reading the initial MockMvc response races the streaming executor and can observe
     * an empty body under full-suite load even though the stream later completes normally.
     * A second MockMvc dispatch is intentionally unnecessary here: it would run the stateless
     * security chain again after the streamed response has already been committed.</p>
     */
    protected ResultActions completeAsync(ResultActions resultActions) throws Exception {
        MvcResult initial = resultActions.andExpect(request().asyncStarted()).andReturn();
        initial.getAsyncResult(30_000);
        return resultActions;
    }

    // ======= database utility =======
    /**
     * Fixture SQL runs with foreign-key checks suspended.
     *
     * <p>Each class keeps its own hand-written list of tables to clear, and those lists go stale:
     * a migration adds a child table and the list that deletes its parent is never updated, so the
     * cleanup fails on a constraint. Suspending the check for fixture statements makes the lists
     * order-independent instead of requiring every one of them to stay exhaustive. Production code
     * paths are unaffected; this helper is only used by test setup and teardown.</p>
     */
    protected void executeSql(String sql) {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                statement.execute(sql);
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute SQL: " + sql, e);
        }
    }

    /**
     * MySQL refuses to truncate a table another table references, so the foreign-key check is
     * suspended for the statement. It must be suspended and restored on the same connection,
     * which {@link #executeSql} cannot do because it opens a new one per call.
     */
    protected void truncateTable(String tableName) {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                statement.execute("TRUNCATE TABLE " + tableName);
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to truncate " + tableName, exception);
        }
    }

    // ======= JSON utility =======
    /** object JSON string */
    protected String toJson(Object object) {
        try {
            return objectMapper != null ? objectMapper.writeValueAsString(object) : "";
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    /** JSON string object (class) */
    protected <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper != null ? objectMapper.readValue(json, clazz) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to object", e);
        }
    }

    /** JSON string object (complex, such as List/Map) */
    protected <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper != null ? objectMapper.readValue(json, typeRef) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to object", e);
        }
    }

    // ======= Test data hooks =======
    protected void initTestData() {
        // Subclasses may override.
    }

    protected void clearTestData() {
        // Subclasses may override.
    }

    protected MockHttpServletRequestBuilder withTestActor(MockHttpServletRequestBuilder request) {
        return request
                .header("X-Dev-User-Id", "888888")
                .header("X-Dev-Tenant-Id", "999999");
    }
}
