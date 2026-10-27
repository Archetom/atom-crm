package io.github.archetom.crm.infra.persistence.migration;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Fast merge guard for the versioned migrations that Flyway loads from this module. */
class FlywayMigrationManifestTest {
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(\\d+)__[A-Za-z0-9_]+\\.(?:sql|class)$");

    @Test
    void mergedMigrationVersionsAreUniqueContiguousAndKeepTheIntegratedTailOrder() throws Exception {
        URL firstMigration = Thread.currentThread().getContextClassLoader()
                .getResource("db/migration/V1__create_user_table.sql");
        assertNotNull(firstMigration, "Flyway migration directory must be on the test classpath");

        Map<Integer, List<String>> migrationsByVersion;
        try (var files = Files.list(Path.of(firstMigration.toURI()).getParent())) {
            migrationsByVersion = files
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(VERSIONED_MIGRATION::matcher)
                    .filter(Matcher::matches)
                    .collect(java.util.stream.Collectors.groupingBy(
                            matcher -> Integer.parseInt(matcher.group(1)),
                            java.util.stream.Collectors.mapping(Matcher::group,
                                    java.util.stream.Collectors.toList())));
        }

        assertEquals(List.of(), migrationsByVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() != 1)
                .map(entry -> "V" + entry.getKey() + entry.getValue())
                .sorted()
                .toList(), "each Flyway version must resolve to exactly one migration");
        assertEquals(IntStream.rangeClosed(1, 110).boxed().toList(), migrationsByVersion.keySet().stream()
                .sorted()
                .toList(), "the merged migration history must remain contiguous");
        assertEquals("V76__add_oa_announcement_notification_delivery.sql", only(migrationsByVersion, 76));
        assertEquals("V77__add_product_and_return_visit_file_targets.sql", only(migrationsByVersion, 77));
        assertEquals("V78__create_marketing_file_draft_and_upload_origin.sql", only(migrationsByVersion, 78));
        assertEquals("V79__create_enterprise_data_source_quota.sql", only(migrationsByVersion, 79));
        assertEquals("V80__create_customer_registered_identity_and_adoption_provenance.sql",
                only(migrationsByVersion, 80));
        assertEquals("V81__create_enterprise_data_adoption_retention_work.sql", only(migrationsByVersion, 81));
        assertEquals("V82__create_call_runtime_reliability_foundation.sql", only(migrationsByVersion, 82));
        assertEquals("V83__add_oa_announcement_attachment_target.sql", only(migrationsByVersion, 83));
        assertEquals("V84__add_oa_request_collaboration_attachment.sql", only(migrationsByVersion, 84));
        assertEquals("V85__create_opportunity_pipeline_catalog.sql", only(migrationsByVersion, 85));
        assertEquals("V86__scope_file_field_drafts_to_stage_workflow_records.sql",
                only(migrationsByVersion, 86));
        assertEquals("V87__add_marketing_form_submission_idempotency.sql",
                only(migrationsByVersion, 87));
        assertEquals("V88__create_marketing_activity.sql", only(migrationsByVersion, 88));
        assertEquals("V89__add_marketing_activity_counters.sql", only(migrationsByVersion, 89));
        assertEquals("V90__add_marketing_activity_participant_departments.sql",
                only(migrationsByVersion, 90));
        assertEquals("V91__add_marketing_activity_form_link.sql", only(migrationsByVersion, 91));
        assertEquals("V92__add_marketing_activity_attachment_target.sql",
                only(migrationsByVersion, 92));
        assertEquals("V93__harden_attachment_relation_target_check.sql",
                only(migrationsByVersion, 93));
        assertEquals("V94__add_marketing_activity_image_attachment_groups.sql",
                only(migrationsByVersion, 94));
        assertEquals("V95__add_return_visit_form_and_satisfaction.sql",
                only(migrationsByVersion, 95));
        assertEquals("V96__enforce_opportunity_contact_customer.sql",
                only(migrationsByVersion, 96));
        assertEquals("V97__add_product_image_drafts.sql", only(migrationsByVersion, 97));
        assertEquals("V98__create_dashboard_performance_target.sql", only(migrationsByVersion, 98));
        assertEquals("V99__create_dashboard_department_performance_target.sql", only(migrationsByVersion, 99));
        assertEquals("V100__create_crm_import_drafts.sql", only(migrationsByVersion, 100));
        assertEquals("V101__extend_crm_import_draft_product_type.sql", only(migrationsByVersion, 101));
        assertEquals("V102__add_dashboard_hidden_modules.sql", only(migrationsByVersion, 102));
        assertEquals("V103__create_customer_pool_release_history.sql", only(migrationsByVersion, 103));
        assertEquals("V104__generalize_marketing_form_targets.sql", only(migrationsByVersion, 104));
        assertEquals("V105__add_contract_collaborator_effective_until.sql", only(migrationsByVersion, 105));
        assertEquals("V106__create_password_reset_token.sql", only(migrationsByVersion, 106));
        assertEquals("V107__create_password_login_challenge.sql", only(migrationsByVersion, 107));
        assertEquals("V108__create_system_installation.sql", only(migrationsByVersion, 108));
        assertEquals("V109__repair_installation_marker_for_missing_administrator.sql", only(migrationsByVersion, 109));
        assertEquals("V110__restore_installation_marker_for_initialized_tenants.sql",
                only(migrationsByVersion, 110));
    }

    private String only(Map<Integer, List<String>> migrationsByVersion, int version) {
        return migrationsByVersion.get(version).getFirst();
    }
}
