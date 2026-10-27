package io.github.archetom.crm;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.FilterConditionRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityProductLineRequest;
import io.github.archetom.crm.api.dto.request.OpportunityProductsUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ProductAvailabilityUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCategoryUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.api.dto.request.ProductUpdateRequest;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.OpportunityProductsResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.dto.response.ProductCategoryResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.application.service.OpportunityProductService;
import io.github.archetom.crm.application.operation.UseCaseOperation;
import io.github.archetom.crm.application.service.template.QueryServiceTemplate;
import io.github.archetom.crm.application.service.template.ServiceOperation;
import io.github.archetom.crm.application.vo.OpportunityProductsVO;
import io.github.archetom.crm.domain.policy.AuthorityCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.repository.OpportunityRepository;
import io.github.archetom.crm.domain.repository.ProductRepository;
import io.github.archetom.crm.domain.valueobject.OpportunityId;
import io.github.archetom.crm.domain.valueobject.ProductId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end B12 coverage against the production MySQL mappings. */
@RequiresDatabase
class ProductIntegrationTest extends BaseIntegrationTest {

    private static final long OWNER_ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Autowired
    private OpportunityProductService opportunityProductService;
    @Autowired
    private QueryServiceTemplate queryServiceTemplate;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OpportunityRepository opportunityRepository;

    @Test
    void productListSupportsSafeServerSideSorting() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product sort owner");
        ProductResponse zebra = createProduct("Zebra product", "SORT-Z", null,
                new BigDecimal("10.00"), Map.of());
        ProductResponse alpha = createProduct("Aardvark product", "SORT-A", null,
                new BigDecimal("10.00"), Map.of());

        performPost("/api/v1/products/query", new ProductQueryRequest()
                .setSortField("NAME").setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(zebra.getId()));
        performPost("/api/v1/products/query", new ProductQueryRequest()
                .setSortField("LIST_PRICE").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());

        ProductQueryRequest temporaryName = new ProductQueryRequest().setConditions(List.of(
                new FilterConditionRequest().setFieldCode("product_name").setOperator("EQ")
                        .setValues(List.of("Aardvark product"))));
        performPost("/api/v1/products/query", temporaryName)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/products/query", new ProductQueryRequest()
                .setSavedViewId(1L).setConditions(temporaryName.getConditions()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productCsvExportUsesTheAuthorizedBoundedProjectionAndCurrentFilters()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product CSV export owner");
        ProductResponse included = fromJson(performPost("/api/v1/products",
                productRequest("CSV export included", "CSV-INCLUDED", null, Map.of())
                        .setListPrice(new BigDecimal("12.50")).setDescription("=formula"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ProductResponse.class);
        createProduct("CSV export excluded", "CSV-EXCLUDED", null,
                new BigDecimal("4.00"), Map.of());

        performPost("/api/v1/products/export/csv", new ProductCsvExportRequest()
                .setFieldCodes(List.of(ProductFieldCatalog.NAME, ProductFieldCatalog.CODE,
                        ProductFieldCatalog.LIST_PRICE, ProductFieldCatalog.DESCRIPTION))
                .setName("CSV export included"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"products.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("\uFEFFproduct_id,product_name,product_code,list_price,description\r\n"
                                + included.getId() + ",CSV export included,CSV-INCLUDED,12.50,'=formula\r\n"));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long permittedExporter = 700003L;
        seedActiveMember(permittedExporter, "Product CSV permitted exporter");
        Long exportRole = createRole(jdbc, "Product CSV export role");
        grant(jdbc, exportRole, AuthorityCatalog.PRODUCTS_READ, null);
        grant(jdbc, exportRole, AuthorityCatalog.PRODUCTS_EXPORT, null);
        assignRole(jdbc, permittedExporter, exportRole);
        createFieldPolicy(jdbc, exportRole, "PRODUCT");
        addFieldAccesses(jdbc, exportRole, "PRODUCT", "READ", "NONE",
                List.of(ProductFieldCatalog.NAME));

        performAs(permittedExporter, TENANT_ID, post("/api/v1/products/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                new ProductCsvExportRequest().setFieldCodes(
                                        List.of(ProductFieldCatalog.NAME)))))
                .andExpect(status().isOk());
        performAs(permittedExporter, TENANT_ID, post("/api/v1/products/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                new ProductCsvExportRequest().setFieldCodes(
                                        List.of(ProductFieldCatalog.LIST_PRICE)))))
                .andExpect(status().isForbidden());

        long readOnlyActor = 700004L;
        seedActiveMember(readOnlyActor, "Product CSV read only");
        Long readRole = createRole(jdbc, "Product CSV read role");
        grant(jdbc, readRole, AuthorityCatalog.PRODUCTS_READ, null);
        assignRole(jdbc, readOnlyActor, readRole);
        performAs(readOnlyActor, TENANT_ID, post("/api/v1/products/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(
                                new ProductCsvExportRequest().setFieldCodes(
                                        List.of(ProductFieldCatalog.NAME)))))
                .andExpect(status().isForbidden());
    }

    @Test
    void productCustomNumericFieldSortUsesCanonicalNumericValues() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product numeric sort owner");
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/PRODUCT",
                new FieldCreateRequest().setDisplayName("Product score")
                        .setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ProductResponse two = createProduct("Two score", "PRODUCT-SORT-2", null,
                new BigDecimal("10.00"), Map.of(score.getCode(), List.of("2")));
        ProductResponse ten = createProduct("Ten score", "PRODUCT-SORT-10", null,
                new BigDecimal("10.00"), Map.of(score.getCode(), List.of("10")));

        performPost("/api/v1/products/query", new ProductQueryRequest()
                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(two.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(ten.getId()));
        performPost("/api/v1/products/query", new ProductQueryRequest()
                .setSortField("NAME").setCustomSortFieldCode(score.getCode())
                .setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/products/query", new ProductQueryRequest()
                .setCustomSortFieldCode(ProductFieldCatalog.NAME).setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogLifecycleAndOpportunitySnapshotsRemainTenantSafeAndStable()
            throws Exception {
        mockMvc.perform(post("/api/v1/products/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product owner");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertEquals(11, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'PRODUCT' "
                        + "AND is_builtin = 1",
                Integer.class, TENANT_ID));
        assertEquals(18, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'OPPORTUNITY' "
                        + "AND is_builtin = 1",
                Integer.class, TENANT_ID));

        ProductCategoryResponse root = createCategory(null, "Services");
        ProductCategoryResponse child = createCategory(root.getId(), "Subscriptions");
        performPut("/api/v1/product-categories/" + root.getId(),
                new ProductCategoryUpdateRequest().setVersion(root.getVersion())
                        .setParentCategoryId(child.getId()).setName("Services"))
                .andExpect(status().isBadRequest());

        FieldDefinitionResponse unique = fromJson(performPost(
                "/api/v1/fields/objects/PRODUCT",
                new FieldCreateRequest().setDisplayName("Catalog identity")
                        .setFieldType("TEXT").setUnique(true))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ProductResponse product = createProduct("Starter plan", "PLAN-A", child.getId(),
                new BigDecimal("20.00"),
                Map.of(unique.getCode(), List.of("offer-1")));
        performPost("/api/v1/products",
                productRequest("Duplicate", "plan-a", child.getId(),
                        Map.of(unique.getCode(), List.of("offer-2"))))
                .andExpect(status().isConflict());
        performPost("/api/v1/products",
                productRequest("Unique collision", "PLAN-B", child.getId(),
                        Map.of(unique.getCode(), List.of("ＯＦＦＥＲ-1"))))
                .andExpect(status().isConflict());
        performPost("/api/v1/products/query",
                new ProductQueryRequest().setCategoryId(child.getId()).setName("Starter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1))
                .andExpect(jsonPath("$.objectList[0].categoryName")
                        .value("Subscriptions"));

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/product-categories/{categoryId}", child.getId())
                        .param("version", child.getVersion().toString()))
                .andExpect(status().isUnprocessableEntity());

        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Product account"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        OpportunityResponse opportunity = fromJson(performPost("/api/v1/opportunities",
                new OpportunityCreateRequest().setName("Product renewal")
                        .setCustomerId(customer.getId()).setPipelineCode("direct")
                        .setStageCode("qualification")
                        .setExpectedCloseDate(LocalDate.of(2027, 3, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        OpportunityProductsResponse attached = fromJson(performPut(
                "/api/v1/opportunities/" + opportunity.getId() + "/products",
                new OpportunityProductsUpdateRequest().setVersion(opportunity.getVersion())
                        .setLines(List.of(new OpportunityProductLineRequest()
                                .setProductId(product.getId())
                                .setQuantity(new BigDecimal("2.5000"))
                                .setUnitPrice(new BigDecimal("18.00")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityProductsResponse.class);
        assertEquals(new BigDecimal("45.00"), attached.getTotalAmount());
        assertEquals(new BigDecimal("10.0000"),
                attached.getLines().getFirst().getDiscountRate());

        ProductResponse renamed = fromJson(performPut(
                "/api/v1/products/" + product.getId(),
                new ProductUpdateRequest().setVersion(product.getVersion())
                        .setName("Renamed plan").setCode(product.getCode())
                        .setCategoryId(child.getId()).setUnit("license")
                        .setListPrice(new BigDecimal("30.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ProductResponse.class);
        assertEquals(List.of("offer-1"), renamed.getCustomFields().get(unique.getCode()));
        ProductResponse unavailable = fromJson(performPut(
                "/api/v1/products/" + product.getId() + "/availability",
                new ProductAvailabilityUpdateRequest().setVersion(renamed.getVersion())
                        .setAvailability("UNAVAILABLE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ProductResponse.class);
        performGet("/api/v1/opportunities/{opportunityId}/products", opportunity.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].productName").value("Starter plan"))
                .andExpect(jsonPath("$.lines[0].unit").value("seat"))
                .andExpect(jsonPath("$.lines[0].listPrice").value(20.00));
        performPut("/api/v1/opportunities/" + opportunity.getId() + "/products",
                new OpportunityProductsUpdateRequest().setVersion(attached.getVersion())
                        .setLines(List.of(new OpportunityProductLineRequest()
                                .setProductId(product.getId())
                                .setQuantity(BigDecimal.ONE))))
                .andExpect(status().isNotFound());

        performAs(OWNER_ACTOR_ID, TENANT_ID,
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/products/{productId}", product.getId())
                        .param("version", unavailable.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/opportunities/{opportunityId}/products", opportunity.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(45.00));

        ProductResponse replacement = createProduct(
                "Replacement", "plan-a", null, new BigDecimal("25.00"), Map.of());
        performAs(OWNER_ACTOR_ID, TENANT_ID,
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/product-categories/{categoryId}", child.getId())
                        .param("version", child.getVersion().toString()))
                .andExpect(status().isOk());
        performGet("/api/v1/products/{productId}", replacement.getId())
                .andExpect(status().isOk());
    }

    @Test
    void productCapabilitiesAndTenantBoundariesFailClosed() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Catalog owner");
        ProductResponse product = createProduct(
                "Tenant product", "TENANT-ONLY", null,
                new BigDecimal("5.00"), Map.of());

        long reader = 777777L;
        seedActiveMember(reader, "Catalog reader");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Product reader");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Product reader");
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code) "
                        + "VALUES (?, ?, 'products:read')",
                TENANT_ID, roleId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, reader, roleId);
        performAs(reader, TENANT_ID,
                get("/api/v1/products/{productId}", product.getId()))
                .andExpect(status().isOk());
        performAs(reader, TENANT_ID,
                post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(productRequest(
                                "Denied", "DENIED", null, Map.of()))))
                .andExpect(status().isForbidden());

        long otherTenant = 123456L;
        long otherOwner = 654321L;
        bootstrap(otherOwner, otherTenant, "Other owner");
        performAs(otherOwner, otherTenant,
                get("/api/v1/products/{productId}", product.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void fieldPoliciesProtectSnapshotsAndAllowMinimalProductCreation()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product policy owner");
        ProductResponse product = createProduct(
                "Protected product", "PROTECTED", null,
                new BigDecimal("42.00"), Map.of());
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Protected account"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        OpportunityResponse opportunity = fromJson(performPost("/api/v1/opportunities",
                new OpportunityCreateRequest().setName("Protected opportunity")
                        .setCustomerId(customer.getId()).setPipelineCode("direct")
                        .setStageCode("qualification")
                        .setExpectedCloseDate(LocalDate.of(2027, 4, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long minimalWriter = 700001L;
        seedActiveMember(minimalWriter, "Minimal product writer");
        Long writerRole = createRole(jdbc, "Minimal product writer role");
        grant(jdbc, writerRole, AuthorityCatalog.PRODUCTS_WRITE, null);
        assignRole(jdbc, minimalWriter, writerRole);
        createFieldPolicy(jdbc, writerRole, "PRODUCT");
        addFieldAccesses(jdbc, writerRole, "PRODUCT", "WRITE", "NONE",
                List.of("product_name", "product_code", "list_price"));

        performAs(minimalWriter, TENANT_ID,
                post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ProductCreateRequest()
                                .setName("Minimal product").setCode("MINIMAL")
                                .setListPrice(new BigDecimal("1.00")))))
                .andExpect(status().isOk());

        long maskedReader = 700002L;
        seedActiveMember(maskedReader, "Masked product reader");
        Long readerRole = createRole(jdbc, "Masked snapshot reader role");
        grant(jdbc, readerRole, AuthorityCatalog.PRODUCTS_READ, null);
        grant(jdbc, readerRole, AuthorityCatalog.OPPORTUNITIES_READ, "TENANT");
        grant(jdbc, readerRole, AuthorityCatalog.OPPORTUNITIES_WRITE, "TENANT");
        assignRole(jdbc, maskedReader, readerRole);
        createFieldPolicy(jdbc, readerRole, "OPPORTUNITY");
        addFieldAccesses(jdbc, readerRole, "OPPORTUNITY", "WRITE", "NONE",
                List.of("products"));
        createFieldPolicy(jdbc, readerRole, "PRODUCT");
        addFieldAccesses(jdbc, readerRole, "PRODUCT", "READ", "NONE",
                List.of("product_name", "product_code", "category", "unit"));
        addFieldAccesses(jdbc, readerRole, "PRODUCT", "READ", "PARTIAL",
                List.of("list_price"));

        OpportunityProductsUpdateRequest attach = new OpportunityProductsUpdateRequest()
                .setVersion(opportunity.getVersion())
                .setLines(List.of(new OpportunityProductLineRequest()
                        .setProductId(product.getId()).setQuantity(BigDecimal.ONE)));
        performAs(maskedReader, TENANT_ID,
                put("/api/v1/opportunities/{opportunityId}/products",
                        opportunity.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(attach)))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_opportunity_product_line "
                        + "WHERE tenant_id = ? AND opportunity_id = ?",
                Integer.class, TENANT_ID, opportunity.getId()));

        jdbc.update("UPDATE role_field_access SET mask_type = 'NONE' "
                        + "WHERE tenant_id = ? AND role_id = ? AND object_type = 'PRODUCT' "
                        + "AND field_id = (SELECT id FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'PRODUCT' "
                        + "AND field_code = 'list_price')",
                TENANT_ID, readerRole, TENANT_ID);
        OpportunityProductsResponse attached = fromJson(
                performAs(maskedReader, TENANT_ID,
                        put("/api/v1/opportunities/{opportunityId}/products",
                                opportunity.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(attach)))
                        .andExpect(status().isOk()).andReturn().getResponse()
                        .getContentAsString(), OpportunityProductsResponse.class);
        assertEquals(new BigDecimal("42.00"), attached.getTotalAmount());

        jdbc.update("UPDATE role_field_access SET mask_type = 'PARTIAL' "
                        + "WHERE tenant_id = ? AND role_id = ? AND object_type = 'PRODUCT' "
                        + "AND field_id = (SELECT id FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'PRODUCT' "
                        + "AND field_code = 'product_code')",
                TENANT_ID, readerRole, TENANT_ID);
        performAs(maskedReader, TENANT_ID,
                get("/api/v1/opportunities/{opportunityId}/products",
                        opportunity.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void snapshotUsesOneCatalogStateAndMissingVersionIsStable() throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product consistency owner");
        ProductResponse product = createProduct(
                "Movable product", "MOVABLE", null,
                new BigDecimal("9.00"), Map.of());
        CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName("Consistency account"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        OpportunityResponse opportunity = fromJson(performPost("/api/v1/opportunities",
                new OpportunityCreateRequest().setName("Consistency opportunity")
                        .setCustomerId(customer.getId()).setPipelineCode("direct")
                        .setStageCode("qualification")
                        .setExpectedCloseDate(LocalDate.of(2027, 5, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        AuthenticatedCaller caller = ownerCaller();
        OpportunityProductsUpdateRequest missingVersion =
                new OpportunityProductsUpdateRequest().setLines(List.of());
        Result<OpportunityProductsVO> missingVersionResult = opportunityProductService
                .replace(caller, opportunity.getId(), missingVersion);
        assertFalse(missingVersionResult.isSuccess());
        assertEquals("200", missingVersionResult.getErrorContext().fetchRootError()
                .getErrorCode().getErrorSpecific());

        Connection writer = dataSource.getConnection();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            writer.setAutoCommit(false);
            try (PreparedStatement guard = writer.prepareStatement(
                    "INSERT INTO crm_product_catalog_guard (tenant_id) VALUES (?) "
                            + "ON DUPLICATE KEY UPDATE tenant_id = "
                            + "crm_product_catalog_guard.tenant_id")) {
                guard.setLong(1, TENANT_ID);
                guard.executeUpdate();
            }
            long categoryId;
            try (PreparedStatement insert = writer.prepareStatement(
                    "INSERT INTO crm_product_category "
                            + "(tenant_id, name, name_key, status) "
                            + "VALUES (?, ?, ?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, TENANT_ID);
                insert.setString(2, "Concurrent category");
                insert.setString(3, "concurrent category");
                insert.executeUpdate();
                try (var keys = insert.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    categoryId = keys.getLong(1);
                }
            }
            try (PreparedStatement update = writer.prepareStatement(
                    "UPDATE crm_product SET category_id = ?, version = version + 1 "
                            + "WHERE tenant_id = ? AND id = ?")) {
                update.setLong(1, categoryId);
                update.setLong(2, TENANT_ID);
                update.setLong(3, product.getId());
                assertEquals(1, update.executeUpdate());
            }

            CountDownLatch started = new CountDownLatch(1);
            OpportunityProductsUpdateRequest request =
                    new OpportunityProductsUpdateRequest()
                            .setVersion(opportunity.getVersion())
                            .setLines(List.of(new OpportunityProductLineRequest()
                                    .setProductId(product.getId())
                                    .setQuantity(BigDecimal.ONE)));
            Future<Result<OpportunityProductsVO>> future = executor.submit(() -> {
                started.countDown();
                return opportunityProductService.replace(
                        caller, opportunity.getId(), request);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,
                    () -> future.get(500, TimeUnit.MILLISECONDS));
            writer.commit();

            Result<OpportunityProductsVO> result = future.get(10, TimeUnit.SECONDS);
            assertTrue(result.isSuccess());
            assertEquals("Concurrent category",
                    result.getData().lines().getFirst().categoryName());
        } finally {
            try {
                if (!writer.getAutoCommit()) {
                    writer.rollback();
                }
            } finally {
                writer.close();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void aggregateQueriesKeepRootsAndChildrenOnOneRepeatableReadSnapshot()
            throws Exception {
        bootstrap(OWNER_ACTOR_ID, TENANT_ID, "Product snapshot reader");
        FieldDefinitionResponse field = fromJson(performPost(
                "/api/v1/fields/objects/PRODUCT",
                new FieldCreateRequest().setDisplayName("Snapshot marker")
                        .setFieldType("TEXT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ProductResponse product = createProduct(
                "Snapshot product", "SNAPSHOT", null, new BigDecimal("12.00"),
                Map.of(field.getCode(), List.of("before")));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch productRootRead = new CountDownLatch(1);
            CountDownLatch productCommit = new CountDownLatch(1);
            Future<Result<ProductReadSnapshot>> productFuture = executor.submit(() ->
                    queryServiceTemplate.execute(UseCaseOperation.PRODUCT_GET,
                            new ServiceOperation<>() {
                                @Override
                                public ProductReadSnapshot execute() {
                                    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                                    Long rootVersion = jdbc.queryForObject(
                                            "SELECT version FROM crm_product "
                                                    + "WHERE tenant_id = ? AND id = ?",
                                            Long.class, TENANT_ID, product.getId());
                                    productRootRead.countDown();
                                    awaitCommit(productCommit);
                                    var aggregate = productRepository.findById(
                                                    new TenantId(TENANT_ID),
                                                    new ProductId(product.getId()))
                                            .orElseThrow();
                                    String value = aggregate.getCustomFieldValues()
                                            .stream()
                                            .filter(candidate -> candidate.fieldCode()
                                                    .getValue().equals(field.getCode()))
                                            .findFirst().orElseThrow().values().getFirst();
                                    return new ProductReadSnapshot(rootVersion,
                                            aggregate.getVersion(), value);
                                }
                            }));
            assertTrue(productRootRead.await(2, TimeUnit.SECONDS));
            updateProductAfterRootRead(product, field, productCommit);
            Result<ProductReadSnapshot> productResult = productFuture.get(
                    10, TimeUnit.SECONDS);
            assertTrue(productResult.isSuccess());
            assertEquals(product.getVersion(), productResult.getData().rootVersion());
            assertEquals(product.getVersion(),
                    productResult.getData().aggregateVersion());
            assertEquals("before", productResult.getData().customValue());

            CustomerResponse customer = fromJson(performPost("/api/v1/customers",
                    new CustomerCreateRequest().setName("Snapshot account"))
                    .andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(), CustomerResponse.class);
            OpportunityResponse opportunity = fromJson(performPost(
                    "/api/v1/opportunities",
                    new OpportunityCreateRequest().setName("Snapshot opportunity")
                            .setCustomerId(customer.getId()).setPipelineCode("direct")
                            .setStageCode("qualification")
                            .setExpectedCloseDate(LocalDate.of(2027, 6, 1)))
                    .andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(), OpportunityResponse.class);
            OpportunityProductsResponse attached = fromJson(performPut(
                    "/api/v1/opportunities/" + opportunity.getId() + "/products",
                    new OpportunityProductsUpdateRequest()
                            .setVersion(opportunity.getVersion())
                            .setLines(List.of(new OpportunityProductLineRequest()
                                    .setProductId(product.getId())
                                    .setQuantity(BigDecimal.ONE))))
                    .andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(), OpportunityProductsResponse.class);

            CountDownLatch opportunityRootRead = new CountDownLatch(1);
            CountDownLatch opportunityCommit = new CountDownLatch(1);
            Future<Result<OpportunityReadSnapshot>> opportunityFuture = executor.submit(() ->
                    queryServiceTemplate.execute(UseCaseOperation.OPPORTUNITY_PRODUCT_GET,
                            new ServiceOperation<>() {
                                @Override
                                public OpportunityReadSnapshot execute() {
                                    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                                    Long rootVersion = jdbc.queryForObject(
                                            "SELECT version FROM crm_opportunity "
                                                    + "WHERE tenant_id = ? AND id = ?",
                                            Long.class, TENANT_ID, opportunity.getId());
                                    opportunityRootRead.countDown();
                                    awaitCommit(opportunityCommit);
                                    var aggregate = opportunityRepository.findById(
                                                    new TenantId(TENANT_ID),
                                                    new OpportunityId(opportunity.getId()))
                                            .orElseThrow();
                                    return new OpportunityReadSnapshot(rootVersion,
                                            aggregate.getVersion(), aggregate
                                                    .getProductLines().getFirst()
                                                    .productName());
                                }
                            }));
            assertTrue(opportunityRootRead.await(2, TimeUnit.SECONDS));
            updateOpportunityAfterRootRead(
                    opportunity, opportunityCommit);
            Result<OpportunityReadSnapshot> opportunityResult = opportunityFuture.get(
                    10, TimeUnit.SECONDS);
            assertTrue(opportunityResult.isSuccess());
            assertEquals(attached.getVersion(),
                    opportunityResult.getData().rootVersion());
            assertEquals(attached.getVersion(),
                    opportunityResult.getData().aggregateVersion());
            assertEquals("Snapshot product",
                    opportunityResult.getData().productName());
        } finally {
            executor.shutdownNow();
        }
    }

    private void updateProductAfterRootRead(
            ProductResponse product, FieldDefinitionResponse field,
            CountDownLatch committed) throws Exception {
        try (Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            try {
                try (PreparedStatement root = writer.prepareStatement(
                        "UPDATE crm_product SET description = ?, version = version + 1 "
                                + "WHERE tenant_id = ? AND id = ?")) {
                    root.setString(1, "after");
                    root.setLong(2, TENANT_ID);
                    root.setLong(3, product.getId());
                    assertEquals(1, root.executeUpdate());
                }
                try (PreparedStatement child = writer.prepareStatement(
                        "UPDATE crm_product_field_value SET values_json = JSON_ARRAY(?), "
                                + "scalar_value = ? WHERE tenant_id = ? "
                                + "AND product_id = ? AND field_id = ?")) {
                    child.setString(1, "after");
                    child.setString(2, "after");
                    child.setLong(3, TENANT_ID);
                    child.setLong(4, product.getId());
                    child.setLong(5, field.getId());
                    assertEquals(1, child.executeUpdate());
                }
                writer.commit();
            } catch (Exception exception) {
                writer.rollback();
                throw exception;
            } finally {
                committed.countDown();
            }
        }
    }

    private void updateOpportunityAfterRootRead(
            OpportunityResponse opportunity, CountDownLatch committed)
            throws Exception {
        try (Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            try {
                try (PreparedStatement root = writer.prepareStatement(
                        "UPDATE crm_opportunity SET version = version + 1 "
                                + "WHERE tenant_id = ? AND id = ?")) {
                    root.setLong(1, TENANT_ID);
                    root.setLong(2, opportunity.getId());
                    assertEquals(1, root.executeUpdate());
                }
                try (PreparedStatement child = writer.prepareStatement(
                        "UPDATE crm_opportunity_product_line SET product_name = ? "
                                + "WHERE tenant_id = ? AND opportunity_id = ?")) {
                    child.setString(1, "Changed snapshot");
                    child.setLong(2, TENANT_ID);
                    child.setLong(3, opportunity.getId());
                    assertEquals(1, child.executeUpdate());
                }
                writer.commit();
            } catch (Exception exception) {
                writer.rollback();
                throw exception;
            } finally {
                committed.countDown();
            }
        }
    }

    private void awaitCommit(CountDownLatch committed) {
        try {
            if (!committed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent writer did not commit");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while waiting for concurrent writer", exception);
        }
    }

    private record ProductReadSnapshot(
            Long rootVersion, Long aggregateVersion, String customValue) {
    }

    private record OpportunityReadSnapshot(
            Long rootVersion, Long aggregateVersion, String productName) {
    }

    private Long createRole(JdbcTemplate jdbc, String name) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, name);
        return jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, name);
    }

    private void grant(
            JdbcTemplate jdbc, Long roleId, String authority, String dataScope) {
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, ?, ?)",
                TENANT_ID, roleId, authority, dataScope);
    }

    private void assignRole(JdbcTemplate jdbc, long actorId, Long roleId) {
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
    }

    private void createFieldPolicy(
            JdbcTemplate jdbc, Long roleId, String objectType) {
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                TENANT_ID, roleId, objectType);
    }

    private void addFieldAccesses(
            JdbcTemplate jdbc, Long roleId, String objectType,
            String accessLevel, String maskType, List<String> fieldCodes) {
        for (String fieldCode : fieldCodes) {
            jdbc.update("INSERT INTO role_field_access "
                            + "(tenant_id, role_id, object_type, field_id, "
                            + "access_level, mask_type) "
                            + "SELECT ?, ?, ?, id, ?, ? FROM custom_field_definition "
                            + "WHERE tenant_id = ? AND object_type = ? AND field_code = ?",
                    TENANT_ID, roleId, objectType, accessLevel, maskType,
                    TENANT_ID, objectType, fieldCode);
        }
    }

    private AuthenticatedCaller ownerCaller() {
        return new AuthenticatedCaller(OWNER_ACTOR_ID, TENANT_ID,
                Set.of(AuthorityCatalog.OPPORTUNITIES_WRITE,
                        AuthorityCatalog.PRODUCTS_READ));
    }

    private ProductCategoryResponse createCategory(Long parentId, String name)
            throws Exception {
        return fromJson(performPost("/api/v1/product-categories",
                new ProductCategoryCreateRequest().setParentCategoryId(parentId)
                        .setName(name))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ProductCategoryResponse.class);
    }

    private ProductResponse createProduct(
            String name, String code, Long categoryId, BigDecimal price,
            Map<String, List<String>> customFields) throws Exception {
        return fromJson(performPost("/api/v1/products",
                productRequest(name, code, categoryId, customFields).setListPrice(price))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ProductResponse.class);
    }

    private ProductCreateRequest productRequest(
            String name, String code, Long categoryId,
            Map<String, List<String>> customFields) {
        return new ProductCreateRequest().setName(name).setCode(code)
                .setCategoryId(categoryId).setUnit("seat")
                .setListPrice(new BigDecimal("10.00"))
                .setAvailability("AVAILABLE").setCustomFields(customFields);
    }

    private void bootstrap(long actorId, long tenantId, String displayName)
            throws Exception {
        performAs(actorId, tenantId, post("/api/v1/organization/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new OrganizationBootstrapRequest()
                        .setDisplayName(displayName))))
                .andExpect(status().isOk());
    }

    private void seedActiveMember(long actorId, String displayName) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile "
                        + "(actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, actorId);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                actorId, TENANT_ID);
    }

    private ResultActions performAs(
            long actorId, long tenantId,
            MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId));
    }

    @Override
    protected void initTestData() {
        clearProductData();
    }

    @Override
    protected void clearTestData() {
        clearProductData();
    }

    private void clearProductData() {
        executeSql("DELETE FROM crm_opportunity_product_line");
        executeSql("DELETE FROM crm_opportunity_star");
        executeSql("DELETE FROM crm_opportunity_unique_field_value");
        executeSql("DELETE FROM crm_opportunity_field_value");
        executeSql("DELETE FROM crm_opportunity_collaborator");
        executeSql("DELETE FROM crm_opportunity_contact");
        executeSql("DELETE FROM crm_opportunity");
        executeSql("DELETE FROM crm_product_unique_field_value");
        executeSql("DELETE FROM crm_product_field_value");
        executeSql("DELETE FROM crm_product");
        executeSql("UPDATE crm_product_category SET parent_category_id = NULL");
        executeSql("DELETE FROM crm_product_category");
        executeSql("DELETE FROM crm_product_catalog_guard");
        executeSql("DELETE FROM crm_customer_star");
        executeSql("DELETE FROM crm_customer_unique_field_value");
        executeSql("DELETE FROM crm_customer_field_value");
        executeSql("DELETE FROM crm_customer_collaborator");
        executeSql("DELETE FROM crm_customer_tag");
        executeSql("DELETE FROM crm_customer_address");
        executeSql("UPDATE crm_customer SET parent_customer_id = NULL");
        executeSql("DELETE FROM crm_customer");
        executeSql("DELETE FROM crm_customer_guard");
        executeSql("DELETE FROM saved_view");
        executeSql("DELETE FROM role_field_access");
        executeSql("DELETE FROM role_field_policy");
        executeSql("DELETE FROM field_layout_preference");
        executeSql("DELETE FROM custom_field_definition");
        executeSql("DELETE FROM customization_object_guard");
        executeSql("DELETE FROM navigation_preference");
        executeSql("DELETE FROM authorization_role_assignment");
        executeSql("DELETE FROM authorization_role_grant");
        executeSql("DELETE FROM authorization_role");
        executeSql("DELETE FROM organization_membership_request");
        executeSql("DELETE FROM organization_membership");
        executeSql("DELETE FROM organization_department");
        executeSql("DELETE FROM organization_member_profile");
        executeSql("DELETE FROM organization_tenant");
    }
}
