package io.github.archetom.crm;

import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit catalog setup for fixtures that insert opportunities without the application boundary. */
final class OpportunityPipelineFixture {

    private OpportunityPipelineFixture() {
    }

    static void provisionActivePair(JdbcTemplate jdbc, long tenantId, String pipelineCode, String stageCode) {
        jdbc.update("INSERT IGNORE INTO crm_opportunity_pipeline_catalog_guard (tenant_id) VALUES (?)", tenantId);
        jdbc.update("INSERT IGNORE INTO crm_opportunity_pipeline "
                        + "(tenant_id, code, name, display_order, status) VALUES (?, ?, ?, 0, 'ACTIVE')",
                tenantId, pipelineCode, pipelineCode);
        jdbc.update("INSERT IGNORE INTO crm_opportunity_pipeline_stage "
                        + "(tenant_id, pipeline_code, code, name, display_order, probability, status) "
                        + "VALUES (?, ?, ?, ?, 0, 0, 'ACTIVE')",
                tenantId, pipelineCode, stageCode, stageCode);
    }
}
