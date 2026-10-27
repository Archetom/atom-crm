package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.request.ApprovalConditionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalActionItemRequest;
import io.github.archetom.crm.api.dto.request.ApprovalBatchActionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionRequest;
import io.github.archetom.crm.api.dto.request.ApprovalDefinitionStatusRequest;
import io.github.archetom.crm.api.dto.request.ApprovalQueryRequest;
import io.github.archetom.crm.api.dto.request.ApprovalStepRequest;
import io.github.archetom.crm.api.dto.request.ApprovalSubmitRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorAddRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCollaboratorRemoveRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchCsvExportRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ContractCollaboratorRequest;
import io.github.archetom.crm.api.dto.request.ContractCreateRequest;
import io.github.archetom.crm.api.dto.request.ContractOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractProductLineRequest;
import io.github.archetom.crm.api.dto.request.ContractProductsUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.ContractUpdateRequest;
import io.github.archetom.crm.api.dto.request.ContractVersionRequest;
import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.ContactUpdateRequest;
import io.github.archetom.crm.api.dto.request.CustomerCreateRequest;
import io.github.archetom.crm.api.dto.request.FieldCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceCreateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchResetRequest;
import io.github.archetom.crm.api.dto.request.InvoiceBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.InvoiceIssueRequest;
import io.github.archetom.crm.api.dto.request.InvoiceOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileRequest;
import io.github.archetom.crm.api.dto.request.InvoiceProfileUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.InvoiceUpdateRequest;
import io.github.archetom.crm.api.dto.request.InvoiceVersionRequest;
import io.github.archetom.crm.api.dto.request.OpportunityCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityOutcomeUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityUpdateRequest;
import io.github.archetom.crm.api.dto.request.OrganizationBootstrapRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchDeleteRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchOwnerUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableBatchVersionRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanBatchCreateRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanDraftRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanUpdateRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivableUpdateRequest;
import io.github.archetom.crm.api.dto.response.ApprovalDefinitionResponse;
import io.github.archetom.crm.api.dto.response.ApprovalInstanceResponse;
import io.github.archetom.crm.api.dto.response.ContractResponse;
import io.github.archetom.crm.api.dto.response.ContractProductsResponse;
import io.github.archetom.crm.api.dto.response.ContactResponse;
import io.github.archetom.crm.api.dto.response.CoreReportResponse;
import io.github.archetom.crm.api.dto.response.CustomerResponse;
import io.github.archetom.crm.api.dto.response.FieldDefinitionResponse;
import io.github.archetom.crm.api.dto.response.InvoiceProfileResponse;
import io.github.archetom.crm.api.dto.response.InvoiceResponse;
import io.github.archetom.crm.api.dto.response.OpportunityResponse;
import io.github.archetom.crm.api.dto.response.ProductResponse;
import io.github.archetom.crm.api.dto.response.ReceivablePlanResponse;
import io.github.archetom.crm.api.dto.response.ReceivableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** End-to-end B13 coverage against the production MySQL mappings. */
@RequiresDatabase
class ContractInvoiceIntegrationTest extends BaseIntegrationTest {
    private static final long ACTOR_ID = 888888L;
    private static final long TENANT_ID = 999999L;

    @Test
    void contractListSupportsSafeServerSideSorting() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Contract sort customer");
        ProductResponse product = product();
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/CONTRACT",
                new FieldCreateRequest().setDisplayName("Contract score")
                        .setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ContractResponse zebra = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setName("Zebra contract").setNumber("SORT-Z")
                        .setCustomFields(Map.of(score.getCode(), List.of("10"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse alpha = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setName("Aardvark contract").setNumber("SORT-A")
                        .setCustomFields(Map.of(score.getCode(), List.of("2"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setSortField("NAME").setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(zebra.getId()));
        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(zebra.getId()));
        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setSortField("NAME").setCustomSortFieldCode(score.getCode())
                .setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setCustomSortFieldCode("contract_number").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setSortField("TOTAL_AMOUNT").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contractSummaryUsesTheSameAuthorizedFiltersAndVisibleReceipts() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Contract summary customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "120.00", "1.0000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        performPost("/api/v1/receivables", new ReceivableCreateRequest()
                .setContractId(contract.getId()).setReceivedDate(LocalDate.of(2031, 1, 2))
                .setPaymentMethod("WIRE").setAmount(new BigDecimal("30.00")))
                .andExpect(status().isOk());

        ContractQueryRequest request = new ContractQueryRequest()
                .setCustomerId(customer.getId());
        performPost("/api/v1/contracts/query", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/contracts/summary", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalAmount").value(108.00))
                .andExpect(jsonPath("$.receivedAmount").value(30.00))
                .andExpect(jsonPath("$.outstandingAmount").value(78.00));

        performPost("/api/v1/contracts/summary", new ContractQueryRequest()
                .setKeyword("no-matching-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.receivedAmount").value(0))
                .andExpect(jsonPath("$.outstandingAmount").value(0));
    }

    @Test
    void receivableSummaryUsesTheSameAuthorizedFilters() throws Exception {
        bootstrap();
        CustomerResponse includedCustomer = customer("Included receivable summary customer");
        CustomerResponse excludedCustomer = customer("Excluded receivable summary customer");
        ProductResponse product = product();
        ContractResponse includedContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(includedCustomer.getId(), product.getId(), "120.00", "1.0000")
                        .setNumber("RECEIVABLE-SUMMARY-INCLUDED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse excludedContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(excludedCustomer.getId(), product.getId(), "120.00", "1.0000")
                        .setNumber("RECEIVABLE-SUMMARY-EXCLUDED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        performPost("/api/v1/receivables", new ReceivableCreateRequest()
                .setContractId(includedContract.getId()).setReceivedDate(LocalDate.of(2031, 1, 2))
                .setPaymentMethod("WIRE").setAmount(new BigDecimal("30.00")))
                .andExpect(status().isOk());
        performPost("/api/v1/receivables", new ReceivableCreateRequest()
                .setContractId(excludedContract.getId()).setReceivedDate(LocalDate.of(2031, 1, 3))
                .setPaymentMethod("WIRE").setAmount(new BigDecimal("20.00")))
                .andExpect(status().isOk());

        ReceivableQueryRequest included = new ReceivableQueryRequest()
                .setCustomerId(includedCustomer.getId());
        performPost("/api/v1/receivables/query", included)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/receivables/summary", included)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalAmount").value(30.00));
        performPost("/api/v1/receivables/summary", new ReceivableQueryRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.totalAmount").value(50.00));
    }

    @Test
    void receivablePlanSummaryUsesTheSameAuthorizedFiltersAndVisibleReceipts() throws Exception {
        bootstrap();
        CustomerResponse includedCustomer = customer("Included receivable plan summary customer");
        CustomerResponse excludedCustomer = customer("Excluded receivable plan summary customer");
        ProductResponse product = product();
        ContractResponse includedContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(includedCustomer.getId(), product.getId(), "120.00", "1.0000")
                        .setNumber("RECEIVABLE-PLAN-SUMMARY-INCLUDED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse excludedContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(excludedCustomer.getId(), product.getId(), "120.00", "1.0000")
                        .setNumber("RECEIVABLE-PLAN-SUMMARY-EXCLUDED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ReceivablePlanResponse includedPlan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(includedContract.getId())
                        .setDueDate(LocalDate.of(2031, 1, 2)).setPlannedAmount(new BigDecimal("80.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        ReceivablePlanResponse excludedPlan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(excludedContract.getId())
                        .setDueDate(LocalDate.of(2031, 1, 3)).setPlannedAmount(new BigDecimal("50.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        performPost("/api/v1/receivables", new ReceivableCreateRequest()
                .setContractId(includedContract.getId()).setReceivablePlanId(includedPlan.getId())
                .setReceivedDate(LocalDate.of(2031, 1, 2)).setPaymentMethod("WIRE")
                .setAmount(new BigDecimal("30.00"))).andExpect(status().isOk());
        performPost("/api/v1/receivables", new ReceivableCreateRequest()
                .setContractId(excludedContract.getId()).setReceivablePlanId(excludedPlan.getId())
                .setReceivedDate(LocalDate.of(2031, 1, 3)).setPaymentMethod("WIRE")
                .setAmount(new BigDecimal("20.00"))).andExpect(status().isOk());

        ReceivablePlanQueryRequest included = new ReceivablePlanQueryRequest()
                .setCustomerId(includedCustomer.getId());
        performPost("/api/v1/receivable-plans/query", included)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/receivable-plans/summary", included)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.plannedAmount").value(80.00))
                .andExpect(jsonPath("$.receivedAmount").value(30.00))
                .andExpect(jsonPath("$.remainingAmount").value(50.00));
        performPost("/api/v1/receivable-plans/summary", new ReceivablePlanQueryRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.plannedAmount").value(130.00))
                .andExpect(jsonPath("$.receivedAmount").value(50.00))
                .andExpect(jsonPath("$.remainingAmount").value(80.00));
    }

    @Test
    void receivableListSupportsSafeServerSideSorting() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Receivable sort customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/RECEIVABLE",
                new FieldCreateRequest().setDisplayName("Receivable score")
                        .setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        ReceivableResponse later = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.of(2031, 2, 2)).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00"))
                        .setCustomFields(Map.of(score.getCode(), List.of("10"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        ReceivableResponse earlier = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.of(2031, 1, 1)).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00"))
                        .setCustomFields(Map.of(score.getCode(), List.of("2"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);

        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setSortField("RECEIVED_DATE").setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(earlier.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(later.getId()));
        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(earlier.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(later.getId()));
        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setSortField("RECEIPT_NUMBER").setCustomSortFieldCode(score.getCode())
                .setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setCustomSortFieldCode("receipt_number").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setSortField("AMOUNT").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invoiceListSupportsSafeServerSideSorting() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Invoice sort customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        FieldDefinitionResponse score = fromJson(performPost("/api/v1/fields/objects/INVOICE",
                new FieldCreateRequest().setDisplayName("Invoice score")
                        .setFieldType("INTEGER"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                FieldDefinitionResponse.class);
        InvoiceResponse later = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "10.00")
                        .setRequestedDate(LocalDate.of(2031, 2, 2))
                        .setCustomFields(Map.of(score.getCode(), List.of("10"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse earlier = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "10.00")
                        .setRequestedDate(LocalDate.of(2031, 1, 1))
                        .setCustomFields(Map.of(score.getCode(), List.of("2"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);

        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setSortField("REQUESTED_DATE").setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(earlier.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(later.getId()));
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setCustomSortFieldCode(score.getCode()).setSortDirection("ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(earlier.getId()))
                .andExpect(jsonPath("$.objectList[1].id").value(later.getId()));
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setSortField("REQUESTED_DATE").setCustomSortFieldCode(score.getCode())
                .setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setCustomSortFieldCode("amount").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setSortField("AMOUNT").setSortDirection("ASC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lifecycleAmountsProfilesApprovalsAndTenantConstraintsStayClosed() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        bootstrap();
        assertExplicitRouteCapabilities();
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        assertEquals(19, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'CONTRACT' AND is_builtin = 1",
                Integer.class, TENANT_ID));
        assertEquals(24, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'INVOICE' AND is_builtin = 1",
                Integer.class, TENANT_ID));

        CustomerResponse customer = customer("Contract customer");
        CustomerResponse otherCustomer = customer("Other customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(new BigDecimal("80.00"), contract.getSubtotal());
        assertEquals(new BigDecimal("72.00"), contract.getTotalAmount());

        InvoiceResponse invoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "50.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals(customer.getId(), invoice.getCustomerId());
        performPost("/api/v1/invoices", invoiceRequest(contract.getId(), "23.00"))
                .andExpect(status().isUnprocessableEntity());

        performPut("/api/v1/contracts/" + contract.getId(),
                contractUpdate(contract, otherCustomer.getId(), product.getId(), "40.00", "2.0000"))
                .andExpect(status().isUnprocessableEntity());
        performPut("/api/v1/contracts/" + contract.getId(),
                contractUpdate(contract, customer.getId(), product.getId(), "40.00", "1.0000"))
                .andExpect(status().isUnprocessableEntity());

        ContractCreateRequest historicalRequest =
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-200");
        ContractResponse historicalContract = fromJson(performPost(
                "/api/v1/contracts", historicalRequest)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        InvoiceResponse deletedInvoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(historicalContract.getId(), "10.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        mockMvc.perform(withTestActor(delete("/api/v1/invoices/{invoiceId}",
                        deletedInvoice.getId())
                        .param("version", deletedInvoice.getVersion().toString())))
                .andExpect(status().isOk());
        performPut("/api/v1/contracts/" + historicalContract.getId(),
                contractUpdate(historicalContract, otherCustomer.getId(), product.getId(),
                        "40.00", "2.0000"))
                .andExpect(status().isUnprocessableEntity());

        InvoiceProfileResponse profile = fromJson(performPost("/api/v1/invoice-profiles",
                new InvoiceProfileRequest().setCustomerId(customer.getId())
                        .setTitleType("INDIVIDUAL").setTitle("Personal")
                        .setTaxNumber("ignored").setBankName("ignored"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceProfileResponse.class);
        assertNull(profile.getTaxNumber());
        assertNull(profile.getBankName());
        performGetWithParams("/api/v1/invoice-profiles",
                Map.of("customerId", customer.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(profile.getId()));

        ApprovalInstanceResponse contractApproval = approval(
                "CONTRACT", "Contract approval", "total_amount", "72.00", contract.getId());
        ApprovalInstanceResponse invoiceApproval = approval(
                "INVOICE", "Invoice approval", "amount", "50.00", invoice.getId());
        assertNotNull(contractApproval.id());
        assertNotNull(invoiceApproval.id());

        InvoiceResponse issued = fromJson(performPut(
                "/api/v1/invoices/" + invoice.getId() + "/issue",
                new InvoiceIssueRequest().setVersion(invoice.getVersion())
                        .setInvoiceNumber("F-100").setIssuedDate(LocalDate.of(2027, 1, 2)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals("ISSUED", issued.getStatus());
        mockMvc.perform(withTestActor(delete("/api/v1/invoice-profiles/{profileId}",
                        profile.getId()).param("version", profile.getVersion().toString())))
                .andExpect(status().isOk());
        assertEquals("DELETED", jdbc.queryForObject(
                "SELECT status FROM crm_invoice_profile WHERE tenant_id = ? AND id = ?",
                String.class, TENANT_ID, profile.getId()));
    }

    @Test
    void commercialPatchEndpointsRetainOmittedValuesAndExposeCollaboratorBaseline() throws Exception {
        bootstrap();
        long collaboratorActorId = 777777L;
        seedActiveMember(collaboratorActorId, "Baseline contract collaborator");
        CustomerResponse customer = customer("Partial update customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-PARTIAL").setCollaborators(List.of(
                                new ContractCollaboratorRequest().setActorId(collaboratorActorId)
                                        .setAccess("WRITE"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        ContractResponse patchedContract = fromJson(mockMvc.perform(withTestActor(
                        patch("/api/v1/contracts/{contractId}", contract.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ContractUpdateRequest()
                                        .setVersion(contract.getVersion()).setRemark("patched contract")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(contract.getName(), patchedContract.getName());
        assertEquals(contract.getNumber(), patchedContract.getNumber());
        assertEquals("patched contract", patchedContract.getRemark());
        mockMvc.perform(withTestActor(get("/api/v1/contracts/{contractId}/collaborators",
                        contract.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(patchedContract.getVersion()))
                .andExpect(jsonPath("$.collaborators[0].actorId").value(collaboratorActorId))
                .andExpect(jsonPath("$.collaborators[0].access").value("WRITE"))
                .andExpect(jsonPath("$.callerState").value("OWNER"));

        ContractProductsResponse baseline = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}/products", contract.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractProductsResponse.class);
        assertEquals(contract.getId(), baseline.getContractId());
        assertEquals(new BigDecimal("2.0000"), baseline.getLines().getFirst().getQuantity());
        assertEquals(new BigDecimal("40.00"), baseline.getLines().getFirst().getUnitPrice());

        ContractProductsResponse replaced = fromJson(mockMvc.perform(withTestActor(
                        put("/api/v1/contracts/{contractId}/products", contract.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ContractProductsUpdateRequest()
                                        .setVersion(baseline.getVersion())
                                        .setLines(List.of(new ContractProductLineRequest()
                                                .setProductId(product.getId())
                                                .setQuantity(new BigDecimal("3.0000"))
                                                .setUnitPrice(new BigDecimal("42.00")))))))
                )
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractProductsResponse.class);
        assertEquals(contract.getId(), replaced.getContractId());
        assertEquals(new BigDecimal("3.0000"), replaced.getLines().getFirst().getQuantity());
        assertEquals(new BigDecimal("42.00"), replaced.getLines().getFirst().getUnitPrice());
        assertTrue(replaced.getVersion() > baseline.getVersion());

        InvoiceProfileResponse profile = fromJson(performPost("/api/v1/invoice-profiles",
                new InvoiceProfileRequest().setCustomerId(customer.getId())
                        .setTitleType("INDIVIDUAL").setTitle("Partial profile"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceProfileResponse.class);
        InvoiceProfileResponse patchedProfile = fromJson(mockMvc.perform(withTestActor(
                        patch("/api/v1/invoice-profiles/{profileId}", profile.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new InvoiceProfileUpdateRequest()
                                        .setVersion(profile.getVersion()).setRemark("patched profile")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceProfileResponse.class);
        assertEquals(profile.getTitle(), patchedProfile.getTitle());
        assertEquals("patched profile", patchedProfile.getRemark());

        InvoiceResponse invoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "10.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse patchedInvoice = fromJson(mockMvc.perform(withTestActor(
                        patch("/api/v1/invoices/{invoiceId}", invoice.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new InvoiceUpdateRequest()
                                        .setVersion(invoice.getVersion()).setRemark("patched invoice")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals(invoice.getAmount(), patchedInvoice.getAmount());
        assertEquals(invoice.getTitle(), patchedInvoice.getTitle());
        assertEquals("patched invoice", patchedInvoice.getRemark());

        ReceivablePlanResponse plan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setDueDate(LocalDate.now().plusDays(7)).setPlannedAmount(new BigDecimal("20.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        ReceivablePlanResponse patchedPlan = fromJson(mockMvc.perform(withTestActor(
                        patch("/api/v1/receivable-plans/{planId}", plan.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ReceivablePlanUpdateRequest()
                                        .setVersion(plan.getVersion()).setReminderDays(7)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        assertEquals(plan.getDueDate(), patchedPlan.getDueDate());
        assertEquals(plan.getPlannedAmount(), patchedPlan.getPlannedAmount());
        assertEquals(7, patchedPlan.getReminderDays());

        ReceivableResponse receivable = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivablePlanId(plan.getId()).setReceivedDate(LocalDate.now())
                        .setPaymentMethod("WIRE").setAmount(new BigDecimal("5.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        ReceivableResponse patchedReceivable = fromJson(mockMvc.perform(withTestActor(
                        patch("/api/v1/receivables/{receivableId}", receivable.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new ReceivableUpdateRequest()
                                        .setVersion(receivable.getVersion()).setPaymentMethod("CASH")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        assertEquals(receivable.getReceivablePlanId(), patchedReceivable.getReceivablePlanId());
        assertEquals(receivable.getAmount(), patchedReceivable.getAmount());
        assertEquals("CASH", patchedReceivable.getPaymentMethod());
    }

    @Test
    void contractBatchOwnerAndDeleteCommandsAreVersionedAndAtomic() throws Exception {
        bootstrap();
        long replacementOwner = 767676L;
        seedActiveMember(replacementOwner, "Contract batch replacement owner");
        CustomerResponse customer = customer("Contract batch customer");
        ProductResponse product = product();
        ContractResponse first = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-BATCH-ONE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse second = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-BATCH-TWO"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        ContractBatchOwnerUpdateRequest ownerRequest = new ContractBatchOwnerUpdateRequest()
                .setOwnerActorId(replacementOwner)
                .setContracts(List.of(
                        new ContractBatchVersionRequest().setContractId(second.getId())
                                .setVersion(second.getVersion()),
                        new ContractBatchVersionRequest().setContractId(first.getId())
                                .setVersion(first.getVersion())));
        mockMvc.perform(withTestActor(put("/api/v1/contracts/batch/owner")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(ownerRequest))))
                .andExpect(status().isOk());

        ContractResponse movedFirst = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", first.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse movedSecond = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", second.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(replacementOwner, movedFirst.getOwnerActorId());
        assertEquals(replacementOwner, movedSecond.getOwnerActorId());

        ContractBatchDeleteRequest staleDelete = new ContractBatchDeleteRequest().setContracts(List.of(
                new ContractBatchVersionRequest().setContractId(first.getId())
                        .setVersion(movedFirst.getVersion()),
                new ContractBatchVersionRequest().setContractId(second.getId())
                        .setVersion(second.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/contracts/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(staleDelete))))
                .andExpect(status().isConflict());
        mockMvc.perform(withTestActor(get("/api/v1/contracts/{contractId}", first.getId())))
                .andExpect(status().isOk());

        ContractBatchDeleteRequest deleteRequest = new ContractBatchDeleteRequest().setContracts(List.of(
                new ContractBatchVersionRequest().setContractId(first.getId())
                        .setVersion(movedFirst.getVersion()),
                new ContractBatchVersionRequest().setContractId(second.getId())
                        .setVersion(movedSecond.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/contracts/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(deleteRequest))))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(get("/api/v1/contracts/{contractId}", first.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(withTestActor(get("/api/v1/contracts/{contractId}", second.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void contractBatchCollaboratorsAreVersionedAndAtomic() throws Exception {
        bootstrap();
        long reader = 757571L;
        long writer = 757572L;
        seedActiveMember(reader, "Contract batch collaborator reader");
        seedActiveMember(writer, "Contract batch collaborator writer");
        CustomerResponse customer = customer("Contract batch collaborator customer");
        ProductResponse product = product();
        ContractResponse first = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-COLLAB-ONE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse second = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-COLLAB-TWO"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        ContractBatchCollaboratorAddRequest add = new ContractBatchCollaboratorAddRequest()
                .setContracts(List.of(
                        new ContractBatchVersionRequest().setContractId(second.getId())
                                .setVersion(second.getVersion()),
                        new ContractBatchVersionRequest().setContractId(first.getId())
                                .setVersion(first.getVersion())))
                .setCollaborators(List.of(
                        new ContractCollaboratorRequest().setActorId(reader).setAccess("READ"),
                        new ContractCollaboratorRequest().setActorId(writer).setAccess("WRITE")));
        mockMvc.perform(withTestActor(put("/api/v1/contracts/batch/collaborators")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(add))))
                .andExpect(status().isOk());

        ContractResponse updatedFirst = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", first.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse updatedSecond = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", second.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(List.of(reader, writer), updatedFirst.getCollaborators().stream()
                .map(value -> value.getActorId()).toList());
        assertEquals(List.of(reader, writer), updatedSecond.getCollaborators().stream()
                .map(value -> value.getActorId()).toList());

        ContractBatchCollaboratorRemoveRequest staleRemove =
                new ContractBatchCollaboratorRemoveRequest().setActorIds(List.of(reader))
                        .setContracts(List.of(
                                new ContractBatchVersionRequest().setContractId(first.getId())
                                        .setVersion(updatedFirst.getVersion()),
                                new ContractBatchVersionRequest().setContractId(second.getId())
                                        .setVersion(updatedSecond.getVersion() - 1)));
        mockMvc.perform(withTestActor(delete("/api/v1/contracts/batch/collaborators")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(staleRemove))))
                .andExpect(status().isConflict());
        ContractResponse retainedFirst = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", first.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse retainedSecond = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", second.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(List.of(reader, writer), retainedFirst.getCollaborators().stream()
                .map(value -> value.getActorId()).toList());
        assertEquals(List.of(reader, writer), retainedSecond.getCollaborators().stream()
                .map(value -> value.getActorId()).toList());

        ContractBatchCollaboratorRemoveRequest remove =
                new ContractBatchCollaboratorRemoveRequest().setActorIds(List.of(reader))
                        .setContracts(List.of(
                                new ContractBatchVersionRequest().setContractId(first.getId())
                                        .setVersion(retainedFirst.getVersion()),
                                new ContractBatchVersionRequest().setContractId(second.getId())
                                        .setVersion(retainedSecond.getVersion())));
        mockMvc.perform(withTestActor(delete("/api/v1/contracts/batch/collaborators")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(remove))))
                .andExpect(status().isOk());
        ContractResponse removedFirst = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", first.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse removedSecond = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/contracts/{contractId}", second.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(List.of(writer), removedFirst.getCollaborators().stream()
                .map(value -> value.getActorId()).toList());
        assertEquals(List.of(writer), removedSecond.getCollaborators().stream()
                .map(value -> value.getActorId()).toList());
    }

    @Test
    void contractCollaboratorEffectiveUntilIsPersistedAndExpiresVisibility() throws Exception {
        bootstrap();
        long collaborator = 757581L;
        LocalDate effectiveUntil = LocalDate.now().plusDays(2);
        seedActiveMember(collaborator, "Expiring contract collaborator");
        CustomerResponse customer = customer("Expiring contract collaborator customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-COLLAB-EXPIRY")
                        .setCollaborators(List.of(new ContractCollaboratorRequest()
                                .setActorId(collaborator).setAccess("READ")
                                .setEffectiveUntil(effectiveUntil))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(effectiveUntil, contract.getCollaborators().getFirst().getEffectiveUntil());

        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        // SELF read scope: the caller does not own this contract, so it can only be reached through
        // the collaborator relation — which is what the expiry is supposed to withdraw.
        Long roleId = createRestrictedBusinessRole(jdbc, collaborator, "SELF");
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "READ");

        mockMvc.perform(as(collaborator, post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].id").value(contract.getId()));

        jdbc.update("UPDATE crm_contract_collaborator SET effective_until = ? "
                        + "WHERE tenant_id = ? AND contract_id = ? AND actor_id = ?",
                LocalDate.now().minusDays(1), TENANT_ID, contract.getId(), collaborator);
        mockMvc.perform(as(collaborator, post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList").isEmpty());
    }

    @Test
    void selectedContractCsvExportRechecksScopeAndObservedVersions() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Contract CSV export customer");
        ProductResponse product = product();
        ContractResponse first = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setName("=Selected CSV one").setNumber("CSV-SELECT-ONE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse second = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "50.00", "1.0000")
                        .setName("Selected CSV two").setNumber("CSV-SELECT-TWO"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        ContractBatchCsvExportRequest request = new ContractBatchCsvExportRequest().setContracts(List.of(
                new ContractBatchVersionRequest().setContractId(second.getId())
                        .setVersion(second.getVersion()),
                new ContractBatchVersionRequest().setContractId(first.getId())
                        .setVersion(first.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/contracts/batch/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("合同 ID,合同编号,合同名称")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        second.getId() + ",CSV-SELECT-TWO,Selected CSV two")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        first.getId() + ",CSV-SELECT-ONE,'=Selected CSV one")));

        long replacementOwner = 757573L;
        seedActiveMember(replacementOwner, "Contract CSV replacement owner");
        mockMvc.perform(withTestActor(put("/api/v1/contracts/{contractId}/owner", first.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContractOwnerUpdateRequest()
                                .setVersion(first.getVersion())
                                .setOwnerActorId(replacementOwner)))))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(post("/api/v1/contracts/batch/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(request))))
                .andExpect(status().isConflict());

        ContractBatchCsvExportRequest duplicate = new ContractBatchCsvExportRequest().setContracts(List.of(
                new ContractBatchVersionRequest().setContractId(first.getId())
                        .setVersion(first.getVersion()),
                new ContractBatchVersionRequest().setContractId(first.getId())
                        .setVersion(first.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/contracts/batch/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(duplicate))))
                .andExpect(status().isBadRequest());

        // Export scope is rechecked separately from read scope: a caller that may read every
        // contract but may only export its own is refused before any version comparison.
        long narrowExporter = 757574L;
        seedActiveMember(narrowExporter, "Contract narrow exporter");
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO authorization_role (tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Tenant reader, self exporter");
        Long exportRoleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Tenant reader, self exporter");
        Map.of("contracts:read", "TENANT", "contracts:export", "SELF")
                .forEach((authority, scope) -> jdbc.update(
                        "INSERT INTO authorization_role_grant "
                                + "(tenant_id, role_id, authority_code, data_scope) "
                                + "VALUES (?, ?, ?, ?)",
                        TENANT_ID, exportRoleId, authority, scope));
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, narrowExporter, exportRoleId);

        ContractBatchCsvExportRequest outsideExportScope = new ContractBatchCsvExportRequest()
                .setContracts(List.of(new ContractBatchVersionRequest().setContractId(second.getId())
                        .setVersion(second.getVersion())));
        mockMvc.perform(as(narrowExporter, post("/api/v1/contracts/batch/export/csv")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(outsideExportScope))))
                .andExpect(status().isForbidden());
    }

    @Test
    void receivableAndInvoiceBatchCommandsAreVersionedAndAtomic() throws Exception {
        bootstrap();
        long replacementOwner = 767676L;
        seedActiveMember(replacementOwner, "Batch replacement owner");
        CustomerResponse customer = customer("Finance batch customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "100.00", "4.0000")
                        .setNumber("FINANCE-BATCH"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        ReceivableResponse firstReceivable = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        ReceivableResponse secondReceivable = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        ReceivableBatchOwnerUpdateRequest receivableOwners = new ReceivableBatchOwnerUpdateRequest()
                .setOwnerActorId(replacementOwner).setReceivables(List.of(
                        new ReceivableBatchVersionRequest().setReceivableId(secondReceivable.getId())
                                .setVersion(secondReceivable.getVersion()),
                        new ReceivableBatchVersionRequest().setReceivableId(firstReceivable.getId())
                                .setVersion(firstReceivable.getVersion())));
        mockMvc.perform(withTestActor(put("/api/v1/receivables/batch/owner")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(receivableOwners))))
                .andExpect(status().isOk());
        ReceivableResponse movedFirstReceivable = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/receivables/{receivableId}", firstReceivable.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        ReceivableResponse movedSecondReceivable = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/receivables/{receivableId}", secondReceivable.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        assertEquals(replacementOwner, movedFirstReceivable.getOwnerActorId());
        assertEquals(replacementOwner, movedSecondReceivable.getOwnerActorId());

        ReceivableBatchDeleteRequest staleReceivableDelete = new ReceivableBatchDeleteRequest()
                .setReceivables(List.of(
                        new ReceivableBatchVersionRequest().setReceivableId(firstReceivable.getId())
                                .setVersion(movedFirstReceivable.getVersion()),
                        new ReceivableBatchVersionRequest().setReceivableId(secondReceivable.getId())
                                .setVersion(secondReceivable.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/receivables/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(staleReceivableDelete))))
                .andExpect(status().isConflict());
        mockMvc.perform(withTestActor(get("/api/v1/receivables/{receivableId}", firstReceivable.getId())))
                .andExpect(status().isOk());
        ReceivableBatchDeleteRequest receivableDelete = new ReceivableBatchDeleteRequest()
                .setReceivables(List.of(
                        new ReceivableBatchVersionRequest().setReceivableId(firstReceivable.getId())
                                .setVersion(movedFirstReceivable.getVersion()),
                        new ReceivableBatchVersionRequest().setReceivableId(secondReceivable.getId())
                                .setVersion(movedSecondReceivable.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/receivables/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(receivableDelete))))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(get("/api/v1/receivables/{receivableId}", firstReceivable.getId())))
                .andExpect(status().isNotFound());

        InvoiceResponse firstInvoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "20.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse secondInvoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "20.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceBatchOwnerUpdateRequest invoiceOwners = new InvoiceBatchOwnerUpdateRequest()
                .setOwnerActorId(replacementOwner).setInvoices(List.of(
                        new InvoiceBatchVersionRequest().setInvoiceId(secondInvoice.getId())
                                .setVersion(secondInvoice.getVersion()),
                        new InvoiceBatchVersionRequest().setInvoiceId(firstInvoice.getId())
                                .setVersion(firstInvoice.getVersion())));
        mockMvc.perform(withTestActor(put("/api/v1/invoices/batch/owner")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invoiceOwners))))
                .andExpect(status().isOk());
        InvoiceResponse movedFirstInvoice = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/invoices/{invoiceId}", firstInvoice.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse movedSecondInvoice = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/invoices/{invoiceId}", secondInvoice.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals(replacementOwner, movedFirstInvoice.getOwnerActorId());
        assertEquals(replacementOwner, movedSecondInvoice.getOwnerActorId());
        InvoiceResponse issuedFirstInvoice = fromJson(performPut(
                "/api/v1/invoices/" + firstInvoice.getId() + "/issue",
                new InvoiceIssueRequest().setVersion(movedFirstInvoice.getVersion())
                        .setInvoiceNumber("BATCH-INV-ONE").setIssuedDate(LocalDate.of(2027, 1, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse issuedSecondInvoice = fromJson(performPut(
                "/api/v1/invoices/" + secondInvoice.getId() + "/issue",
                new InvoiceIssueRequest().setVersion(movedSecondInvoice.getVersion())
                        .setInvoiceNumber("BATCH-INV-TWO").setIssuedDate(LocalDate.of(2027, 1, 1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceBatchResetRequest staleInvoiceReset = new InvoiceBatchResetRequest().setInvoices(List.of(
                new InvoiceBatchVersionRequest().setInvoiceId(firstInvoice.getId())
                        .setVersion(issuedFirstInvoice.getVersion()),
                new InvoiceBatchVersionRequest().setInvoiceId(secondInvoice.getId())
                        .setVersion(movedSecondInvoice.getVersion())));
        mockMvc.perform(withTestActor(put("/api/v1/invoices/batch/reset")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(staleInvoiceReset))))
                .andExpect(status().isConflict());
        mockMvc.perform(withTestActor(get("/api/v1/invoices/{invoiceId}", firstInvoice.getId())))
                .andExpect(jsonPath("$.status").value("ISSUED"));
        InvoiceBatchResetRequest invoiceReset = new InvoiceBatchResetRequest().setInvoices(List.of(
                new InvoiceBatchVersionRequest().setInvoiceId(firstInvoice.getId())
                        .setVersion(issuedFirstInvoice.getVersion()),
                new InvoiceBatchVersionRequest().setInvoiceId(secondInvoice.getId())
                        .setVersion(issuedSecondInvoice.getVersion())));
        mockMvc.perform(withTestActor(put("/api/v1/invoices/batch/reset")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invoiceReset))))
                .andExpect(status().isOk());
        InvoiceResponse resetFirstInvoice = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/invoices/{invoiceId}", firstInvoice.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse resetSecondInvoice = fromJson(mockMvc.perform(withTestActor(
                        get("/api/v1/invoices/{invoiceId}", secondInvoice.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals("PENDING", resetFirstInvoice.getStatus());
        assertEquals("PENDING", resetSecondInvoice.getStatus());
        InvoiceBatchDeleteRequest staleInvoiceDelete = new InvoiceBatchDeleteRequest().setInvoices(List.of(
                new InvoiceBatchVersionRequest().setInvoiceId(firstInvoice.getId())
                        .setVersion(resetFirstInvoice.getVersion()),
                new InvoiceBatchVersionRequest().setInvoiceId(secondInvoice.getId())
                        .setVersion(issuedSecondInvoice.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/invoices/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(staleInvoiceDelete))))
                .andExpect(status().isConflict());
        mockMvc.perform(withTestActor(get("/api/v1/invoices/{invoiceId}", firstInvoice.getId())))
                .andExpect(status().isOk());
        InvoiceBatchDeleteRequest invoiceDelete = new InvoiceBatchDeleteRequest().setInvoices(List.of(
                new InvoiceBatchVersionRequest().setInvoiceId(firstInvoice.getId())
                        .setVersion(resetFirstInvoice.getVersion()),
                new InvoiceBatchVersionRequest().setInvoiceId(secondInvoice.getId())
                        .setVersion(resetSecondInvoice.getVersion())));
        mockMvc.perform(withTestActor(post("/api/v1/invoices/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(invoiceDelete))))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(get("/api/v1/invoices/{invoiceId}", firstInvoice.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void coreReportAggregatesOnlyTheCurrentCommercialSourceViews() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Report customer");
        OpportunityResponse open = opportunity(customer.getId(), "Open report opportunity", "100.00");
        OpportunityResponse won = closeOpportunity(
                opportunity(customer.getId(), "Won report opportunity", "60.00"), "WON");
        OpportunityResponse lost = closeOpportunity(
                opportunity(customer.getId(), "Lost report opportunity", "30.00"), "LOST");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("50.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);

        CoreReportResponse report = fromJson(performGet("/api/v1/reports/core")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CoreReportResponse.class);

        assertEquals(1L, report.getOpenOpportunityCount());
        assertEquals(new BigDecimal("100.00"), report.getOpenOpportunityEstimatedAmount());
        assertEquals(1L, report.getWonOpportunityCount());
        assertEquals(new BigDecimal("60.00"), report.getWonOpportunityEstimatedAmount());
        assertEquals(1L, report.getLostOpportunityCount());
        assertEquals(new BigDecimal("30.00"), report.getLostOpportunityEstimatedAmount());
        assertEquals(1L, report.getActiveContractCount());
        assertEquals(new BigDecimal("72.00"), report.getActiveContractAmount());
        assertEquals(1L, report.getReceivableCount());
        assertEquals(new BigDecimal("50.00"), report.getReceivableAmount());

        assertTrue(open.getId() > 0);
        assertTrue(won.getId() > 0);
        assertTrue(lost.getId() > 0);
    }

    @Test
    void coreReportKeepsEachSourceScopeTenantBoundaryAndLifecycleFilter() throws Exception {
        bootstrap();
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        long reportActor = 626270L;
        long departmentOwner = 626271L;
        long outsideDepartmentOwner = 626272L;
        long reportDepartment = insertDepartment(jdbc, "Report department");
        long outsideDepartment = insertDepartment(jdbc, "Outside report department");
        coreReportReader(jdbc, reportActor, "Scoped report reader", reportDepartment,
                "SELF", "DEPARTMENT", "TENANT");
        seedActiveMember(departmentOwner, "Report department owner", reportDepartment);
        seedActiveMember(outsideDepartmentOwner, "Outside report department owner",
                outsideDepartment);

        CustomerResponse customer = customer("Scoped report customer");
        ProductResponse product = product();
        opportunity(customer.getId(), "Visible self opportunity", "11.00", reportActor);
        closeOpportunity(opportunity(customer.getId(), "Hidden other opportunity", "13.00",
                departmentOwner), "WON");
        closeOpportunity(opportunity(customer.getId(), "Invalid opportunity", "17.00",
                reportActor), "INVALID");

        ContractResponse departmentContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "20.00", "1.0000")
                        .setNumber("REPORT-DEPARTMENT").setDiscountRate(BigDecimal.ZERO)
                        .setOwnerActorId(departmentOwner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse outsideDepartmentContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "30.00", "1.0000")
                        .setNumber("REPORT-OUTSIDE").setDiscountRate(BigDecimal.ZERO)
                        .setOwnerActorId(outsideDepartmentOwner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse voidedContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("REPORT-VOIDED").setDiscountRate(BigDecimal.ZERO)
                        .setOwnerActorId(departmentOwner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        fromJson(performPut("/api/v1/contracts/" + voidedContract.getId() + "/void",
                new ContractVersionRequest().setVersion(voidedContract.getVersion()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        receivable(departmentContract.getId(), reportActor, "3.00");
        receivable(departmentContract.getId(), outsideDepartmentOwner, "4.00");
        ReceivableResponse deletedReceipt = receivable(departmentContract.getId(), departmentOwner,
                "5.00");
        mockMvc.perform(as(ACTOR_ID, delete("/api/v1/receivables/{receivableId}",
                        deletedReceipt.getId()).param("version", deletedReceipt.getVersion().toString())))
                .andExpect(status().isOk());

        long otherTenantId = 999998L;
        long otherTenantOwner = 626273L;
        bootstrap(otherTenantOwner, otherTenantId, "Other report tenant owner");
        jdbc.update("INSERT INTO organization_membership (tenant_id, actor_id, status) "
                        + "VALUES (?, ?, 'ACTIVE')",
                otherTenantId, reportActor);
        CustomerResponse otherCustomer = fromJson(mockMvc.perform(as(otherTenantOwner, otherTenantId,
                        post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new CustomerCreateRequest()
                                        .setName("Other report customer")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
        OpportunityPipelineFixture.provisionActivePair(jdbc, otherTenantId, "direct", "qualification");
        jdbc.update("INSERT INTO crm_opportunity "
                        + "(tenant_id, owner_actor_id, customer_id, name, pipeline_code, stage_code, "
                        + "estimated_amount, outcome, status) "
                        + "VALUES (?, ?, ?, 'Cross-tenant opportunity', 'direct', 'qualification', "
                        + "101.00, 'OPEN', 'ACTIVE')",
                otherTenantId, reportActor, otherCustomer.getId());
        jdbc.update("INSERT INTO crm_contract "
                        + "(tenant_id, owner_actor_id, customer_id, name, contract_number, "
                        + "contract_number_key, subtotal, discount_rate, total_amount, status) "
                        + "VALUES (?, ?, ?, 'Cross-tenant contract', 'CROSS-REPORT', "
                        + "'cross-report', 100.00, 0.0000, 100.00, 'ACTIVE')",
                otherTenantId, reportActor, otherCustomer.getId());
        Long crossTenantContractId = jdbc.queryForObject(
                "SELECT id FROM crm_contract WHERE tenant_id = ? AND contract_number = ?",
                Long.class, otherTenantId, "CROSS-REPORT");
        jdbc.update("INSERT INTO crm_receivable "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, receipt_number, "
                        + "received_date, payment_method, amount, status) "
                        + "VALUES (?, ?, ?, ?, 'CROSS-REPORT-RECEIPT', CURRENT_DATE, 'WIRE', 101.00, "
                        + "'ACTIVE')",
                otherTenantId, reportActor, otherCustomer.getId(), crossTenantContractId);

        CoreReportResponse report = fromJson(mockMvc.perform(as(reportActor,
                        get("/api/v1/reports/core")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CoreReportResponse.class);
        assertEquals(1L, report.getOpenOpportunityCount());
        assertEquals(new BigDecimal("11.00"), report.getOpenOpportunityEstimatedAmount());
        assertEquals(0L, report.getWonOpportunityCount());
        assertEquals(new BigDecimal("0.00"), report.getWonOpportunityEstimatedAmount());
        assertEquals(0L, report.getLostOpportunityCount());
        assertEquals(new BigDecimal("0.00"), report.getLostOpportunityEstimatedAmount());
        assertEquals(1L, report.getActiveContractCount());
        assertEquals(new BigDecimal("20.00"), report.getActiveContractAmount());
        assertEquals(2L, report.getReceivableCount());
        assertEquals(new BigDecimal("7.00"), report.getReceivableAmount());
        assertTrue(outsideDepartmentContract.getId() > 0);
    }

    @Test
    void coreReportRejectsHiddenOrMaskedGroupingAndFilterFields() throws Exception {
        bootstrap();
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);

        long hiddenOutcomeActor = 626261L;
        Long hiddenOutcomeRole = coreReportReader(jdbc, hiddenOutcomeActor,
                "Report reader with hidden opportunity outcome");
        hideBuiltinFieldAccess(jdbc, hiddenOutcomeRole, "OPPORTUNITY", "outcome");
        mockMvc.perform(as(hiddenOutcomeActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long maskedOutcomeActor = 626262L;
        Long maskedOutcomeRole = coreReportReader(jdbc, maskedOutcomeActor,
                "Report reader with masked opportunity outcome");
        maskBuiltinFieldAccess(jdbc, maskedOutcomeRole, "OPPORTUNITY", "outcome", "PARTIAL");
        mockMvc.perform(as(maskedOutcomeActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long hiddenStatusActor = 626263L;
        Long hiddenStatusRole = coreReportReader(jdbc, hiddenStatusActor,
                "Report reader with hidden contract status");
        hideBuiltinFieldAccess(jdbc, hiddenStatusRole, "CONTRACT", "status");
        mockMvc.perform(as(hiddenStatusActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long maskedStatusActor = 626264L;
        Long maskedStatusRole = coreReportReader(jdbc, maskedStatusActor,
                "Report reader with masked contract status");
        maskBuiltinFieldAccess(jdbc, maskedStatusRole, "CONTRACT", "status", "FULL");
        mockMvc.perform(as(maskedStatusActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long hiddenOpportunityAmountActor = 626265L;
        Long hiddenOpportunityAmountRole = coreReportReader(jdbc, hiddenOpportunityAmountActor,
                "Report reader with hidden opportunity amount");
        hideBuiltinFieldAccess(jdbc, hiddenOpportunityAmountRole, "OPPORTUNITY",
                "estimated_amount");
        mockMvc.perform(as(hiddenOpportunityAmountActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long partialContractAmountActor = 626266L;
        Long partialContractAmountRole = coreReportReader(jdbc, partialContractAmountActor,
                "Report reader with partially masked contract amount");
        maskBuiltinFieldAccess(jdbc, partialContractAmountRole, "CONTRACT", "total_amount",
                "PARTIAL");
        mockMvc.perform(as(partialContractAmountActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long fullReceivableAmountActor = 626267L;
        Long fullReceivableAmountRole = coreReportReader(jdbc, fullReceivableAmountActor,
                "Report reader with fully masked receivable amount");
        maskBuiltinFieldAccess(jdbc, fullReceivableAmountRole, "RECEIVABLE", "amount", "FULL");
        mockMvc.perform(as(fullReceivableAmountActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());

        long missingSourceActor = 626268L;
        coreReportReader(jdbc, missingSourceActor, "Report reader without receivables", null,
                "TENANT", "TENANT", null);
        mockMvc.perform(as(missingSourceActor, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());
    }

    @Test
    void receivablesAndPlansStayTenantScopedAndNeverExceedTheirContractAmounts() throws Exception {
        bootstrap();
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        assertEquals(12, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'RECEIVABLE' AND is_builtin = 1",
                Integer.class, TENANT_ID));
        assertEquals(15, jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = 'RECEIVABLE_PLAN' AND is_builtin = 1",
                Integer.class, TENANT_ID));
        performPost("/api/v1/receivable-plans/query", new ReceivablePlanQueryRequest())
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));

        CustomerResponse customer = customer("Receivable customer");
        CustomerResponse replacementCustomer = customer("Replacement receivable customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-RECEIVABLE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        ReceivablePlanResponse plan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setDueDate(LocalDate.now().plusDays(7)).setPlannedAmount(new BigDecimal("40.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        assertEquals(customer.getId(), plan.getCustomerId());
        assertEquals(new BigDecimal("40.00"), plan.getRemainingAmount());
        ReceivablePlanResponse replacementPlan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setDueDate(LocalDate.now().plusDays(14)).setPlannedAmount(new BigDecimal("20.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        performGetWithParams("/api/v1/receivable-plans/available",
                Map.of("contractId", contract.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(plan.getId()));
        performPut("/api/v1/contracts/" + contract.getId(),
                contractUpdate(contract, replacementCustomer.getId(), product.getId(), "40.00", "2.0000"))
                .andExpect(status().isUnprocessableEntity());

        ReceivableResponse receipt = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivablePlanId(plan.getId()).setReceivedDate(LocalDate.now())
                        .setPaymentMethod("WIRE").setAmount(new BigDecimal("20.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        assertEquals(customer.getId(), receipt.getCustomerId());
        assertEquals(plan.getId(), receipt.getReceivablePlanId());
        performPut("/api/v1/contracts/" + contract.getId(),
                contractUpdate(contract, customer.getId(), product.getId(), "40.00", "1.0000"))
                .andExpect(status().isUnprocessableEntity());
        performPost("/api/v1/receivables", new ReceivableCreateRequest()
                .setContractId(contract.getId()).setReceivablePlanId(plan.getId())
                .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                .setAmount(new BigDecimal("21.00"))).andExpect(status().isUnprocessableEntity());
        ReceivableResponse retainedPlan = fromJson(performPut("/api/v1/receivables/" + receipt.getId(),
                new ReceivableUpdateRequest().setVersion(receipt.getVersion())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("20.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        assertEquals(plan.getId(), retainedPlan.getReceivablePlanId());
        performPut("/api/v1/receivables/" + receipt.getId(),
                new ReceivableUpdateRequest().setVersion(retainedPlan.getVersion())
                        .setReceivablePlanId(replacementPlan.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("20.00")))
                .andExpect(status().isUnprocessableEntity());

        ReceivableResponse updated = fromJson(performPut("/api/v1/receivables/" + receipt.getId(),
                new ReceivableUpdateRequest().setVersion(retainedPlan.getVersion()).setReceivablePlanId(plan.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("40.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        performGetWithParams("/api/v1/receivable-plans/available",
                Map.of("contractId", contract.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(replacementPlan.getId()))
                .andExpect(jsonPath("$[1]").doesNotExist());
        performPut("/api/v1/receivable-plans/" + plan.getId(),
                new ReceivablePlanUpdateRequest().setVersion(plan.getVersion())
                        .setDueDate(LocalDate.now().plusDays(14)).setPlannedAmount(new BigDecimal("50.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(withTestActor(delete("/api/v1/receivable-plans/{planId}", plan.getId())
                        .param("version", plan.getVersion().toString())))
                .andExpect(status().isUnprocessableEntity());

        ApprovalInstanceResponse approval = approval(
                "RECEIVABLE", "Receivable approval", "amount", "40.00", updated.getId());
        assertNotNull(approval.id());

        performPut("/api/v1/contracts/" + contract.getId(),
                contractUpdate(contract, customer.getId(), product.getId(), "40.00", "0.2500"))
                .andExpect(status().isUnprocessableEntity());

        ContractResponse historicalContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-RECEIVABLE-HISTORY"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ReceivableResponse deletedReceipt = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(historicalContract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        mockMvc.perform(withTestActor(delete("/api/v1/receivables/{receivableId}", deletedReceipt.getId())
                        .param("version", deletedReceipt.getVersion().toString())))
                .andExpect(status().isOk());
        mockMvc.perform(withTestActor(delete("/api/v1/contracts/{contractId}", historicalContract.getId())
                        .param("version", historicalContract.getVersion().toString())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updatingAReceivableWithoutPlanDoesNotRequirePlanReadCapability() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("No-plan receivable customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-NO-PLAN"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ReceivableResponse receipt = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);

        long receiptWriter = 444444L;
        seedActiveMember(receiptWriter, "No-plan receivable writer");
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long roleId = createReceivableWriterRole(jdbc, receiptWriter);
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "READ");
        addAllBuiltinFieldAccesses(jdbc, roleId, "RECEIVABLE", "WRITE");

        ReceivableUpdateRequest update = new ReceivableUpdateRequest().setVersion(receipt.getVersion())
                .setReceivedDate(LocalDate.now()).setPaymentMethod("CASH")
                .setAmount(new BigDecimal("10.00"));
        mockMvc.perform(as(receiptWriter, put("/api/v1/receivables/{receivableId}", receipt.getId())
                .contentType(MediaType.APPLICATION_JSON).content(toJson(update))))
                .andExpect(status().isOk());
    }

    @Test
    void planBindingAndAvailabilityRequireUnmaskedPlanCapacityFields() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Restricted plan customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-PLAN-FIELD-ACCESS"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ReceivablePlanResponse plan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setDueDate(LocalDate.now().plusDays(7)).setPlannedAmount(new BigDecimal("20.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        ReceivableResponse receipt = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);

        long restrictedActor = 455555L;
        seedActiveMember(restrictedActor, "Restricted plan receipt writer");
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long roleId = createPlanBoundReceivableWriterRole(jdbc, restrictedActor);
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "READ");
        addAllBuiltinFieldAccesses(jdbc, roleId, "RECEIVABLE", "WRITE");
        addAllBuiltinFieldAccesses(jdbc, roleId, "RECEIVABLE_PLAN", "READ");
        hideBuiltinFieldAccess(jdbc, roleId, "RECEIVABLE_PLAN", "planned_amount");

        ReceivableUpdateRequest bindPlan = new ReceivableUpdateRequest()
                .setVersion(receipt.getVersion()).setReceivablePlanId(plan.getId())
                .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                .setAmount(new BigDecimal("10.00"));
        mockMvc.perform(as(restrictedActor, put("/api/v1/receivables/{receivableId}", receipt.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(bindPlan))))
                .andExpect(status().isForbidden());

        setBuiltinFieldAccess(jdbc, roleId, "RECEIVABLE_PLAN", "planned_amount", "READ");
        hideBuiltinFieldAccess(jdbc, roleId, "RECEIVABLE_PLAN", "received_amount");
        hideBuiltinFieldAccess(jdbc, roleId, "RECEIVABLE_PLAN", "remaining_amount");
        hideBuiltinFieldAccess(jdbc, roleId, "RECEIVABLE_PLAN", "progress");
        mockMvc.perform(as(restrictedActor, get("/api/v1/receivable-plans/available")
                        .param("contractId", contract.getId().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void contractSourceFieldsMustBeUnmaskedForReceivableAndPlanWrites() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Contract source field customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "1.0000")
                        .setNumber("C-CONTRACT-FIELD-ACCESS"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ReceivablePlanResponse plan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setDueDate(LocalDate.now().plusDays(7)).setPlannedAmount(new BigDecimal("10.00"))
                        .setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        ReceivableResponse receipt = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);

        long restrictedActor = 433333L;
        seedActiveMember(restrictedActor, "Contract source field writer");
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long roleId = createPlanBoundReceivableWriterRole(jdbc, restrictedActor);
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "READ");
        addAllBuiltinFieldAccesses(jdbc, roleId, "RECEIVABLE", "WRITE");
        addAllBuiltinFieldAccesses(jdbc, roleId, "RECEIVABLE_PLAN", "WRITE");
        hideBuiltinFieldAccess(jdbc, roleId, "CONTRACT", "customer");

        mockMvc.perform(as(restrictedActor, post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReceivableCreateRequest().setContractId(contract.getId())
                                .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                                .setAmount(new BigDecimal("1.00"))))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(restrictedActor, post("/api/v1/receivable-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReceivablePlanCreateRequest().setContractId(contract.getId())
                                .setDueDate(LocalDate.now().plusDays(8))
                                .setPlannedAmount(new BigDecimal("1.00")).setPaymentMethod("WIRE")))))
                .andExpect(status().isForbidden());

        setBuiltinFieldAccess(jdbc, roleId, "CONTRACT", "customer", "READ");
        maskBuiltinFieldAccess(jdbc, roleId, "CONTRACT", "total_amount", "FULL");
        mockMvc.perform(as(restrictedActor, put("/api/v1/receivables/{receivableId}", receipt.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ReceivableUpdateRequest().setVersion(receipt.getVersion())
                                .setReceivedDate(LocalDate.now()).setPaymentMethod("WIRE")
                                .setAmount(new BigDecimal("10.00"))))))
                .andExpect(status().isForbidden());
        ReceivablePlanBatchCreateRequest batch = new ReceivablePlanBatchCreateRequest()
                .setContractId(contract.getId()).setPlans(List.of(new ReceivablePlanDraftRequest()
                        .setDueDate(LocalDate.now().plusDays(9)).setPlannedAmount(new BigDecimal("1.00"))
                        .setPaymentMethod("WIRE")));
        mockMvc.perform(as(restrictedActor, post("/api/v1/receivable-plans/batch")
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(batch))))
                .andExpect(status().isForbidden());
        ReceivablePlanUpdateRequest updatePlan = new ReceivablePlanUpdateRequest()
                .setVersion(plan.getVersion()).setDueDate(LocalDate.now().plusDays(8))
                .setPlannedAmount(new BigDecimal("10.00")).setPaymentMethod("WIRE");
        mockMvc.perform(as(restrictedActor, put("/api/v1/receivable-plans/{planId}", plan.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(updatePlan))))
                .andExpect(status().isForbidden());
    }

    @Test
    void availablePlansFilterCompletedPlansBeforeReturningTheFullResult() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Available plan scale customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-AVAILABLE-SCALE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        List<Object[]> plans = new java.util.ArrayList<>();
        for (int installment = 1; installment <= 501; installment++) {
            plans.add(new Object[]{TENANT_ID, ACTOR_ID, customer.getId(), contract.getId(), installment,
                    LocalDate.now().plusDays(installment), new BigDecimal("0.01"), "WIRE"});
        }
        jdbc.batchUpdate("INSERT INTO crm_receivable_plan "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, installment, due_date, "
                        + "planned_amount, payment_method) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", plans);
        List<Long> planIds = jdbc.queryForList("SELECT id FROM crm_receivable_plan "
                        + "WHERE tenant_id = ? AND contract_id = ? ORDER BY installment",
                Long.class, TENANT_ID, contract.getId());
        List<Object[]> receipts = new java.util.ArrayList<>();
        for (int index = 0; index < 500; index++) {
            receipts.add(new Object[]{TENANT_ID, ACTOR_ID, customer.getId(), contract.getId(),
                    planIds.get(index), "RCV-SCALE-" + index, LocalDate.now(), "WIRE",
                    new BigDecimal("0.01")});
        }
        jdbc.batchUpdate("INSERT INTO crm_receivable "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, receivable_plan_id, "
                        + "receipt_number, received_date, payment_method, amount) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", receipts);

        performGetWithParams("/api/v1/receivable-plans/available",
                Map.of("contractId", contract.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].installment").value(501))
                .andExpect(jsonPath("$[1]").doesNotExist());
        List<Object[]> additionalPlans = new java.util.ArrayList<>();
        for (int installment = 502; installment <= 1_002; installment++) {
            additionalPlans.add(new Object[]{TENANT_ID, ACTOR_ID, customer.getId(), contract.getId(), installment,
                    LocalDate.now().plusDays(installment), new BigDecimal("0.01"), "WIRE"});
        }
        jdbc.batchUpdate("INSERT INTO crm_receivable_plan "
                        + "(tenant_id, owner_actor_id, customer_id, contract_id, installment, due_date, "
                        + "planned_amount, payment_method) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", additionalPlans);
        performGetWithParams("/api/v1/receivable-plans/available",
                Map.of("contractId", contract.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].installment").value(501))
                .andExpect(jsonPath("$[499].installment").value(1_000))
                .andExpect(jsonPath("$[500]").doesNotExist());
    }

    @Test
    void mutationResponsesAndCollaboratorsRespectIndependentAccessScopes() throws Exception {
        bootstrap();
        long scopedActor = 666666L;
        long remainingCollaborator = 555555L;
        seedActiveMember(scopedActor, "Scoped contract member");
        seedActiveMember(remainingCollaborator, "Remaining contract collaborator");
        CustomerResponse customer = customer("Scoped contract customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-SCOPE")
                        .setCollaborators(List.of(
                                new ContractCollaboratorRequest().setActorId(scopedActor)
                                        .setAccess("READ"),
                                new ContractCollaboratorRequest().setActorId(remainingCollaborator)
                                        .setAccess("READ"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long roleId = createRestrictedBusinessRole(jdbc, scopedActor);
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "READ");
        addAllBuiltinFieldAccesses(jdbc, roleId, "INVOICE", "WRITE");

        mockMvc.perform(as(scopedActor, post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].collaborators").isEmpty());
        mockMvc.perform(as(scopedActor, get("/api/v1/contracts/{contractId}/collaborators",
                        contract.getId())))
                .andExpect(status().isNotFound());

        ContractResponse exited = fromJson(mockMvc.perform(as(scopedActor,
                        delete("/api/v1/contracts/{contractId}/collaborators/me", contract.getId())
                                .param("version", contract.getVersion().toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertNull(exited.getCustomerId());
        assertNull(exited.getOpportunityId());
        assertNull(exited.getContactId());
        assertEquals(List.of(), exited.getProducts());

        ContractResponse updated = fromJson(performPut("/api/v1/contracts/" + contract.getId(),
                contractUpdate(exited, customer.getId(), product.getId(), "40.00", "2.0000"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(List.of(), updated.getCollaborators());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contract_collaborator "
                        + "WHERE tenant_id = ? AND contract_id = ?",
                Integer.class, TENANT_ID, contract.getId()));

        mockMvc.perform(as(scopedActor, put("/api/v1/contracts/{contractId}/void",
                        contract.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContractVersionRequest()
                                .setVersion(updated.getVersion())))))
                .andExpect(status().isForbidden());

        InvoiceResponse createdInvoice = fromJson(mockMvc.perform(as(scopedActor,
                        post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(invoiceRequest(contract.getId(), "10.00")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertNull(createdInvoice.getCustomerId());
        assertEquals(contract.getId(), createdInvoice.getContractId());

        InvoiceResponse updatedInvoice = fromJson(mockMvc.perform(as(scopedActor,
                        put("/api/v1/invoices/{invoiceId}", createdInvoice.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new InvoiceUpdateRequest()
                                        .setVersion(createdInvoice.getVersion())
                                        .setAmount(new BigDecimal("9.00"))
                                        .setInvoiceType("VAT")
                                        .setTitleType("ORGANIZATION")
                                        .setTitle("Scoped contract customer")
                                        .setTaxNumber("TAX-100")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertNull(updatedInvoice.getCustomerId());
        assertEquals(contract.getId(), updatedInvoice.getContractId());
    }

    @Test
    void assignmentScopesRejectOutOfScopeOwnersAndEmptyProductsNeedNoProductRead()
            throws Exception {
        bootstrap();
        long scopedActor = 666666L;
        long outsideOwner = 555555L;
        seedActiveMember(scopedActor, "Self-scoped assignee");
        seedActiveMember(outsideOwner, "Outside assignee");
        CustomerResponse customer = customer("Assignment customer");
        ProductResponse product = product();
        ContractResponse productContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-CLEAR"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        ContractResponse invoiceContract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "40.00", "2.0000")
                        .setNumber("C-INVOICE-SCOPE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);

        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long roleId = createAssignmentRestrictedRole(jdbc, scopedActor);
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "WRITE");
        addAllBuiltinFieldAccesses(jdbc, roleId, "INVOICE", "WRITE");

        ContractResponse cleared = fromJson(mockMvc.perform(as(scopedActor,
                        put("/api/v1/contracts/{contractId}", productContract.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(contractUpdate(productContract, customer.getId(),
                                        product.getId(), "40.00", "2.0000")
                                        .setProducts(List.of())))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(List.of(), cleared.getProducts());

        ContractCreateRequest emptyRequest = new ContractCreateRequest()
                .setName("Empty product contract").setNumber("C-EMPTY")
                .setCustomerId(customer.getId()).setDiscountRate(BigDecimal.ZERO)
                .setProducts(List.of());
        ContractResponse owned = fromJson(mockMvc.perform(as(scopedActor,
                        post("/api/v1/contracts").contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(emptyRequest))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        assertEquals(scopedActor, owned.getOwnerActorId());

        mockMvc.perform(as(scopedActor, post("/api/v1/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContractCreateRequest()
                                .setName("Forbidden owner contract").setNumber("C-FORBIDDEN")
                                .setCustomerId(customer.getId()).setOwnerActorId(outsideOwner)
                                .setDiscountRate(BigDecimal.ZERO).setProducts(List.of())))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(scopedActor, put("/api/v1/contracts/{contractId}/owner", owned.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ContractOwnerUpdateRequest()
                                .setVersion(owned.getVersion())
                                .setOwnerActorId(outsideOwner)))))
                .andExpect(status().isForbidden());

        InvoiceResponse invoice = fromJson(mockMvc.perform(as(scopedActor,
                        post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(invoiceRequest(invoiceContract.getId(), "1.00")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        mockMvc.perform(as(scopedActor, post("/api/v1/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(invoiceRequest(invoiceContract.getId(), "1.00")
                                .setOwnerActorId(outsideOwner)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(scopedActor, put("/api/v1/invoices/{invoiceId}/owner", invoice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new InvoiceOwnerUpdateRequest()
                                .setVersion(invoice.getVersion())
                                .setOwnerActorId(outsideOwner)))))
                .andExpect(status().isForbidden());

        InvoiceResponse reassigned = fromJson(mockMvc.perform(as(scopedActor,
                        put("/api/v1/invoices/{invoiceId}/owner", invoice.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new InvoiceOwnerUpdateRequest()
                                        .setVersion(invoice.getVersion())
                                        .setOwnerActorId(scopedActor)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals(customer.getId(), reassigned.getCustomerId());
        assertEquals(invoiceContract.getId(), reassigned.getContractId());

        InvoiceResponse issued = fromJson(mockMvc.perform(as(scopedActor,
                        put("/api/v1/invoices/{invoiceId}/issue", invoice.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new InvoiceIssueRequest()
                                        .setVersion(reassigned.getVersion())
                                        .setInvoiceNumber("F-SCOPE")
                                        .setIssuedDate(LocalDate.of(2027, 2, 3))))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals(customer.getId(), issued.getCustomerId());
        assertEquals(invoiceContract.getId(), issued.getContractId());

        InvoiceResponse reset = fromJson(mockMvc.perform(as(scopedActor,
                        put("/api/v1/invoices/{invoiceId}/reset", invoice.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(new InvoiceVersionRequest()
                                        .setVersion(issued.getVersion())))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        assertEquals(customer.getId(), reset.getCustomerId());
        assertEquals(invoiceContract.getId(), reset.getContractId());
    }

    @Test
    void remainingKeywordSearchUsesFieldSpecificComparisonSemantics() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("B16c search customer");
        ProductResponse product = product();
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "20.00", "2.0000")
                        .setName("B16c Contract%_literal")
                        .setNumber("C-B16C-100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        InvoiceResponse invoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "2.00").setTitle("B16c Invoice%_literal"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        InvoiceResponse issued = fromJson(performPut(
                "/api/v1/invoices/" + invoice.getId() + "/issue",
                new InvoiceIssueRequest().setVersion(invoice.getVersion())
                        .setInvoiceNumber("F-B16C-%_100")
                        .setIssuedDate(LocalDate.of(2027, 7, 18)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);
        ReceivablePlanResponse plan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(contract.getId())
                        .setDueDate(LocalDate.of(2027, 8, 18))
                        .setPlannedAmount(new BigDecimal("10.00"))
                        .setPaymentMethod("LegacyPlanPaymentCandidate"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        ReceivableResponse receipt = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contract.getId())
                        .setReceivablePlanId(plan.getId())
                        .setReceivedDate(LocalDate.of(2027, 7, 18))
                        .setPaymentMethod("LegacyReceiptPaymentCandidate")
                        .setAmount(new BigDecimal("1.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);

        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setKeyword("B16c Contract%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/contracts/query", new ContractQueryRequest()
                .setKeyword("Ｃ－Ｂ１６Ｃ－１"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setKeyword("B16c Invoice%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setKeyword(issued.getInvoiceNumber()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/invoices/query", new InvoiceQueryRequest()
                .setKeyword(invoice.getApplicationNumber()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setKeyword(receipt.getReceiptNumber()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/receivables/query", new ReceivableQueryRequest()
                .setKeyword("LegacyReceiptPaymentCandidate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        performPost("/api/v1/receivable-plans/query", new ReceivablePlanQueryRequest()
                .setKeyword("01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        performPost("/api/v1/receivable-plans/query", new ReceivablePlanQueryRequest()
                .setKeyword("1x"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        performPost("/api/v1/receivable-plans/query", new ReceivablePlanQueryRequest()
                .setKeyword("LegacyPlanPaymentCandidate"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
    }

    @Test
    void blankAndHiddenRemainingKeywordCandidatesReturnSafePages() throws Exception {
        bootstrap();
        long scopedActor = 666666L;
        seedActiveMember(scopedActor, "Hidden search field member");
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long roleId = createHiddenSearchRole(jdbc, scopedActor);
        addHiddenFieldPolicy(jdbc, roleId, "CONTRACT");
        addHiddenFieldPolicy(jdbc, roleId, "INVOICE");
        addHiddenFieldPolicy(jdbc, roleId, "RECEIVABLE");
        addHiddenFieldPolicy(jdbc, roleId, "RECEIVABLE_PLAN");

        // Rows the keyword would match if any candidate were still visible. Without them the
        // zero-row assertions below would hold on an empty tenant whatever the policy did.
        CustomerResponse customer = customer("Hidden search customer");
        ContractResponse matching = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product().getId(), "100.00", "1.0000")
                        .setName("visible text contract").setNumber("VISIBLE-TEXT-1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        performPost("/api/v1/invoices", invoiceRequest(matching.getId(), "10.00")
                        .setTitle("visible text invoice"))
                .andExpect(status().isOk());
        // Receivables and plans cannot be searched by free text at all: their only candidates are
        // receipt_number (LITERAL_PREFIX) and plan_number (EXACT positive integer). A "visible
        // text" keyword misses them on shape rather than on policy, so each needs a keyword its
        // own candidate could actually match. Both numbers are assigned by the server, so they
        // are read back from the create response instead of being recomputed here.
        ReceivableResponse receivable = fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(matching.getId())
                        .setReceivedDate(LocalDate.of(2031, 3, 4)).setPaymentMethod("WIRE")
                        .setAmount(new BigDecimal("10.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
        ReceivablePlanResponse plan = fromJson(performPost("/api/v1/receivable-plans",
                new ReceivablePlanCreateRequest().setContractId(matching.getId())
                        .setDueDate(LocalDate.of(2031, 3, 5))
                        .setPlannedAmount(new BigDecimal("10.00")).setPaymentMethod("WIRE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivablePlanResponse.class);
        String receiptKeyword = receivable.getReceiptNumber();
        String planKeyword = String.valueOf(plan.getInstallment());

        mockMvc.perform(as(scopedActor, post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"   \"}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(scopedActor, post("/api/v1/invoices/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"   \"}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(scopedActor, post("/api/v1/receivables/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"   \"}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(scopedActor, post("/api/v1/receivable-plans/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"   \"}")))
                .andExpect(status().isOk());
        mockMvc.perform(as(scopedActor, post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"visible text\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        mockMvc.perform(as(scopedActor, post("/api/v1/invoices/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"visible text\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        mockMvc.perform(as(scopedActor, post("/api/v1/receivables/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + receiptKeyword + "\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));
        mockMvc.perform(as(scopedActor, post("/api/v1/receivable-plans/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + planKeyword + "\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(0));

        // Control: each keyword reaches its row for a caller whose candidates are visible, so the
        // zero rows above come from the field policy rather than from an unmatchable fixture.
        mockMvc.perform(withTestActor(post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"visible text\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        mockMvc.perform(withTestActor(post("/api/v1/invoices/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"visible text\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        mockMvc.perform(withTestActor(post("/api/v1/receivables/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + receiptKeyword + "\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
        // Scoped to this customer because an installment ordinal restarts at 1 for every contract,
        // so the plan keyword is only unique within one. Narrowing cannot make an unmatched
        // keyword match, so the control still shows the keyword reaches the row.
        mockMvc.perform(withTestActor(post("/api/v1/receivable-plans/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + planKeyword + "\",\"customerId\":"
                                + customer.getId() + "}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalNum").value(1));
    }

    @Test
    void approvalFactsRespectEachRelationshipDomainsCurrentVisibility() throws Exception {
        bootstrap();
        long contractReviewer = 666666L;
        long invoiceReviewer = 555555L;
        seedActiveMember(contractReviewer, "Contract approval reviewer");
        seedActiveMember(invoiceReviewer, "Invoice approval reviewer");
        CustomerResponse customer = customer("Approval relation customer");
        ProductResponse product = product();
        ContactResponse contact = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Approval relation contact")
                        .setCustomerId(customer.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        OpportunityResponse opportunity = fromJson(performPost("/api/v1/opportunities",
                new OpportunityCreateRequest().setName("Approval relation opportunity")
                        .setCustomerId(customer.getId()).setPipelineCode("direct")
                        .setStageCode("qualification").setEstimatedAmount(BigDecimal.TEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        ContractResponse contract = fromJson(performPost("/api/v1/contracts",
                contractRequest(customer.getId(), product.getId(), "10.00", "1.0000")
                        .setName("Approval relation contract")
                        .setNumber("C-APPROVAL-RELATION")
                        .setContactId(contact.getId()).setOpportunityId(opportunity.getId())
                        .setDiscountRate(BigDecimal.ZERO))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContractResponse.class);
        InvoiceResponse invoice = fromJson(performPost("/api/v1/invoices",
                invoiceRequest(contract.getId(), "1.00"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                InvoiceResponse.class);

        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        createApprovalReviewRole(jdbc, contractReviewer,
                "Contract relation approval reviewer", "contracts:read", "CONTRACT");
        createApprovalReviewRole(jdbc, invoiceReviewer,
                "Invoice relation approval reviewer", "invoices:read", "INVOICE");

        ApprovalInstanceResponse contractApproval = relationshipApproval(
                "CONTRACT", contract.getId(), contractReviewer, "status", "ACTIVE",
                List.of("customer", "opportunity", "contact", "status"));
        ApprovalInstanceResponse invoiceApproval = relationshipApproval(
                "INVOICE", invoice.getId(), invoiceReviewer, "status", "PENDING",
                List.of("customer", "contract", "status"));

        ApprovalInstanceResponse contractDetail = fromJson(mockMvc.perform(as(contractReviewer,
                        get("/api/v1/approvals/{instanceId}", contractApproval.id())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        assertEquals(contractApproval.id(), contractDetail.id());

        ApprovalInstanceResponse invoiceDetail = fromJson(mockMvc.perform(as(invoiceReviewer,
                        get("/api/v1/approvals/{instanceId}", invoiceApproval.id())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
        assertEquals(invoiceApproval.id(), invoiceDetail.id());

        mockMvc.perform(as(contractReviewer, post("/api/v1/approvals/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalQueryRequest().setBucket("PENDING")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].facts").doesNotExist());
        mockMvc.perform(as(invoiceReviewer, post("/api/v1/approvals/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalQueryRequest().setBucket("PENDING")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectList[0].facts").doesNotExist());

        mockMvc.perform(as(contractReviewer, post("/api/v1/approvals/actions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new ApprovalBatchActionRequest().setActions(List.of(
                                new ApprovalActionItemRequest()
                                        .setInstanceId(contractApproval.id())
                                        .setVersion(contractApproval.version())
                                        .setAction("APPROVE")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atomic").value(true))
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.items[0].instanceId").value(contractApproval.id()));
    }

    @Test
    void referencedOpportunityAndContactCannotMoveToAnotherCustomer() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Relation customer");
        CustomerResponse otherCustomer = customer("Relation other customer");
        ContactResponse contact = fromJson(performPost("/api/v1/contacts",
                new ContactCreateRequest().setName("Contract contact")
                        .setCustomerId(customer.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ContactResponse.class);
        OpportunityResponse opportunity = fromJson(performPost("/api/v1/opportunities",
                new OpportunityCreateRequest().setName("Contract opportunity")
                        .setCustomerId(customer.getId()).setPipelineCode("direct")
                        .setStageCode("qualification").setEstimatedAmount(BigDecimal.TEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
        performPost("/api/v1/contracts", new ContractCreateRequest()
                .setName("Related contract").setNumber("C-RELATION")
                .setCustomerId(customer.getId()).setContactId(contact.getId())
                .setOpportunityId(opportunity.getId()).setDiscountRate(BigDecimal.ZERO)
                .setProducts(List.of()))
                .andExpect(status().isOk());

        performPut("/api/v1/contacts/" + contact.getId(),
                new ContactUpdateRequest().setVersion(contact.getVersion()).setCustomerId(null))
                .andExpect(status().isUnprocessableEntity());
        performPut("/api/v1/opportunities/" + opportunity.getId(),
                new OpportunityUpdateRequest().setVersion(opportunity.getVersion())
                        .setName(opportunity.getName()).setCustomerId(otherCustomer.getId())
                        .setPipelineCode(opportunity.getPipelineCode())
                        .setStageCode(opportunity.getStageCode())
                        .setExpectedCloseDate(opportunity.getExpectedCloseDate())
                        .setEstimatedAmount(opportunity.getEstimatedAmount())
                        .setNextContactTime(opportunity.getNextContactTime())
                        .setDescription(opportunity.getDescription())
                        .setCustomFields(opportunity.getCustomFields()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invoiceProfileCapacityIsBoundedUnderConcurrentCreationAndQuery() throws Exception {
        bootstrap();
        CustomerResponse customer = customer("Profile capacity customer");
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        for (int index = 0; index < 99; index++) {
            jdbc.update("INSERT INTO crm_invoice_profile "
                            + "(tenant_id, customer_id, title_type, title, status) "
                            + "VALUES (?, ?, 'INDIVIDUAL', ?, 'ACTIVE')",
                    TENANT_ID, customer.getId(), "Seed profile " + index);
        }

        List<Integer> statuses = runConcurrently(
                () -> profileCreateStatus(customer.getId(), "Concurrent profile A"),
                () -> profileCreateStatus(customer.getId(), "Concurrent profile B"));
        assertEquals(1L, statuses.stream().filter(value -> value == 200).count());
        assertEquals(1L, statuses.stream().filter(value -> value == 422).count());
        assertEquals(100, jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_invoice_profile "
                        + "WHERE tenant_id = ? AND customer_id = ? AND status = 'ACTIVE'",
                Integer.class, TENANT_ID, customer.getId()));
        performGetWithParams("/api/v1/invoice-profiles",
                Map.of("customerId", customer.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(100));
    }

    private ApprovalInstanceResponse approval(
            String objectType, String name, String fieldCode,
            String expectedValue, Long subjectId) throws Exception {
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest()
                .setObjectType(objectType).setName(name).setRepeatMode("ONCE")
                .setInitiatorActorIds(List.of(ACTOR_ID)).setManagerActorIds(List.of(ACTOR_ID))
                .setSteps(List.of(new ApprovalStepRequest().setCode("owner-review")
                        .setName("Owner review").setOrder(0).setType("MEMBER")
                        .setMode("ALL").setFallback("MANAGERS")
                        .setMemberActorIds(List.of(ACTOR_ID))
                        .setConditions(List.of(new ApprovalConditionRequest()
                                .setFieldCode(fieldCode).setOperator("EQ")
                                .setValues(List.of(expectedValue))))));
        ApprovalDefinitionResponse definition = fromJson(performPost(
                "/api/v1/approval-definitions", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        definition = fromJson(performPut("/api/v1/approval-definitions/"
                        + definition.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(definition.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        return fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(subjectId).setTitle(name)
                        .setIdempotencyKey(objectType.toLowerCase() + "-approval")
                        .setFacts(Map.of(fieldCode, List.of("1"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
    }

    private ApprovalInstanceResponse relationshipApproval(
            String objectType, Long subjectId, long reviewer,
            String conditionField, String expectedValue, List<String> factCodes)
            throws Exception {
        ApprovalDefinitionRequest request = new ApprovalDefinitionRequest()
                .setObjectType(objectType).setName(objectType + " relationship approval")
                .setRepeatMode("ONCE").setInitiatorActorIds(List.of(ACTOR_ID))
                .setManagerActorIds(List.of(ACTOR_ID))
                .setSteps(List.of(new ApprovalStepRequest().setCode("relationship-review")
                        .setName("Relationship review").setOrder(0).setType("MEMBER")
                        .setMode("ALL").setFallback("MANAGERS")
                        .setMemberActorIds(List.of(reviewer))
                        .setConditions(List.of(new ApprovalConditionRequest()
                                .setFieldCode(conditionField).setOperator("EQ")
                                .setValues(List.of(expectedValue))))));
        ApprovalDefinitionResponse definition = fromJson(performPost(
                "/api/v1/approval-definitions", request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        definition = fromJson(performPut("/api/v1/approval-definitions/"
                        + definition.id() + "/status",
                new ApprovalDefinitionStatusRequest().setVersion(definition.version())
                        .setStatus("ACTIVE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalDefinitionResponse.class);
        java.util.LinkedHashMap<String, List<String>> requestedFacts =
                new java.util.LinkedHashMap<>();
        factCodes.forEach(code -> requestedFacts.put(code, List.of(
                conditionField.equals(code) ? expectedValue : "1")));
        return fromJson(performPost("/api/v1/approvals",
                new ApprovalSubmitRequest().setDefinitionId(definition.id())
                        .setSubjectId(subjectId).setTitle(objectType + " relationship approval")
                        .setIdempotencyKey(objectType.toLowerCase()
                                + "-relationship-approval")
                        .setFacts(requestedFacts))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ApprovalInstanceResponse.class);
    }

    private CustomerResponse customer(String name) throws Exception {
        return fromJson(performPost("/api/v1/customers",
                new CustomerCreateRequest().setName(name))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                CustomerResponse.class);
    }

    private OpportunityResponse opportunity(Long customerId, String name, String amount)
            throws Exception {
        return opportunity(customerId, name, amount, null);
    }

    private OpportunityResponse opportunity(
            Long customerId, String name, String amount, Long ownerActorId)
            throws Exception {
        OpportunityCreateRequest request = new OpportunityCreateRequest()
                .setName(name).setCustomerId(customerId)
                .setPipelineCode("direct").setStageCode("qualification")
                .setEstimatedAmount(new BigDecimal(amount));
        if (ownerActorId != null) {
            request.setOwnerActorId(ownerActorId);
        }
        return fromJson(performPost("/api/v1/opportunities",
                request)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
    }

    private OpportunityResponse closeOpportunity(
            OpportunityResponse opportunity, String outcome) throws Exception {
        return fromJson(performPut("/api/v1/opportunities/" + opportunity.getId() + "/outcome",
                new OpportunityOutcomeUpdateRequest().setVersion(opportunity.getVersion())
                        .setOutcome(outcome))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                OpportunityResponse.class);
    }

    private ReceivableResponse receivable(Long contractId, long ownerActorId, String amount)
            throws Exception {
        return fromJson(performPost("/api/v1/receivables",
                new ReceivableCreateRequest().setContractId(contractId)
                        .setOwnerActorId(ownerActorId).setReceivedDate(LocalDate.now())
                        .setPaymentMethod("WIRE").setAmount(new BigDecimal(amount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ReceivableResponse.class);
    }

    private ProductResponse product() throws Exception {
        return fromJson(performPost("/api/v1/products",
                new ProductCreateRequest().setName("Service").setCode("SERVICE")
                        .setUnit("seat").setListPrice(new BigDecimal("80.00"))
                        .setAvailability("AVAILABLE"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                ProductResponse.class);
    }

    private ContractCreateRequest contractRequest(
            Long customerId, Long productId, String unitPrice, String quantity) {
        return new ContractCreateRequest().setName("Annual contract").setNumber("C-100")
                .setCustomerId(customerId).setDiscountRate(new BigDecimal("10.0000"))
                .setProducts(List.of(new ContractProductLineRequest().setProductId(productId)
                        .setUnitPrice(new BigDecimal(unitPrice))
                        .setQuantity(new BigDecimal(quantity))));
    }

    private ContractUpdateRequest contractUpdate(
            ContractResponse contract, Long customerId, Long productId,
            String unitPrice, String quantity) {
        return new ContractUpdateRequest().setVersion(contract.getVersion())
                .setName(contract.getName()).setNumber(contract.getNumber())
                .setCustomerId(customerId).setDiscountRate(contract.getDiscountRate())
                .setProducts(List.of(new ContractProductLineRequest().setProductId(productId)
                        .setUnitPrice(new BigDecimal(unitPrice))
                        .setQuantity(new BigDecimal(quantity))));
    }

    private InvoiceCreateRequest invoiceRequest(Long contractId, String amount) {
        return new InvoiceCreateRequest().setContractId(contractId)
                .setAmount(new BigDecimal(amount)).setInvoiceType("VAT")
                .setTitleType("ORGANIZATION").setTitle("Contract customer")
                .setTaxNumber("TAX-100");
    }

    private void bootstrap() throws Exception {
        bootstrap(ACTOR_ID, TENANT_ID, "Contract owner");
    }

    private void bootstrap(long actorId, long tenantId, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/organization/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new OrganizationBootstrapRequest()
                                .setDisplayName(displayName)))
                        .header("X-Dev-User-Id", actorId)
                        .header("X-Dev-Tenant-Id", tenantId))
                .andExpect(status().isOk());
    }

    private void assertExplicitRouteCapabilities() throws Exception {
        long member = 777777L;
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile "
                        + "(actor_id, display_name) VALUES (?, ?)",
                member, "Contract member");
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, member);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) "
                        + "SELECT tenant_id, ?, id FROM authorization_role "
                        + "WHERE tenant_id = ? AND system_key = 'TENANT_MEMBER'",
                member, TENANT_ID);

        mockMvc.perform(as(member, post("/api/v1/contracts/query")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, put("/api/v1/invoices/1/issue")
                        .contentType(MediaType.APPLICATION_JSON)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, get("/api/v1/invoice-profiles")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, get("/api/v1/reports/core")))
                .andExpect(status().isForbidden());
    }

    private void seedActiveMember(long actorId, String displayName) {
        seedActiveMember(actorId, displayName, null);
    }

    private void seedActiveMember(long actorId, String displayName, Long departmentId) {
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO organization_member_profile "
                        + "(actor_id, display_name) VALUES (?, ?)",
                actorId, displayName);
        jdbc.update("INSERT INTO organization_membership "
                        + "(tenant_id, actor_id, department_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                TENANT_ID, actorId, departmentId);
    }

    private Long coreReportReader(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId, String roleName) {
        return coreReportReader(jdbc, actorId, roleName, null,
                "TENANT", "TENANT", "TENANT");
    }

    private Long coreReportReader(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId, String roleName,
            Long departmentId, String opportunityScope, String contractScope,
            String receivableScope) {
        seedActiveMember(actorId, roleName, departmentId);
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, roleName);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, roleName);
        grant(jdbc, roleId, "reports:read", null);
        grant(jdbc, roleId, "opportunities:read", opportunityScope);
        grant(jdbc, roleId, "contracts:read", contractScope);
        grant(jdbc, roleId, "receivables:read", receivableScope);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        addAllBuiltinFieldAccesses(jdbc, roleId, "OPPORTUNITY", "READ");
        addAllBuiltinFieldAccesses(jdbc, roleId, "CONTRACT", "READ");
        addAllBuiltinFieldAccesses(jdbc, roleId, "RECEIVABLE", "READ");
        return roleId;
    }

    private void grant(
            org.springframework.jdbc.core.JdbcTemplate jdbc, Long roleId,
            String authority, String dataScope) {
        if (dataScope == null && !"reports:read".equals(authority)) {
            return;
        }
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) VALUES (?, ?, ?, ?)",
                TENANT_ID, roleId, authority, dataScope);
    }

    private long insertDepartment(
            org.springframework.jdbc.core.JdbcTemplate jdbc, String name) {
        jdbc.update("INSERT INTO organization_department (tenant_id, name, status) "
                        + "VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, name);
        return jdbc.queryForObject("SELECT id FROM organization_department "
                        + "WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, name);
    }

    private Long createRestrictedBusinessRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId) {
        return createRestrictedBusinessRole(jdbc, actorId, "TENANT");
    }

    /**
     * @param readScope the {@code contracts:read} data scope. Pass {@code "SELF"} to make the
     *                  collaborator relation the only route to a contract the caller does not own;
     *                  under {@code "TENANT"} the caller sees every contract regardless of it.
     */
    private Long createRestrictedBusinessRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId, String readScope) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Scoped contract writer");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Scoped contract writer");
        Map.of("contracts:read", readScope, "contracts:write", "TENANT",
                        "contracts:assign", "SELF", "invoices:write", "TENANT")
                .forEach((authority, scope) -> jdbc.update(
                        "INSERT INTO authorization_role_grant "
                                + "(tenant_id, role_id, authority_code, data_scope) "
                                + "VALUES (?, ?, ?, ?)",
                        TENANT_ID, roleId, authority, scope));
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        return roleId;
    }

    private Long createReceivableWriterRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "No-plan receivable writer");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "No-plan receivable writer");
        Map.of("contracts:read", "TENANT", "receivables:write", "TENANT")
                .forEach((authority, scope) -> jdbc.update(
                        "INSERT INTO authorization_role_grant "
                                + "(tenant_id, role_id, authority_code, data_scope) "
                                + "VALUES (?, ?, ?, ?)",
                        TENANT_ID, roleId, authority, scope));
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        return roleId;
    }

    private Long createPlanBoundReceivableWriterRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Restricted plan-bound receivable writer");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Restricted plan-bound receivable writer");
        Map.of("contracts:read", "TENANT", "receivables:write", "TENANT",
                        "receivable-plans:read", "TENANT", "receivable-plans:write", "TENANT")
                .forEach((authority, scope) -> jdbc.update(
                        "INSERT INTO authorization_role_grant "
                                + "(tenant_id, role_id, authority_code, data_scope) "
                                + "VALUES (?, ?, ?, ?)",
                        TENANT_ID, roleId, authority, scope));
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        return roleId;
    }

    private Long createAssignmentRestrictedRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Self-scoped contract and invoice writer");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Self-scoped contract and invoice writer");
        Map.of("customers:read", "TENANT", "contracts:read", "TENANT",
                        "contracts:write", "TENANT", "contracts:assign", "SELF",
                        "invoices:write", "TENANT", "invoices:assign", "SELF",
                        "invoices:issue", "TENANT")
                .forEach((authority, scope) -> jdbc.update(
                        "INSERT INTO authorization_role_grant "
                                + "(tenant_id, role_id, authority_code, data_scope) "
                                + "VALUES (?, ?, ?, ?)",
                        TENANT_ID, roleId, authority, scope));
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        return roleId;
    }

    private Long createHiddenSearchRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, "Hidden contract and invoice search fields");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, "Hidden contract and invoice search fields");
        Map.of("contracts:read", "TENANT", "invoices:read", "TENANT",
                        "receivables:read", "TENANT", "receivable-plans:read", "TENANT")
                .forEach((authority, scope) -> jdbc.update(
                        "INSERT INTO authorization_role_grant "
                                + "(tenant_id, role_id, authority_code, data_scope) "
                                + "VALUES (?, ?, ?, ?)",
                        TENANT_ID, roleId, authority, scope));
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        return roleId;
    }

    private Long createApprovalReviewRole(
            org.springframework.jdbc.core.JdbcTemplate jdbc, long actorId, String roleName,
            String recordAuthority, String objectType) {
        jdbc.update("INSERT INTO authorization_role "
                        + "(tenant_id, name, status) VALUES (?, ?, 'ACTIVE')",
                TENANT_ID, roleName);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM authorization_role WHERE tenant_id = ? AND name = ?",
                Long.class, TENANT_ID, roleName);
        jdbc.update("INSERT INTO authorization_role_grant "
                        + "(tenant_id, role_id, authority_code, data_scope) "
                        + "VALUES (?, ?, 'approvals:read', NULL), "
                        + "(?, ?, 'approvals:review', NULL), (?, ?, ?, 'TENANT')",
                TENANT_ID, roleId, TENANT_ID, roleId,
                TENANT_ID, roleId, recordAuthority);
        jdbc.update("INSERT INTO authorization_role_assignment "
                        + "(tenant_id, actor_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID, actorId, roleId);
        addAllBuiltinFieldAccesses(jdbc, roleId, objectType, "READ");
        return roleId;
    }

    private int profileCreateStatus(Long customerId, String title) throws Exception {
        return performPost("/api/v1/invoice-profiles",
                new InvoiceProfileRequest().setCustomerId(customerId)
                        .setTitleType("INDIVIDUAL").setTitle(title))
                .andReturn().getResponse().getStatus();
    }

    @SafeVarargs
    private final List<Integer> runConcurrently(java.util.concurrent.Callable<Integer>... tasks)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<Integer>> futures = Arrays.stream(tasks).map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("invoice profile start timeout");
                }
                return task.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            java.util.ArrayList<Integer> statuses = new java.util.ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(10, TimeUnit.SECONDS));
            }
            return List.copyOf(statuses);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void addAllBuiltinFieldAccesses(
            org.springframework.jdbc.core.JdbcTemplate jdbc, Long roleId,
            String objectType, String accessLevel) {
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                TENANT_ID, roleId, objectType);
        jdbc.update("INSERT INTO role_field_access "
                        + "(tenant_id, role_id, object_type, field_id, access_level, mask_type) "
                        + "SELECT ?, ?, ?, id, ?, 'NONE' FROM custom_field_definition "
                        + "WHERE tenant_id = ? AND object_type = ? AND is_builtin = 1",
                TENANT_ID, roleId, objectType, accessLevel, TENANT_ID, objectType);
    }

    private void addHiddenFieldPolicy(
            org.springframework.jdbc.core.JdbcTemplate jdbc, Long roleId, String objectType) {
        jdbc.update("INSERT INTO role_field_policy "
                        + "(tenant_id, role_id, object_type) VALUES (?, ?, ?)",
                TENANT_ID, roleId, objectType);
    }

    private void setBuiltinFieldAccess(
            org.springframework.jdbc.core.JdbcTemplate jdbc, Long roleId,
            String objectType, String code, String accessLevel) {
        jdbc.update("UPDATE role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "SET access_rule.access_level = ?, access_rule.mask_type = 'NONE' "
                        + "WHERE access_rule.tenant_id = ? AND access_rule.role_id = ? "
                        + "AND access_rule.object_type = ? AND field_definition.field_code = ?",
                accessLevel, TENANT_ID, roleId, objectType, code);
    }

    private void hideBuiltinFieldAccess(
            org.springframework.jdbc.core.JdbcTemplate jdbc, Long roleId,
            String objectType, String code) {
        jdbc.update("DELETE access_rule FROM role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "WHERE access_rule.tenant_id = ? AND access_rule.role_id = ? "
                        + "AND access_rule.object_type = ? AND field_definition.field_code = ?",
                TENANT_ID, roleId, objectType, code);
    }

    private void maskBuiltinFieldAccess(
            org.springframework.jdbc.core.JdbcTemplate jdbc, Long roleId,
            String objectType, String code, String maskType) {
        jdbc.update("UPDATE role_field_access access_rule "
                        + "JOIN custom_field_definition field_definition "
                        + "ON field_definition.tenant_id = access_rule.tenant_id "
                        + "AND field_definition.id = access_rule.field_id "
                        + "SET access_rule.mask_type = ? WHERE access_rule.tenant_id = ? "
                        + "AND access_rule.role_id = ? AND access_rule.object_type = ? "
                        + "AND field_definition.field_code = ?",
                maskType, TENANT_ID, roleId, objectType, code);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            long actorId,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return as(actorId, TENANT_ID, request);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder as(
            long actorId, long tenantId,
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request.header("X-Dev-User-Id", actorId)
                .header("X-Dev-Tenant-Id", tenantId);
    }

    @Override
    protected void initTestData() {
        clearContractInvoiceData();
    }

    @Override
    protected void clearTestData() {
        clearContractInvoiceData();
    }

    private void clearContractInvoiceData() {
        executeSql("DELETE FROM approval_review_task");
        executeSql("DELETE FROM approval_instance_manager");
        executeSql("DELETE FROM approval_instance_watcher");
        executeSql("DELETE FROM approval_instance");
        executeSql("DELETE FROM approval_definition");
        executeSql("DELETE FROM crm_receivable_unique_field_value");
        executeSql("DELETE FROM crm_receivable_field_value");
        executeSql("DELETE FROM crm_receivable");
        executeSql("DELETE FROM crm_receivable_plan_unique_field_value");
        executeSql("DELETE FROM crm_receivable_plan_field_value");
        executeSql("DELETE FROM crm_receivable_plan");
        executeSql("DELETE FROM crm_invoice_unique_field_value");
        executeSql("DELETE FROM crm_invoice_field_value");
        executeSql("DELETE FROM crm_invoice_profile");
        executeSql("DELETE FROM crm_invoice");
        executeSql("DELETE FROM crm_contract_unique_field_value");
        executeSql("DELETE FROM crm_contract_field_value");
        executeSql("DELETE FROM crm_contract_product_line");
        executeSql("DELETE FROM crm_contract_collaborator");
        executeSql("DELETE FROM crm_contract");
        executeSql("DELETE FROM crm_opportunity_star");
        executeSql("DELETE FROM crm_opportunity_unique_field_value");
        executeSql("DELETE FROM crm_opportunity_field_value");
        executeSql("DELETE FROM crm_opportunity_product_line");
        executeSql("DELETE FROM crm_opportunity_collaborator");
        executeSql("DELETE FROM crm_opportunity_contact");
        executeSql("DELETE FROM crm_opportunity");
        executeSql("DELETE FROM crm_contact_star");
        executeSql("DELETE FROM crm_contact_unique_field_value");
        executeSql("DELETE FROM crm_contact_field_value");
        executeSql("DELETE FROM crm_contact_collaborator");
        executeSql("DELETE FROM crm_contact_tag");
        executeSql("DELETE FROM crm_contact_address");
        executeSql("UPDATE crm_contact SET parent_contact_id = NULL");
        executeSql("DELETE FROM crm_contact");
        executeSql("DELETE FROM crm_contact_guard");
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
