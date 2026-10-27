package io.github.archetom.crm;

import io.github.archetom.crm.application.security.attachment.AttachmentTargetAccessRegistry;
import io.github.archetom.crm.application.port.out.attachment.AttachmentRuntimePolicy;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitCreateRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.api.dto.response.ReturnVisitResponse;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.valueobject.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B29a durable parent proof for Product and ReturnVisit ordinary/structured file paths. */
@RequiresDatabase
@Import(B29aProductReturnVisitFileIntegrationTest.AttachmentPolicyConfiguration.class)
class B29aProductReturnVisitFileIntegrationTest extends BaseIntegrationTest {
    private static final long TENANT = 9_240_770L;
    private static final long FILE_READER = TENANT + 1;
    private static final long MASKED_FILE_READER = TENANT + 2;
    private static final long FILE_BLIND_READER = TENANT + 3;

    @Autowired
    private AttachmentTargetAccessRegistry targetAccessRegistry;

    @Test
    void v77RegistersBothTypedParentsAndRejectsMismatchedOrdinaryAndFileRows() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Parents parents = seed(jdbc);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history"
                + " WHERE version = '77' AND success = 1", Integer.class));

        long productAttachment = attachmentFile(jdbc);
        long visitAttachment = attachmentFile(jdbc);
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, product_id)"
                + " VALUES (?, ?, 'PRODUCT', ?)", TENANT, productAttachment, parents.productId());
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, return_visit_id)"
                + " VALUES (?, ?, 'RETURN_VISIT', ?)", TENANT, visitAttachment, parents.returnVisitId());

        TenantId tenantId = new TenantId(TENANT);
        assertTrue(targetAccessRegistry.require(AttachmentTargetType.PRODUCT).lockForWorker(tenantId,
                new AttachmentTarget(AttachmentTargetType.PRODUCT, parents.productId())).attachmentsPermitted());
        assertTrue(targetAccessRegistry.require(AttachmentTargetType.RETURN_VISIT).lockForWorker(tenantId,
                new AttachmentTarget(AttachmentTargetType.RETURN_VISIT, parents.returnVisitId()))
                .attachmentsPermitted());

        long productDraft = draft(jdbc, "PRODUCT");
        long visitDraft = draft(jdbc, "RETURN_VISIT");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM crm_file_field_draft"
                + " WHERE tenant_id = ? AND id IN (?, ?)", Integer.class, TENANT, productDraft, visitDraft));

        long productFile = attachmentFile(jdbc);
        long visitFile = attachmentFile(jdbc);
        jdbc.update("""
                INSERT INTO crm_attachment_field_binding
                    (tenant_id, file_id, object_type, field_id, product_id, ordinal, content_version, evidence_digest)
                VALUES (?, ?, 'PRODUCT', ?, ?, 0, 1, UNHEX(REPEAT('11', 32)))
                """, TENANT, productFile, parents.productFieldId(), parents.productId());
        jdbc.update("""
                INSERT INTO crm_attachment_field_binding
                    (tenant_id, file_id, object_type, field_id, return_visit_id, ordinal, content_version, evidence_digest)
                VALUES (?, ?, 'RETURN_VISIT', ?, ?, 0, 1, UNHEX(REPEAT('22', 32)))
                """, TENANT, visitFile, parents.returnVisitFieldId(), parents.returnVisitId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND state = 'ACTIVE'", Integer.class, TENANT));

        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, return_visit_id)
                VALUES (?, ?, 'PRODUCT', ?)
                """, TENANT, attachmentFile(jdbc), parents.returnVisitId()));
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO crm_attachment_field_binding
                    (tenant_id, file_id, object_type, field_id, return_visit_id, ordinal, content_version, evidence_digest)
                VALUES (?, ?, 'PRODUCT', ?, ?, 0, 1, UNHEX(REPEAT('33', 32)))
                """, TENANT, attachmentFile(jdbc), parents.productFieldId(), parents.returnVisitId()));
    }

    @Test
    void workerTargetLifecycleClosesWhenEitherNewParentIsDeleted() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Parents parents = seed(jdbc);
        jdbc.update("UPDATE crm_product SET status = 'DELETED' WHERE tenant_id = ? AND id = ?", TENANT,
                parents.productId());
        jdbc.update("UPDATE crm_return_visit SET status = 'DELETED' WHERE tenant_id = ? AND id = ?", TENANT,
                parents.returnVisitId());

        TenantId tenantId = new TenantId(TENANT);
        assertFalse(targetAccessRegistry.require(AttachmentTargetType.PRODUCT).lockForWorker(tenantId,
                new AttachmentTarget(AttachmentTargetType.PRODUCT, parents.productId())).attachmentsPermitted());
        assertFalse(targetAccessRegistry.require(AttachmentTargetType.RETURN_VISIT).lockForWorker(tenantId,
                new AttachmentTarget(AttachmentTargetType.RETURN_VISIT, parents.returnVisitId()))
                .attachmentsPermitted());
    }

    @Test
    void productAndReturnVisitDeleteUseCasesWithdrawOrdinaryAndFileBindings() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Parents parents = seedParents(jdbc);
        long productAttachment = attachmentFile(jdbc);
        long productFile = attachmentFile(jdbc);
        long visitAttachment = attachmentFile(jdbc);
        long visitFile = attachmentFile(jdbc);
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, product_id)"
                + " VALUES (?, ?, 'PRODUCT', ?)", TENANT, productAttachment, parents.productId());
        jdbc.update("INSERT INTO crm_attachment_relation (tenant_id, file_id, target_type, return_visit_id)"
                + " VALUES (?, ?, 'RETURN_VISIT', ?)", TENANT, visitAttachment, parents.returnVisitId());
        bindProductFile(jdbc, productFile, parents);
        bindReturnVisitFile(jdbc, visitFile, parents);

        performAs(delete("/api/v1/products/{id}", parents.productId()).param("version", "0"))
                .andExpect(status().isOk());
        performAs(delete("/api/v1/return-visits/{id}", parents.returnVisitId()).param("version", "0"))
                .andExpect(status().isOk());

        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT relation_state FROM crm_attachment_relation"
                + " WHERE tenant_id = ? AND file_id = ?", String.class, TENANT, productAttachment));
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT relation_state FROM crm_attachment_relation"
                + " WHERE tenant_id = ? AND file_id = ?", String.class, TENANT, visitAttachment));
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT state FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND file_id = ?", String.class, TENANT, productFile));
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT state FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND file_id = ?", String.class, TENANT, visitFile));
    }

    @Test
    void createBeforeIdDraftsBindThroughCommandsAndWholeListUpdatesPreserveTypedFileValues() throws Exception {
        bootstrap();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        FieldDefinitionResponse productField = createFileField("PRODUCT", "B29a Product API FILE");
        FieldDefinitionResponse visitField = createFileField("RETURN_VISIT", "B29a ReturnVisit API FILE");

        DraftCandidates productDraft = stagedReadyCandidates(jdbc, "PRODUCT", productField.getId(), null, 2);
        ProductResponse product = fromJson(performAs(post("/api/v1/products")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new ProductCreateRequest().setName("B29a API Product").setCode("B29A-API-PRODUCT")
                        .setUnit("seat").setListPrice(new BigDecimal("10.00")).setAvailability("AVAILABLE")
                        .setFileFieldDraftId(productDraft.draftId())
                        .setFileFields(Map.of(productField.getCode(), productDraft.fileIds())))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ProductResponse.class);
        assertFileIds(product.getFileFields(), productField.getCode(), productDraft.fileIds());
        assertTypedBinding(jdbc, "PRODUCT", "product_id", product.getId(), productDraft.fileIds());

        createFieldReader(jdbc, FILE_READER, "B29a readable product files", "PRODUCT", productField.getId(),
                "products:read", "NONE", true);
        ProductResponse readable = fromJson(performAs(FILE_READER, TENANT,
                get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ProductResponse.class);
        assertFileIds(readable.getFileFields(), productField.getCode(), productDraft.fileIds());
        createFieldReader(jdbc, MASKED_FILE_READER, "B29a masked product files", "PRODUCT", productField.getId(),
                "products:read", "FULL", true);
        ProductResponse masked = fromJson(performAs(MASKED_FILE_READER, TENANT,
                get("/api/v1/products/{id}", product.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ProductResponse.class);
        assertTrue(masked.getFileFields().isEmpty());

        long productReplacement = stagedReadyCandidates(jdbc, "PRODUCT", productField.getId(), product.getId(), 1)
                .fileIds().getFirst();
        List<Long> productReplaced = List.of(productDraft.fileIds().get(1), productReplacement);
        product = updateProduct(product, productField.getCode(), productReplaced);
        assertFileIds(product.getFileFields(), productField.getCode(), productReplaced);
        assertTypedBinding(jdbc, "PRODUCT", "product_id", product.getId(), productReplaced);
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, productDraft.fileIds().getFirst()));

        List<Long> productReordered = List.of(productReplacement, productDraft.fileIds().get(1));
        product = updateProduct(product, productField.getCode(), productReordered);
        assertFileIds(product.getFileFields(), productField.getCode(), productReordered);
        assertTypedBinding(jdbc, "PRODUCT", "product_id", product.getId(), productReordered);
        product = updateProduct(product, productField.getCode(), List.of());
        assertTrue(product.getFileFields().isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND product_id = ? AND state = 'ACTIVE'", Integer.class, TENANT, product.getId()));

        long customerId = customer(jdbc);
        DraftCandidates visitDraft = stagedReadyCandidates(jdbc, "RETURN_VISIT", visitField.getId(), null, 2);
        ReturnVisitResponse visit = fromJson(performAs(post("/api/v1/return-visits")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new ReturnVisitCreateRequest().setCustomerId(customerId)
                        .setVisitTime(LocalDateTime.of(2030, 1, 2, 3, 4)).setFileFieldDraftId(visitDraft.draftId())
                        .setFileFields(Map.of(visitField.getCode(), visitDraft.fileIds())))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ReturnVisitResponse.class);
        assertFileIds(visit.getFileFields(), visitField.getCode(), visitDraft.fileIds());
        assertTypedBinding(jdbc, "RETURN_VISIT", "return_visit_id", visit.getId(), visitDraft.fileIds());

        long visitReplacement = stagedReadyCandidates(jdbc, "RETURN_VISIT", visitField.getId(), visit.getId(), 1)
                .fileIds().getFirst();
        List<Long> visitReplaced = List.of(visitDraft.fileIds().get(1), visitReplacement);
        visit = updateReturnVisit(visit, visitField.getCode(), visitReplaced);
        assertFileIds(visit.getFileFields(), visitField.getCode(), visitReplaced);
        assertTypedBinding(jdbc, "RETURN_VISIT", "return_visit_id", visit.getId(), visitReplaced);
        assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT publication_state FROM crm_attachment_file"
                + " WHERE tenant_id = ? AND id = ?", String.class, TENANT, visitDraft.fileIds().getFirst()));

        List<Long> visitReordered = List.of(visitReplacement, visitDraft.fileIds().get(1));
        visit = updateReturnVisit(visit, visitField.getCode(), visitReordered);
        assertFileIds(visit.getFileFields(), visitField.getCode(), visitReordered);
        assertTypedBinding(jdbc, "RETURN_VISIT", "return_visit_id", visit.getId(), visitReordered);

        createFieldReader(jdbc, FILE_BLIND_READER, "B29a file-blind return visits", "RETURN_VISIT", visitField.getId(),
                "return-visits:read", "NONE", false);
        ReturnVisitResponse unreadable = fromJson(performAs(FILE_BLIND_READER, TENANT,
                get("/api/v1/return-visits/{id}", visit.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ReturnVisitResponse.class);
        assertTrue(unreadable.getFileFields().isEmpty());
        visit = updateReturnVisit(visit, visitField.getCode(), List.of());
        assertTrue(visit.getFileFields().isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND return_visit_id = ? AND state = 'ACTIVE'", Integer.class, TENANT,
                visit.getId()));
    }

    private void createFieldReader(JdbcTemplate jdbc, long actorId, String roleName, String objectType,
                                   long fieldId, String authorityCode, String maskType, boolean filesRead) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                actorId, roleName);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT);
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, roleName);
        long roleId = jdbc.queryForObject("SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT, roleName);
        jdbc.update("INSERT INTO authorization_role_assignment (tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT, actorId, roleId);
        jdbc.update("INSERT INTO authorization_role_grant (tenant_id, role_id, authority_code, data_scope)"
                        + " VALUES (?, ?, ?, ?)", TENANT, roleId, authorityCode,
                "return-visits:read".equals(authorityCode) ? "TENANT" : null);
        if (filesRead) {
            jdbc.update("INSERT INTO authorization_role_grant (tenant_id, role_id, authority_code) VALUES (?, ?, 'files:read')",
                    TENANT, roleId);
        }
        jdbc.update("INSERT INTO role_field_policy (tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                TENANT, roleId, objectType);
        jdbc.update("INSERT INTO role_field_access (tenant_id, role_id, object_type, field_id, access_level, mask_type)"
                        + " VALUES (?, ?, ?, ?, 'READ', ?)",
                TENANT, roleId, objectType, fieldId, maskType);
    }

    private Parents seed(JdbcTemplate jdbc) {
        seedTenant(jdbc);
        return seedParents(jdbc);
    }

    private void seedTenant(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO organization_member_profile (actor_id, display_name) VALUES (?, ?)",
                TENANT, "B29a typed file owner");
        jdbc.update("INSERT INTO organization_tenant (tenant_id, initialized_by_actor_id) VALUES (?, ?)",
                TENANT, TENANT);
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT, TENANT);
    }

    private Parents seedParents(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO crm_customer (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                TENANT, TENANT, "B29a return-visit customer");
        long customerId = id(jdbc, "crm_customer");
        jdbc.update("INSERT INTO crm_product (tenant_id, owner_actor_id, name, code, code_key, status)"
                + " VALUES (?, ?, 'B29a Product', 'B29A-PRODUCT', 'b29a-product', 'ACTIVE')", TENANT, TENANT);
        long productId = id(jdbc, "crm_product");
        jdbc.update("""
                INSERT INTO crm_return_visit
                    (tenant_id, owner_actor_id, customer_id, visit_number, visit_time, status)
                VALUES (?, ?, ?, 'B29A-RETURN-VISIT', '2030-01-01 00:00:00', 'ACTIVE')
                """, TENANT, TENANT, customerId);
        long returnVisitId = id(jdbc, "crm_return_visit");
        jdbc.update("""
                INSERT INTO custom_field_definition
                    (tenant_id, object_type, field_code, display_name, field_type, is_builtin,
                     is_required, is_unique, is_searchable, status, display_order, options_json)
                VALUES (?, 'PRODUCT', 'cf_b29a_product_file', 'B29a Product FILE', 'FILE',
                        0, 0, 0, 0, 'ACTIVE', 901, JSON_ARRAY()),
                       (?, 'RETURN_VISIT', 'cf_b29a_return_visit_file', 'B29a ReturnVisit FILE', 'FILE',
                        0, 0, 0, 0, 'ACTIVE', 902, JSON_ARRAY())
                """, TENANT, TENANT);
        long productFieldId = jdbc.queryForObject("SELECT id FROM custom_field_definition WHERE tenant_id = ?"
                + " AND object_type = 'PRODUCT' AND field_code = 'cf_b29a_product_file'", Long.class, TENANT);
        long returnVisitFieldId = jdbc.queryForObject("SELECT id FROM custom_field_definition WHERE tenant_id = ?"
                + " AND object_type = 'RETURN_VISIT' AND field_code = 'cf_b29a_return_visit_file'", Long.class,
                TENANT);
        return new Parents(productId, returnVisitId, productFieldId, returnVisitFieldId);
    }

    private void bindProductFile(JdbcTemplate jdbc, long fileId, Parents parents) {
        jdbc.update("""
                INSERT INTO crm_attachment_field_binding
                    (tenant_id, file_id, object_type, field_id, product_id, ordinal, content_version, evidence_digest)
                VALUES (?, ?, 'PRODUCT', ?, ?, 0, 1, UNHEX(REPEAT('11', 32)))
                """, TENANT, fileId, parents.productFieldId(), parents.productId());
    }

    private void bindReturnVisitFile(JdbcTemplate jdbc, long fileId, Parents parents) {
        jdbc.update("""
                INSERT INTO crm_attachment_field_binding
                    (tenant_id, file_id, object_type, field_id, return_visit_id, ordinal, content_version, evidence_digest)
                VALUES (?, ?, 'RETURN_VISIT', ?, ?, 0, 1, UNHEX(REPEAT('22', 32)))
                """, TENANT, fileId, parents.returnVisitFieldId(), parents.returnVisitId());
    }

    private FieldDefinitionResponse createFileField(String objectType, String displayName) throws Exception {
        return fromJson(performAs(post("/api/v1/fields/objects/{objectType}", objectType)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new FieldCreateRequest().setDisplayName(displayName).setFieldType("FILE")
                        .setSearchable(false))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), FieldDefinitionResponse.class);
    }

    private ProductResponse updateProduct(ProductResponse current, String fieldCode, List<Long> fileIds) throws Exception {
        return fromJson(performAs(put("/api/v1/products/{id}", current.getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new ProductUpdateRequest().setVersion(current.getVersion()).setName(current.getName())
                        .setCode(current.getCode()).setCategoryId(current.getCategoryId()).setUnit(current.getUnit())
                        .setListPrice(current.getListPrice()).setDescription(current.getDescription())
                        .setFileFields(Map.of(fieldCode, fileIds)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ProductResponse.class);
    }

    private ReturnVisitResponse updateReturnVisit(ReturnVisitResponse current, String fieldCode, List<Long> fileIds)
            throws Exception {
        return fromJson(performAs(put("/api/v1/return-visits/{id}", current.getId())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new ReturnVisitUpdateRequest().setVersion(current.getVersion())
                        .setVisitTime(current.getVisitTime()).setRemark(current.getRemark())
                        .setFileFields(Map.of(fieldCode, fileIds)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ReturnVisitResponse.class);
    }

    private void assertFileIds(Map<String, List<io.github.archetom.crm.api.dto.response.AttachmentResponse>> values,
                               String fieldCode, List<Long> expected) {
        assertEquals(expected, values.get(fieldCode).stream()
                .map(io.github.archetom.crm.api.dto.response.AttachmentResponse::getFileId).toList());
    }

    private void assertTypedBinding(JdbcTemplate jdbc, String objectType, String targetColumn, long targetId,
                                    List<Long> expected) {
        String otherTargetColumn = "product_id".equals(targetColumn) ? "return_visit_id" : "product_id";
        List<Long> actual = jdbc.queryForList("SELECT file_id FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND object_type = ? AND " + targetColumn + " = ? AND state = 'ACTIVE'"
                + " ORDER BY ordinal", Long.class, TENANT, objectType, targetId);
        assertEquals(expected, actual);
        assertEquals(expected.size(), jdbc.queryForObject("SELECT COUNT(*) FROM crm_attachment_field_binding"
                + " WHERE tenant_id = ? AND object_type = ? AND " + targetColumn + " = ? AND state = 'ACTIVE'"
                + " AND " + otherTargetColumn + " IS NULL", Integer.class, TENANT, objectType, targetId));
    }

    private long customer(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO crm_customer (tenant_id, owner_actor_id, name) VALUES (?, ?, ?)",
                TENANT, TENANT, "B29a API return-visit customer");
        return id(jdbc, "crm_customer");
    }

    private DraftCandidates stagedReadyCandidates(JdbcTemplate jdbc, String objectType, long fieldId,
                                                   Long directTargetId, int count) {
        long draftId = directTargetId == null ? draft(jdbc, objectType) : directDraft(jdbc, objectType, directTargetId);
        java.util.ArrayList<Long> fileIds = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < count; ordinal++) {
            long fileId = readyFile(jdbc, ordinal);
            jdbc.update("INSERT INTO crm_file_field_draft_file"
                    + " (tenant_id, draft_id, object_type, field_id, file_id, ordinal, state)"
                    + " VALUES (?, ?, ?, ?, ?, ?, 'STAGED')", TENANT, draftId, objectType, fieldId, fileId, ordinal);
            fileIds.add(fileId);
        }
        return new DraftCandidates(draftId, List.copyOf(fileIds));
    }

    private long directDraft(JdbcTemplate jdbc, String objectType, long targetId) {
        jdbc.update("INSERT INTO crm_file_field_draft (tenant_id, uploader_actor_id, object_type, target_type, target_id,"
                        + " purpose_digest, deadline, state) VALUES (?, ?, ?, ?, ?, UNHEX(REPEAT('00', 32)),"
                        + " DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 'ACTIVE')",
                TENANT, TENANT, objectType, objectType, targetId);
        return id(jdbc, "crm_file_field_draft");
    }

    private long readyFile(JdbcTemplate jdbc, int ordinal) {
        jdbc.update("INSERT INTO crm_attachment_file (tenant_id, uploader_actor_id, display_name, declared_media_type,"
                        + " expected_bytes, byte_state, inspection_state, publication_state, actual_bytes, media_type)"
                        + " VALUES (?, ?, ?, 'application/pdf', 4, 'STORED', 'CLEAN', 'PRIVATE', 4, 'application/pdf')",
                TENANT, TENANT, "b29a-ready-" + ordinal + ".pdf");
        long fileId = id(jdbc, "crm_attachment_file");
        jdbc.update("INSERT INTO crm_attachment_content (tenant_id, file_id, content_version, intent_version, binding_ref,"
                        + " planned_locator, stored_identity, storage_generation, attempt, actual_bytes, sha256,"
                        + " upload_deadline, stored_time) VALUES (?, ?, 1, 0, X'01', X'02', X'03', X'04', 1, 4,"
                        + " UNHEX(REPEAT('11', 32)), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), UTC_TIMESTAMP(6))",
                TENANT, fileId);
        return fileId;
    }

    private void bootstrap() throws Exception {
        performAs(post("/api/v1/organization/bootstrap")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest().setDisplayName("B29a delete owner"))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions performAs(MockHttpServletRequestBuilder request)
            throws Exception {
        return performAs(TENANT, TENANT, request);
    }

    private org.springframework.test.web.servlet.ResultActions performAs(
            long actorId, long tenantId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    private long attachmentFile(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO crm_attachment_file
                    (tenant_id, uploader_actor_id, display_name, declared_media_type, expected_bytes,
                     byte_state, inspection_state, publication_state)
                VALUES (?, ?, 'b29a-file.pdf', 'application/pdf', 4, 'UPLOADING', 'NOT_RUN', 'PRIVATE')
                """, TENANT, TENANT);
        return id(jdbc, "crm_attachment_file");
    }

    private long draft(JdbcTemplate jdbc, String objectType) {
        jdbc.update("""
                INSERT INTO crm_file_field_draft
                    (tenant_id, uploader_actor_id, object_type, purpose_digest, deadline, state)
                VALUES (?, ?, ?, UNHEX(REPEAT('00', 32)), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 'ACTIVE')
                """, TENANT, TENANT, objectType);
        return id(jdbc, "crm_file_field_draft");
    }

    private long id(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT MAX(id) FROM " + table + " WHERE tenant_id = ?", Long.class, TENANT);
    }

    @Override
    protected void clearTestData() {
        executeSql("DELETE FROM crm_attachment_field_binding WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_file_field_draft_file WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_file_field_draft WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_relation WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_content WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_read_lease WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_job WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_attachment_file WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_return_visit_unique_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_return_visit_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_unique_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_field_value WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product_catalog_guard WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM role_field_access WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM role_field_policy WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM custom_field_definition WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM customization_object_guard WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_return_visit WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_product WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_customer WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_activity_relation WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM crm_activity WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM navigation_preference WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role_assignment WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role_grant WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM authorization_role WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_membership_request WHERE target_tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_membership WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_department WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_tenant WHERE tenant_id = " + TENANT);
        executeSql("DELETE FROM organization_member_profile WHERE actor_id BETWEEN " + TENANT
                + " AND " + FILE_BLIND_READER);
    }

    private record Parents(long productId, long returnVisitId, long productFieldId, long returnVisitFieldId) { }
    private record DraftCandidates(long draftId, List<Long> fileIds) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class AttachmentPolicyConfiguration {
        @Bean
        @Primary
        AttachmentRuntimePolicy b29aAttachmentRuntimePolicy() {
            Duration second = Duration.ofSeconds(1);
            return new AttachmentRuntimePolicy(true, 16, 64, 64, 2, 2, 2, 2, 128, 1,
                    second, second, second, second, second, second, second, second,
                    Duration.ofSeconds(5), Duration.ofMillis(20), 3, second, Duration.ofSeconds(600), 1);
        }
    }
}
