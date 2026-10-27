package io.github.archetom.crm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that the generated machine-readable API contract is actually served. */
@RequiresDatabase
class OpenApiIntegrationTest extends BaseIntegrationTest {

    @Test
    void publishesProjectIdentityAndVersion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/open/marketing-forms/{publicId}/submissions']"
                        + "['post']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("MarketingFormSubmissionResponse")))
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("atom-crm-test API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$['components']['securitySchemes']['devUserId']['name']")
                        .value("X-Dev-User-Id"))
                .andExpect(jsonPath("$['components']['securitySchemes']['devTenantId']['name']")
                        .value("X-Dev-Tenant-Id"));
    }

    @Test
    void publishesTypedOpportunityProductsReplacementContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}/products']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("OpportunityProductsResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}/products']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("OpportunityProductsUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}/products']"
                        + "['put']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("OpportunityProductsResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityProductsResponse']"
                        + "['properties']['lines']['items']['$ref']")
                        .value(containsString("OpportunityProductLineResponse")));
    }

    @Test
    void publishesTypedContractDetailContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ContractResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ContractResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}']"
                        + "['put']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ContractResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}']"
                        + "['patch']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ContractResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ContractResponse']['properties']['products']"
                        + "['items']['$ref']")
                        .value(containsString("ContractProductLineResponse")));
    }

    @Test
    void publishesTypedContractProductsReplacementContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}/products']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ContractProductsResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}/products']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContractProductsUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}/products']"
                        + "['put']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ContractProductsResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ContractProductsResponse']"
                        + "['properties']['lines']['items']['$ref']")
                        .value(containsString("ContractProductSnapshotResponse")));
    }

    @Test
    void publishesContractBatchMutationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/batch/owner']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContractBatchOwnerUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/batch/delete']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContractBatchDeleteRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/batch/export/csv']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContractBatchCsvExportRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/batch/collaborators']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContractBatchCollaboratorAddRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/batch/collaborators']"
                        + "['delete']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContractBatchCollaboratorRemoveRequest")))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchOwnerUpdateRequest']"
                        + "['properties']['contracts']['items']['$ref']")
                        .value(containsString("ContractBatchVersionRequest")))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchCollaboratorAddRequest']"
                        + "['properties']['collaborators']['items']['$ref']")
                        .value(containsString("ContractCollaboratorRequest")))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchCollaboratorAddRequest']"
                        + "['properties']['contracts']['minItems']")
                        .value(1))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchCollaboratorAddRequest']"
                        + "['properties']['collaborators']['minItems']")
                        .value(1))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchCollaboratorRemoveRequest']"
                        + "['properties']['contracts']['minItems']")
                        .value(1))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchCollaboratorRemoveRequest']"
                        + "['properties']['actorIds']['minItems']")
                        .value(1))
                .andExpect(jsonPath("$['components']['schemas']['ContractBatchCsvExportRequest']"
                        + "['properties']['contracts']['minItems']")
                        .value(1));
    }

    @Test
    void publishesLeadAndContactBatchMutationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/leads/batch/owner']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("LeadBatchOwnerUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/leads/batch/delete']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("LeadBatchDeleteRequest")))
                .andExpect(jsonPath("$['components']['schemas']['LeadBatchOwnerUpdateRequest']"
                        + "['properties']['leads']['items']['$ref']")
                        .value(containsString("LeadBatchVersionRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contacts/batch/owner']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContactBatchOwnerUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/contacts/batch/delete']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ContactBatchDeleteRequest")))
                .andExpect(jsonPath("$['components']['schemas']['ContactBatchOwnerUpdateRequest']"
                        + "['properties']['contacts']['items']['$ref']")
                        .value(containsString("ContactBatchVersionRequest")));
    }

    @Test
    void publishesOpportunityBatchMutationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/batch/owner']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("OpportunityBatchOwnerUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/batch/delete']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("OpportunityBatchDeleteRequest")))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityBatchOwnerUpdateRequest']"
                        + "['properties']['opportunities']['items']['$ref']")
                        .value(containsString("OpportunityBatchVersionRequest")));
    }

    @Test
    void publishesReceivableAndInvoiceBatchMutationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/receivables/batch/owner']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ReceivableBatchOwnerUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/receivables/batch/delete']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("ReceivableBatchDeleteRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/invoices/batch/owner']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("InvoiceBatchOwnerUpdateRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/invoices/batch/reset']"
                        + "['put']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("InvoiceBatchResetRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/invoices/batch/delete']"
                        + "['post']['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("InvoiceBatchDeleteRequest")))
                .andExpect(jsonPath("$['components']['schemas']['InvoiceBatchResetRequest']"
                        + "['properties']['invoices']['items']['$ref']")
                        .value(containsString("InvoiceBatchVersionRequest")));
    }

    @Test
    void publishesMarketingActivityParticipantContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityCreateRequest']"
                        + "['properties']['participantDepartmentIds']['items']['minimum']").value(1))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityCreateRequest']"
                        + "['properties']['marketingForm']['$ref']")
                        .value(containsString("MarketingFormCreateRequest")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityResponse']['required']")
                        .value(hasItems("participantActorIds", "participantDepartmentIds", "viewCount",
                                "submissionCount")));
    }

    @Test
    void publishesTypedUserManagementContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/users']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("UserPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['UserPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("UserResponse")))
                .andExpect(jsonPath("$['components']['schemas']['UserPageResponse']['required']")
                        .value(hasItems("pageNum", "pageSize", "totalNum", "objectList")))
                .andExpect(jsonPath("$['components']['schemas']['UserResponse']['required']")
                        .value(hasItems("id", "username", "email", "maskedPhoneNumber", "realName",
                                "status", "statusName", "active", "createdTime", "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['UserResponse']['properties']['status']['enum']")
                        .value(hasItems("ACTIVE", "INACTIVE", "LOCKED")))
                .andExpect(jsonPath("$['components']['schemas']['Pager']['properties']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['Pager']['properties']")
                        .value(org.hamcrest.Matchers.aMapWithSize(5)));
    }

    @Test
    void publishesTypedCustomerPoolContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/customer-pools']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("CustomerPoolPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['CustomerPoolPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("CustomerPoolResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/customer-pools/catalog']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("CustomerPoolResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/customer-pools/{poolId}/customers']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("CustomerPoolCustomerPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['CustomerPoolCustomerPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("CustomerResponse")));
    }

    @Test
    void publishesTypedOrganizationDepartmentAndMembershipContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/organization/departments']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("DepartmentResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/organization/members']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MemberProfilePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/organization/departments/{departmentId}/members']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("DepartmentMemberProfilePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/organization/departments/{departmentId}/members']['get']"
                        + "['parameters'][1]['schema']['$ref']")
                        .value(containsString("DepartmentMemberQueryRequest")))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentMemberQueryRequest']['properties']"
                        + "['includeDescendants']['type']").value("boolean"))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentMemberProfilePageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("DepartmentMemberProfileResponse")))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentMemberProfileResponse']['properties']"
                        + "['departmentName']['type']").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentMemberProfileResponse']['properties']"
                        + "['loginName']['type']").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentMemberProfileResponse']['properties']"
                        + "['roleNames']['items']['type']").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentMemberProfileResponse']['required']")
                        .value(hasItems("actorId", "displayName", "active", "departmentName")))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentResponse']['required']")
                        .value(hasItems("id", "name", "status", "active")))
                .andExpect(jsonPath("$['components']['schemas']['MemberProfileResponse']['required']")
                        .value(hasItems("actorId", "displayName", "active")))
                .andExpect(jsonPath("$['paths']['/api/v1/organization/membership-requests']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MembershipRequestPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/organization/membership-requests/mine']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MembershipRequestPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MembershipRequestPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("MembershipRequestResponse")));
    }

    @Test
    void publishesTypedAuthorizationRoleManagementContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/authorization/capabilities']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("AuthorityCapabilityResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/authorization/roles']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("TenantRolePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/authorization/roles']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("TenantRoleResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/authorization/roles/{roleId}/grants']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("TenantRoleResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/authorization/roles/{roleId}/assignments']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("RoleAssignmentPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['TenantRolePageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("TenantRoleResponse")))
                .andExpect(jsonPath("$['components']['schemas']['RoleAssignmentPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("RoleAssignmentResponse")))
                .andExpect(jsonPath("$['components']['schemas']['TenantRoleResponse']['required']")
                        .value(hasItems("id", "name", "status", "grants", "version")))
                .andExpect(jsonPath("$['components']['schemas']['RoleGrantResponse']['required']")
                        .value(hasItems("authorityCode")))
                .andExpect(jsonPath("$['components']['schemas']['RoleAssignmentResponse']['required']")
                        .value(hasItems("actorId", "roleId", "createdTime")));
    }

    @Test
    void publishesTypedFieldCustomizationContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/fields/objects']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("BusinessObjectResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/fields/objects/{objectType}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("FieldDefinitionResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/fields/objects/{objectType}']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("FieldDefinitionResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/fields/objects/{objectType}/layout']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("FieldLayoutResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/fields/roles/{roleId}/objects/{objectType}/access']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("RoleFieldPolicyResponse")))
                .andExpect(jsonPath("$['components']['schemas']['FieldDefinitionResponse']['required']")
                        .value(hasItems("id", "objectType", "code", "fieldType", "status", "version")))
                .andExpect(jsonPath("$['components']['schemas']['FieldCreateRequest']['properties']['fieldType']['enum']")
                        .value(hasItems("TEXT", "MULTI_SELECT", "ADDRESS")))
                .andExpect(jsonPath("$['components']['schemas']['FieldDefinitionResponse']['properties']['fieldType']['enum']")
                        .value(hasItems("TEXT", "MULTI_SELECT", "ADDRESS")))
                .andExpect(jsonPath("$['components']['schemas']['FieldDefinitionResponse']"
                        + "['properties']['filterOperators']['items']['$ref']")
                        .value(containsString("FilterOperatorOptionResponse")))
                .andExpect(jsonPath("$['components']['schemas']['FilterOperatorOptionResponse']"
                        + "['properties']['code']['enum']")
                        .value(hasItems("EQ", "BETWEEN", "IS_EMPTY")))
                .andExpect(jsonPath("$['components']['schemas']['FilterOperatorOptionResponse']['required']")
                        .value(hasItems("code", "minimumValueCount", "maximumValueCount")))
                .andExpect(jsonPath("$['components']['schemas']['RoleFieldAccessRequest']['properties']['accessLevel']['enum']")
                        .value(hasItems("HIDDEN", "READ", "WRITE")))
                .andExpect(jsonPath("$['components']['schemas']['RoleFieldAccessResponse']['properties']['maskType']['enum']")
                        .value(hasItems("NONE", "PARTIAL", "FULL")))
                .andExpect(jsonPath("$['components']['schemas']['FieldLayoutResponse']['properties']['version']['type']")
                        .value(hasItems("integer", "null")))
                .andExpect(jsonPath("$['components']['schemas']['RoleFieldPolicyResponse']['properties']['version']['type']")
                        .value(hasItems("integer", "null")))
                .andExpect(jsonPath("$['components']['schemas']['FieldLayoutResponse']['required']")
                        .value(hasItems("objectType", "orderedCodes", "hiddenCodes", "pinnedCodes", "version")))
                .andExpect(jsonPath("$['components']['schemas']['RoleFieldPolicyResponse']['required']")
                        .value(hasItems("roleId", "objectType", "accesses", "version")));
    }

    @Test
    void publishesTypedNavigationPreferenceContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/navigation/preferences']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("NavigationPreferenceResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/navigation/preferences']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("NavigationPreferenceResponse")))
                .andExpect(jsonPath("$['components']['schemas']['NavigationPreferenceResponse']['required']")
                        .value(hasItems("orderedCodes", "hiddenCodes", "version", "availableItems")))
                .andExpect(jsonPath("$['components']['schemas']['NavigationPreferenceResponse']"
                        + "['properties']['version']['type']")
                        .value(hasItems("integer", "null")))
                .andExpect(jsonPath("$['components']['schemas']['NavigationPreferenceResponse']"
                        + "['properties']['availableItems']['items']['$ref']")
                        .value(containsString("NavigationItemResponse")))
                .andExpect(jsonPath("$['components']['schemas']['NavigationItemResponse']['required']")
                        .value(hasItems("code", "parentCode", "route", "titleKey", "order",
                                "requiredAuthority", "children")));
    }

    @Test
    void publishesTypedEmbeddedPageContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/embedded-pages/configuration-options']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("EmbeddedPageConfigurationOptionsResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/embedded-pages']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("EmbeddedPagePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/embedded-pages']['get']['parameters'][*]['name']")
                        .value(hasItems("page", "size", "objectType", "platform", "status")))
                .andExpect(jsonPath("$['paths']['/api/v1/embedded-pages']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("EmbeddedPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/embedded-pages/{pageId}']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("EmbeddedPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/embedded-pages/objects/{objectType}/{recordId}']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("RenderedEmbeddedPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['EmbeddedPagePageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("EmbeddedPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['EmbeddedPageResponse']['required']")
                        .value(hasItems("id", "objectType", "platform", "name", "urlTemplate",
                                "sortOrder", "status", "createdByActorId", "version", "createdTime",
                                "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['RenderedEmbeddedPageResponse']['required']")
                        .value(hasItems("id", "name", "url", "sortOrder")))
                .andExpect(jsonPath("$['components']['schemas']['EmbeddedPageConfigurationOptionsResponse']"
                        + "['properties']['objectTypes']['items']['$ref']")
                        .value(containsString("BusinessObjectResponse")))
                .andExpect(jsonPath("$['components']['schemas']['EmbeddedPageConfigurationOptionsResponse']"
                        + "['required']")
                        .value(hasItems("objectTypes", "platforms", "statuses")));
    }

    @Test
    void publishesCredentialSafeLeadIntakeClientContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/lead-intake-clients']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("LeadIntakeClientResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/lead-intake-clients']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("LeadIntakeClientCreatedResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/lead-intake-clients/{clientId}']['delete']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("LeadIntakeClientResponse")))
                .andExpect(jsonPath("$['components']['schemas']['LeadIntakeClientResponse']['required']")
                        .value(hasItems("id", "keyId", "ownerActorId", "active", "version",
                                "createdTime", "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['LeadIntakeClientResponse']"
                        + "['properties']['apiKey']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['LeadIntakeClientCreatedResponse']"
                        + "['required']").value(hasItems("client", "apiKey")))
                .andExpect(jsonPath("$['components']['schemas']['LeadIntakeClientCreatedResponse']"
                        + "['properties']['client']['$ref']")
                        .value(containsString("LeadIntakeClientResponse")));
    }

    @Test
    void publishesLeadConversionQueryContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['LeadQueryRequest']"
                        + "['properties']['converted']['type']").value("boolean"));
    }

    @Test
    void publishesNullableContactCustomerRelationUpdateContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['ContactUpdateRequest']"
                        + "['properties']['customerId']['type'][0]").value("integer"))
                .andExpect(jsonPath("$['components']['schemas']['ContactUpdateRequest']"
                        + "['properties']['customerId']['type'][1]").value("null"))
                .andExpect(jsonPath("$['components']['schemas']['ContactUpdateRequest']"
                        + "['properties']['customerId']['description']")
                        .value("Optional customer relation; send null to clear it"));
    }

    @Test
    void publishesNullableLeadCustomerRelationUpdateContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['LeadUpdateRequest']"
                        + "['properties']['customerId']['type'][0]").value("integer"))
                .andExpect(jsonPath("$['components']['schemas']['LeadUpdateRequest']"
                        + "['properties']['customerId']['type'][1]").value("null"))
                .andExpect(jsonPath("$['components']['schemas']['LeadUpdateRequest']"
                        + "['properties']['customerId']['description']")
                        .value("Optional customer relation; send null to clear it"));
    }

    @Test
    void publishesNullableLeadContactAndOpportunityNextContactTimeUpdateContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['ContactUpdateRequest']"
                        + "['properties']['nextContactTime']['type'][0]").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['ContactUpdateRequest']"
                        + "['properties']['nextContactTime']['type'][1]").value("null"))
                .andExpect(jsonPath("$['components']['schemas']['ContactUpdateRequest']"
                        + "['properties']['nextContactTime']['description']")
                        .value("Optional next contact time; send null to clear it"))
                .andExpect(jsonPath("$['components']['schemas']['LeadUpdateRequest']"
                        + "['properties']['nextContactTime']['type'][0]").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['LeadUpdateRequest']"
                        + "['properties']['nextContactTime']['type'][1]").value("null"))
                .andExpect(jsonPath("$['components']['schemas']['LeadUpdateRequest']"
                        + "['properties']['nextContactTime']['description']")
                        .value("Optional next contact time; send null to clear it"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityUpdateRequest']"
                        + "['properties']['nextContactTime']['type'][0]").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityUpdateRequest']"
                        + "['properties']['nextContactTime']['type'][1]").value("null"))
                // Contact and Lead carry an explicit "specified" flag, so for them an omitted
                // field and an explicit null are different requests and "send null to clear it"
                // is accurate. Opportunity has no such flag and replaces its basic fields
                // outright, so omitting the field clears the value just as sending null does.
                .andExpect(jsonPath("$['components']['schemas']['OpportunityUpdateRequest']"
                        + "['properties']['nextContactTime']['description']")
                        .value("Replaced outright; omitting it clears the stored value"));
    }

    @Test
    void publishesOpportunityEditableBaselineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}/editable-baseline']"
                        + "['get']['responses']['200']['content']"
                        + "['*/*']['schema']['$ref']")
                        .value("#/components/schemas/OpportunityEditableBaselineResponse"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityEditableBaselineResponse']"
                        + "['properties']['editable']['type']").value("boolean"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityEditableBaselineResponse']"
                        + "['properties']['estimatedAmount']['minimum']").value(0))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityEditableBaselineResponse']"
                        + "['properties']['nextContactTime']['type'][0]").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityEditableBaselineResponse']"
                        + "['properties']['nextContactTime']['type'][1]").value("null"));
    }

    @Test
    void publishesTypedOpportunityListAndDetailContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/query']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/OpportunityPageResponse"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityPageResponse']['required']")
                        .value(hasItems("pageNum", "pageSize", "totalNum", "objectList")))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value("#/components/schemas/OpportunityResponse"))
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/OpportunityResponse"));
    }

    @Test
    void publishesOpportunityCollaboratorBaselineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}/collaborators']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/OpportunityCollaboratorBaselineResponse"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityCollaboratorBaselineResponse']"
                        + "['required']").value(hasItems("version", "collaborators", "callerState")))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][0]").value("OWNER"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][3]").value("NONE"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityCollaboratorResponse']"
                        + "['required']").value(hasItems("actorId", "access")));
    }

    @Test
    void publishesContactCollaboratorBaselineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/contacts/{contactId}/collaborators']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/ContactCollaboratorBaselineResponse"))
                .andExpect(jsonPath("$['components']['schemas']['ContactCollaboratorBaselineResponse']"
                        + "['required']").value(hasItems("version", "collaborators", "callerState")))
                .andExpect(jsonPath("$['components']['schemas']['ContactCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][0]").value("OWNER"))
                .andExpect(jsonPath("$['components']['schemas']['ContactCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][3]").value("NONE"))
                .andExpect(jsonPath("$['components']['schemas']['ContactCollaboratorResponse']"
                        + "['required']").value(hasItems("actorId", "access")));
    }

    @Test
    void publishesCustomerCollaboratorBaselineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/customers/{customerId}/collaborators']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/CustomerCollaboratorBaselineResponse"))
                .andExpect(jsonPath("$['components']['schemas']['CustomerCollaboratorBaselineResponse']"
                        + "['required']").value(hasItems("version", "collaborators", "callerState")))
                .andExpect(jsonPath("$['components']['schemas']['CustomerCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][0]").value("OWNER"))
                .andExpect(jsonPath("$['components']['schemas']['CustomerCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][3]").value("NONE"))
                .andExpect(jsonPath("$['components']['schemas']['CustomerCollaboratorResponse']"
                        + "['required']").value(hasItems("actorId", "access")));
    }

    @Test
    void publishesTypedReceivableListSummaryContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/receivables/summary']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/ReceivableListSummaryResponse"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivableListSummaryResponse']"
                        + "['required']").value(hasItems("count", "totalAmount")));
    }

    @Test
    void publishesTypedReceivablePlanListSummaryContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/receivable-plans/summary']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/ReceivablePlanListSummaryResponse"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanListSummaryResponse']"
                        + "['required']").value(hasItems("count", "plannedAmount", "receivedAmount",
                                "remainingAmount")))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanQueryRequest']"
                        + "['properties']['subordinateOwnerOnly']['type']").value("boolean"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanQueryRequest']"
                        + "['properties']['savedViewId']['format']").value("int64"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanQueryRequest']"
                        + "['properties']['conditions']['maxItems']").value(50));
    }

    @Test
    void publishesBoundedReceivablePlanCsvExportContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/receivable-plans/export/csv']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value("#/components/schemas/ReceivablePlanCsvExportRequest"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanCsvExportRequest']"
                        + "['required']").value(hasItems("fieldCodes")))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanCsvExportRequest']"
                        + "['properties']['fieldCodes']['maxItems']").value(15))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanCsvExportRequest']"
                        + "['properties']['page']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanCsvExportRequest']"
                        + "['properties']['size']").doesNotExist());
    }

    static Stream<Arguments> sortQuerySchemas() {
        return Stream.of(
                arguments("CustomerQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR"),
                arguments("LeadQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR"),
                arguments("ContactQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR"),
                arguments("OpportunityQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR"),
                arguments("ProductQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR"),
                arguments("ContractQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|NAME|OWNER_ACTOR"),
                arguments("ReceivableQueryRequest",
                        "RECEIVED_DATE|CREATED_TIME|UPDATED_TIME|RECEIPT_NUMBER|OWNER_ACTOR"),
                arguments("InvoiceQueryRequest",
                        "UPDATED_TIME|CREATED_TIME|REQUESTED_DATE|ISSUED_DATE|"
                                + "APPLICATION_NUMBER|INVOICE_NUMBER|OWNER_ACTOR"),
                arguments("ReturnVisitQueryRequest",
                        "VISIT_TIME|UPDATED_TIME|CREATED_TIME|VISIT_NUMBER|OWNER_ACTOR"));
    }

    /**
     * One published contract per query DTO.  The schemas differ only in their sort-field set, so
     * each stays its own named case; the enum is derived from the same expected pattern literal
     * because the published schema must keep those two in agreement.
     */
    @ParameterizedTest(name = "{0} publishes its sort field, custom sort code and direction contract")
    @MethodSource("sortQuerySchemas")
    void publishesSortQueryContract(String schema, String sortFieldPattern) throws Exception {
        String properties = "$['components']['schemas']['" + schema + "']['properties']";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(properties + "['sortField']['pattern']").value(sortFieldPattern))
                .andExpect(jsonPath(properties + "['sortField']['enum']")
                        .value(hasItems(sortFieldPattern.split("\\|"))))
                .andExpect(jsonPath(properties + "['customSortFieldCode']['pattern']")
                        .value("[a-z][a-z0-9_]{1,63}"))
                .andExpect(jsonPath(properties + "['sortDirection']['pattern']").value("ASC|DESC"))
                .andExpect(jsonPath(properties + "['sortDirection']['enum']").value(hasItems("ASC", "DESC")));
    }

    @Test
    void publishesCredentialSafeConnectorControlPlaneContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/connectors/query']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ConnectorPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/connectors/{connectorId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ConnectorResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/connectors']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ConnectorCredentialCreatedResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/connectors/{connectorId}/credentials']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ConnectorCredentialCreatedResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/connectors/{connectorId}/credentials/{keyId}']"
                        + "['delete']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ConnectorResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/connectors/{connectorId}/status']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ConnectorResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ConnectorPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("ConnectorResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ConnectorResponse']['required']")
                        .value(hasItems("id", "connectorRef", "status", "credentials", "version",
                                "createdTime", "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['ConnectorResponse']"
                        + "['properties']['credential']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['ConnectorCredentialCreatedResponse']"
                        + "['required']").value(hasItems("connector", "credential")))
                .andExpect(jsonPath("$['components']['schemas']['ConnectorCredentialCreatedResponse']"
                        + "['properties']['connector']['$ref']")
                        .value(containsString("ConnectorResponse")));
    }

    @Test
    void publishesTypedEnterpriseDataContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/enterprise-data/capabilities']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("EnterpriseDataCapabilityResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/enterprise-data/search']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("EnterpriseDataObservationResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/enterprise-data/lookup']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("EnterpriseDataObservationResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/enterprise-data/adoption-preview']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("EnterpriseDataAdoptionPreviewResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/enterprise-data/adopt']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("EnterpriseDataAdoptionResponse")))
                .andExpect(jsonPath("$['components']['schemas']['EnterpriseDataObservationResponse']"
                        + "['properties']['rawResponse']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['EnterpriseDataAdoptionPreviewResponse']"
                        + "['properties']['observation']['$ref']")
                        .value(containsString("EnterpriseDataObservationResponse")));
    }

    @Test
    void publishesTypedSavedViewContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/saved-views']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("SavedViewResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/saved-views']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("SavedViewResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/saved-views/{viewId}']['put']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("SavedViewResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/saved-views/{viewId}/default']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("SavedViewResponse")))
                .andExpect(jsonPath("$['components']['schemas']['SavedViewResponse']['required']")
                        .value(hasItems("id", "objectType", "name", "conditions", "sortOrder", "hidden",
                                "defaultView", "version", "createdTime", "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['SavedViewResponse']"
                        + "['properties']['conditions']['items']['$ref']")
                        .value(containsString("SavedViewConditionResponse")))
                .andExpect(jsonPath("$['components']['schemas']['SavedViewConditionResponse']['required']")
                        .value(hasItems("fieldCode", "operator", "values", "exposed")))
                .andExpect(jsonPath("$['components']['schemas']['SavedViewCreateRequest']"
                        + "['properties']['conditions']['items']['$ref']")
                        .value(containsString("SavedViewConditionRequest")));
    }

    @Test
    void snapshotsReturnVisitRelationPatchAndNullableContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/return-visits/{returnVisitId}']['put']['description']")
                        .value(containsString("Omit contactId or contractId to retain")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['contactId']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['contactId']['type'][0]")
                        .value("integer"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['contactId']['type'][1]")
                        .value("null"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['contractId']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['contractId']['type'][0]")
                        .value("integer"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['contractId']['type'][1]")
                        .value("null"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contactRelationState']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contractRelationState']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['approvalStatus']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitCreateRequest']['properties']['visitForm']['enum']")
                        .value(hasItems("IN_PERSON", "PHONE", "SMS", "EMAIL", "WECHAT")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitCreateRequest']['properties']['satisfaction']['enum']")
                        .value(hasItems("VERY_SATISFIED", "SATISFIED", "NEUTRAL", "DISSATISFIED", "VERY_DISSATISFIED")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['visitForm']['type'][1]")
                        .value("null"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitUpdateRequest']['properties']['satisfaction']['type'][1]")
                        .value("null"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['visitForm']['enum']")
                        .value(hasItems("IN_PERSON", "PHONE", "SMS", "EMAIL", "WECHAT")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['satisfaction']['enum']")
                        .value(hasItems("VERY_SATISFIED", "SATISFIED", "NEUTRAL", "DISSATISFIED", "VERY_DISSATISFIED")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contactRelationState']['enum'][0]")
                        .value("VISIBLE"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contactRelationState']['enum'][1]")
                        .value("HIDDEN"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contactRelationState']['enum'][2]")
                        .value("EMPTY"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contractRelationState']['enum'][0]")
                        .value("VISIBLE"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contractRelationState']['enum'][1]")
                        .value("HIDDEN"))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['contractRelationState']['enum'][2]")
                        .value("EMPTY"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivableResponse']['properties']['approvalStatus']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ReceivableResponse']['properties']['approvalStatus']['enum']")
                        .value(hasItems("PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitResponse']['properties']['approvalStatus']['enum']")
                        .value(hasItems("PENDING", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED")))
                .andExpect(jsonPath("$['paths']['/api/v1/receivables/query']['post']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ReceivablePageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePageResponse']['properties']['objectList']['items']['$ref']")
                        .value(containsString("ReceivableResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/return-visits/query']['post']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ReturnVisitPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ReturnVisitPageResponse']['properties']['objectList']['items']['$ref']")
                        .value(containsString("ReturnVisitResponse")));
    }

    @Test
    void snapshotsCommercialPartialUpdatesAndContractCollaboratorBaseline() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}']['patch']['description']")
                        .value(containsString("Omitted fields retain")))
                .andExpect(jsonPath("$['paths']['/api/v1/invoice-profiles/{profileId}']['patch']['description']")
                        .value(containsString("Omitted fields retain")))
                .andExpect(jsonPath("$['paths']['/api/v1/invoices/{invoiceId}']['patch']['description']")
                        .value(containsString("Omitted fields retain")))
                .andExpect(jsonPath("$['paths']['/api/v1/receivables/{receivableId}']['patch']['description']")
                        .value(containsString("Omitted fields retain")))
                .andExpect(jsonPath("$['paths']['/api/v1/receivable-plans/{planId}']['patch']['description']")
                        .value(containsString("Omitted fields retain")))
                .andExpect(jsonPath("$['components']['schemas']['ContractUpdateRequest']['required'][0]")
                        .value("version"))
                .andExpect(jsonPath("$['components']['schemas']['InvoiceUpdateRequest']['required'][0]")
                        .value("version"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivableUpdateRequest']['required'][0]")
                        .value("version"))
                .andExpect(jsonPath("$['components']['schemas']['ReceivablePlanUpdateRequest']['required'][0]")
                        .value("version"))
                .andExpect(jsonPath("$['components']['schemas']['InvoiceProfileUpdateRequest']['required'][0]")
                        .value("version"))
                .andExpect(jsonPath("$['paths']['/api/v1/contracts/{contractId}/collaborators']['get']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ContractCollaboratorBaselineResponse']['properties']['version']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ContractCollaboratorBaselineResponse']['properties']['collaborators']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ContractCollaboratorBaselineResponse']['properties']['callerState']['enum'][0]")
                        .value("OWNER"))
                .andExpect(jsonPath("$['components']['schemas']['ContractCollaboratorBaselineResponse']['properties']['callerState']['enum'][3]")
                        .value("NONE"))
                .andExpect(jsonPath("$['components']['schemas']['ContractCollaboratorBaselineResponse']['required']")
                        .value(hasItems("version", "collaborators", "callerState")))
                .andExpect(jsonPath("$['components']['schemas']['ContractCollaboratorResponse']['required']")
                        .value(hasItems("actorId", "access")));
    }

    @Test
    void publishesOpportunityContactsBaselineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/opportunities/{opportunityId}/contacts/baseline']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/OpportunityContactsBaselineResponse"))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityContactsBaselineResponse']"
                        + "['required']").value(hasItems("id", "version", "editable")))
                .andExpect(jsonPath("$['components']['schemas']['OpportunityContactsBaselineResponse']"
                        + "['properties']['contacts']['items']['$ref']")
                        .value("#/components/schemas/OpportunityContactResponse"));
    }

    @Test
    void publishesLeadCollaboratorBaselineContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/leads/{leadId}/collaborators']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value("#/components/schemas/LeadCollaboratorBaselineResponse"))
                .andExpect(jsonPath("$['components']['schemas']['LeadCollaboratorBaselineResponse']"
                        + "['required']").value(hasItems("version", "collaborators", "callerState")))
                .andExpect(jsonPath("$['components']['schemas']['LeadCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][0]").value("OWNER"))
                .andExpect(jsonPath("$['components']['schemas']['LeadCollaboratorBaselineResponse']"
                        + "['properties']['callerState']['enum'][3]").value("NONE"))
                .andExpect(jsonPath("$['components']['schemas']['LeadCollaboratorResponse']"
                        + "['required']").value(hasItems("actorId", "access")));
    }

    @Test
    void publishesTypedP5ApprovalAndStageWorkflowContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/authorization/me']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("MemberAuthorizationResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approval-definitions/query']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalDefinitionPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalDefinitionPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("ApprovalDefinitionResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approval-definitions/{definitionId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalDefinitionResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approval-definitions/fields']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['items']['$ref']")
                        .value(containsString("ApprovalFieldResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approvals/{instanceId}/actions']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalActionResult")))
                .andExpect(jsonPath("$['paths']['/api/v1/approvals/actions/batch']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalBatchActionResult")))
                .andExpect(jsonPath("$['paths']['/api/v1/approvals/pending-count']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalPendingCountResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approvals/query']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalInstanceSummaryPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalInstanceSummaryPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("ApprovalInstanceSummaryResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approvals/{instanceId}/transfer-candidates']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalTransferCandidatePageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalTransferCandidatePageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("ApprovalTransferCandidateResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/approvals/subjects/{objectType}/{subjectId}"
                        + "/start-options/{definitionId}/selections/{selectionCode}/candidates']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalSelectionCandidatePageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalSelectionCandidatePageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("ApprovalSelectionCandidateResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflows/{workflowId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowDetailResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflows/query']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowSummaryPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['StageWorkflowSummaryPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("StageWorkflowSummaryResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflow-records/{workflowRecordId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowRecordResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflow-records/{workflowRecordId}/advance']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowActionResult")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflow-records/{workflowRecordId}"
                        + "/advance-options/{selectionCode}/candidates']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalSelectionCandidatePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflow-records/{workflowRecordId}"
                        + "/writable-fields/{fieldCode}/candidates']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageFieldCandidatePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflow-records/{workflowRecordId}"
                        + "/writable-fields/{fieldCode}/file-drafts']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageFileFieldDraftResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/file-field-drafts/{draftId}/fields/{fieldCode}"
                        + "/upload-intents']['post']['responses']['201']['content']['*/*']['schema']['$ref']")
                        .value(containsString("FileFieldUploadIntentResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/file-field-drafts']['post']['responses']['201']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("FileFieldDraftResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/forms/{formId}/file-field-drafts']['post']"
                        + "['responses']['201']['content']['*/*']['schema']['$ref']")
                        .value(containsString("FileFieldDraftResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/file-field-drafts/{draftId}/fields/{fieldCode}"
                        + "/files/{fileId}/content']['put']['responses']['202']['content']['*/*']['schema']['$ref']")
                        .value(containsString("FileFieldUploadedFileResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/file-field-drafts/{draftId}/fields/{fieldCode}"
                        + "/files/{fileId}/upload-status']['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("FileFieldUploadedFileResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflows/configuration-options']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowConfigurationOptionsResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflows/configuration-options/candidates']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowConfigurationCandidatePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflows/configuration-options/candidates']['get']"
                        + "['parameters'][0]['schema']['enum']")
                        .value(hasItems("MEMBER", "DEPARTMENT")))
                .andExpect(jsonPath("$['components']['schemas']['FileFieldUploadIntentResponse']['required']")
                        .value(hasItems("draftId", "fieldCode", "fileId", "uploadUrl", "uploadMethod",
                                "requiredHeaders", "expiresAt")))
                .andExpect(jsonPath("$['components']['schemas']['FileFieldUploadedFileResponse']['required']")
                        .value(hasItems("fileId", "displayName", "mediaType", "size", "status")))
                .andExpect(jsonPath("$['components']['schemas']['StageWorkflowRecordResponse']"
                        + "['properties']['writableFields']['items']['$ref']")
                        .value(containsString("StageWritableFieldResponse")))
                .andExpect(jsonPath("$['components']['schemas']['StageWorkflowRecordResponse']"
                        + "['required']").value(hasItems("version", "allowedOutcomes")))
                .andExpect(jsonPath("$['components']['schemas']['StageWritableFieldResponse']"
                        + "['properties']['input']['$ref']")
                        .value(containsString("StageFieldInputResponse")))
                .andExpect(jsonPath("$['components']['schemas']['StageWritableFieldResponse']"
                        + "['properties']['currentAddress']['$ref']")
                        .value(containsString("StageAddressValueResponse")))
                .andExpect(jsonPath("$['components']['schemas']['StageWorkflowDetailResponse']"
                        + "['properties']['allowedStatuses']['items']['enum']")
                        .value(hasItems("ACTIVE", "INACTIVE", "DELETED")))
                .andExpect(jsonPath("$['components']['schemas']['StageAdvanceRequest']"
                        + "['properties']['actorFieldValues']").exists())
                .andExpect(jsonPath("$['components']['schemas']['StageAdvanceRequest']"
                        + "['properties']['fileFieldDraftId']").exists())
                .andExpect(jsonPath("$['components']['schemas']['StageApprovalGateResponse']"
                        + "['properties']['approverSelections']['items']['$ref']")
                        .value(containsString("ApprovalStartSelectionResponse")))
                .andExpect(jsonPath("$['components']['schemas']['StageWorkflowApplicableResponse']"
                        + "['required']").value(hasItems("objectType", "recordId", "workflows")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflow-records/{workflowRecordId}/comments']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("StageWorkflowCommentPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['StageWorkflowCommentPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("StageWorkflowCommentResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/stage-workflows/records/{workflowRecordId}']['get']"
                        + "['deprecated']").value(true))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesListPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['WorkTaskSeriesListPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("WorkTaskSeriesListItemResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series/{seriesId}']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesDetailResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesMutationResultResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series/{seriesId}']['put']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesMutationResultResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series/{seriesId}/activate']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesMutationResultResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series/{seriesId}/pause']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesMutationResultResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/work/task-series/{seriesId}']['delete']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("WorkTaskSeriesMutationResultResponse")))
                .andExpect(jsonPath("$['components']['schemas']['WorkTaskSeriesDetailResponse']['required']")
                        .value(hasItems("id", "version", "title", "status", "recurrence", "allowedActions",
                                "actionBlockers")))
                .andExpect(jsonPath("$['components']['schemas']['WorkTaskSeriesMutationResultResponse']['required']")
                        .value(hasItems("id", "version", "status", "allowedActions")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalActionRequest']['properties']['action']['enum']")
                        .value(hasItems("APPROVE", "REJECT", "TRANSFER")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalActionRequest']['properties']"
                        + "['transferTargetConsistent']").doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['ApprovalPendingCountResponse']['required']")
                        .value(hasItems("count")))
                .andExpect(jsonPath("$['components']['schemas']['ApprovalSubmitRequest']['properties']['facts']")
                        .doesNotExist())
                .andExpect(jsonPath("$['components']['schemas']['ApprovalInstanceResponse']['properties']['allowedActions']")
                        .exists())
                .andExpect(jsonPath("$['components']['schemas']['ApprovalInstanceResponse']['properties']['facts']")
                        .doesNotExist());
    }

    @Test
    void publishesTypedPublicMarketingFileContracts() throws Exception {
        String base = "/api/v1/open/marketing-forms/{publicId}/file-drafts";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['" + base + "']['post']['responses']['201']"
                        + "['content']['application/json']['schema']['$ref']")
                        .value(containsString("PublicMarketingFileDraftResponse")))
                .andExpect(jsonPath("$['paths']['" + base + "/{draftId}/fields/{fieldCode}"
                        + "/upload-intents']['post']['responses']['201']['content']['application/json']"
                        + "['schema']['$ref']")
                        .value(containsString("PublicMarketingUploadIntentResponse")))
                .andExpect(jsonPath("$['paths']['" + base + "/{draftId}/fields/{fieldCode}/files/{fileId}"
                        + "/content']['put']['requestBody']['content']['application/octet-stream']"
                        + "['schema']['format']").value("binary"))
                .andExpect(jsonPath("$['paths']['" + base + "/{draftId}/fields/{fieldCode}/files/{fileId}"
                        + "/content']['put']['responses']['202']['content']['application/json']"
                        + "['schema']['$ref']")
                        .value(containsString("PublicMarketingFileUploadStatusResponse")))
                .andExpect(jsonPath("$['paths']['" + base + "/{draftId}/fields/{fieldCode}/files/{fileId}"
                        + "/upload-status']['get']['responses']['200']['content']['application/json']"
                        + "['schema']['$ref']")
                        .value(containsString("PublicMarketingFileUploadStatusResponse")))
                .andExpect(jsonPath("$['paths']['" + base + "/{draftId}']['delete']['responses']['200']"
                        + "['content']['application/json']['schema']['$ref']")
                        .value(containsString("PublicMarketingFileDraftWithdrawalResponse")))
                .andExpect(jsonPath("$['components']['schemas']['PublicMarketingFileDraftResponse']['required']")
                        .value(hasItems("draftId", "fieldCodes", "uploadCapability", "expiresAt")))
                .andExpect(jsonPath("$['components']['schemas']['PublicMarketingUploadIntentResponse']['required']")
                        .value(hasItems("draftId", "fieldCode", "fileId", "uploadUrl", "uploadMethod",
                                "requiredHeaders", "capabilityHeaderName", "version", "displayName",
                                "mediaType", "size", "expiresAt")))
                .andExpect(jsonPath("$['components']['schemas']['PublicMarketingFileUploadStatusResponse']"
                        + "['required']").value(hasItems("draftId", "fieldCode", "fileId", "displayName",
                                "mediaType", "size", "version", "status")))
                .andExpect(jsonPath("$['components']['schemas']['PublicMarketingFileUploadStatusResponse']"
                        + "['properties']['status']['enum']").value(hasItems("PENDING", "UPLOADED", "FAILED")));
    }

    @Test
    void publishesRenderablePublicMarketingFormContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['components']['schemas']['PublicMarketingFormResponse']['required']")
                        .value(hasItems("publicId", "title", "submitButtonText", "fields")))
                .andExpect(jsonPath("$['components']['schemas']['PublicMarketingFormResponse']"
                        + "['properties']['successMessage']['type']").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormFieldResponse']['required']")
                        .value(hasItems("fieldCode", "label", "required", "sortOrder", "fieldType", "options")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormFieldResponse']"
                        + "['properties']['fieldType']['enum']").value(hasItems("TEXT", "SELECT", "FILE")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormFieldOptionResponse']['required']")
                        .value(hasItems("value", "label", "sortOrder")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormSubmissionRequest']['required']")
                        .value(hasItems("idempotencyKey", "values")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormSubmissionResponse']['required']")
                        .value(hasItems("submissionId", "accepted", "idempotencyKey")));
    }

    @Test
    void publishesTypedMarketingActivityContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-activities/configuration-options']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingActivityConfigurationOptionsResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityConfigurationOptionsResponse']"
                        + "['required']").value(hasItems("activityTypes", "relatedObjectTypes",
                                "defaultRelatedObjectType")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityConfigurationOptionsResponse']"
                        + "['properties']['relatedObjectTypes']['items']['$ref']")
                        .value(containsString("MarketingActivityRelatedObjectTypeOptionResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityRelatedObjectTypeOptionResponse']"
                        + "['required']").value(hasItems("code", "displayName", "supportsMarketingForm")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-activities/batch-delete']['post']"
                        + "['requestBody']['content']['application/json']['schema']['$ref']")
                        .value(containsString("MarketingActivityBatchDeleteRequest")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-activities']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingActivityPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-activities']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingActivityResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-activities/{activityId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingActivityResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-activities/{activityId}']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingActivityResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("MarketingActivityResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityPageResponse']['required']")
                        .value(hasItems("pageNum", "pageSize", "totalNum", "objectList")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityBatchDeleteRequest']['required']")
                        .value(hasItems("activities")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityResponse']['required']")
                        .value(hasItems("id", "name", "relatedObjectType", "createdByActorId",
                                "participantActorIds", "startTime", "endTime", "oneSubmissionPerUser",
                                "viewCount", "submissionCount", "status", "version", "createdTime",
                                "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityCreateRequest']"
                        + "['properties']['viewCount']['minimum']").value(0))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityCreateRequest']"
                        + "['properties']['submissionCount']['minimum']").value(0))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityCreateRequest']"
                        + "['properties']['marketingFormId']['minimum']").value(1))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityResponse']"
                        + "['properties']['marketingFormPublicId']['type']").value("string"))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityResponse']"
                        + "['properties']['relatedObjectType']['enum']")
                        .value(hasItems("CUSTOMER", "CONTACT", "LEAD")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingActivityResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("ACTIVE", "DELETED")));
    }

    @Test
    void publishesTheTypedControlledAttachmentLifecycleContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/attachment-targets/{targetType}/{targetId}/attachments"
                        + "/upload-intents']['post']['responses']['201']['content']['application/json']"
                        + "['schema']['$ref']").value(containsString("AttachmentUploadIntentResponse")))
                .andExpect(jsonPath("$['components']['schemas']['AttachmentUploadIntentCreateRequest']"
                        + "['properties']['marketingActivityImageType']['enum']")
                        .value(hasItems("COVER", "DETAIL")))
                .andExpect(jsonPath("$['components']['schemas']['AttachmentUploadIntentCreateRequest']"
                        + "['properties']['productImageType']['enum']")
                        .value(hasItems("PRIMARY", "DETAIL")))
                .andExpect(jsonPath("$['paths']['/api/v1/product-image-drafts']['post']['responses']['201']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ProductImageDraftResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/product-image-drafts/{draftId}/images/{imageType}"
                        + "/upload-intents']['post']['responses']['201']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ProductImageUploadIntentResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/product-image-drafts/{draftId}/images/{imageType}"
                        + "/files/{fileId}/content']['put']['responses']['202']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ProductImageUploadStatusResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ProductCreateRequest']"
                        + "['properties']['productImageDraftId']['minimum']").value(1))
                .andExpect(jsonPath("$['components']['schemas']['ProductCreateRequest']"
                        + "['properties']['productImages']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/attachment-targets/{targetType}/{targetId}"
                        + "/file-fields/{fieldCode}/upload-intents']['post']['responses']['201']"
                        + "['content']['application/json']['schema']['$ref']")
                        .value(containsString("AttachmentUploadIntentResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/attachment-targets/{targetType}/{targetId}/attachments/{fileId}"
                        + "/content']['put']['requestBody']['content']['application/octet-stream']"
                        + "['schema']['format']").value("binary"))
                .andExpect(jsonPath("$['paths']['/api/v1/attachment-targets/{targetType}/{targetId}/attachments/{fileId}"
                        + "/upload-status']['get']['responses']['200']['content']['application/json']"
                        + "['schema']['$ref']").value(containsString("AttachmentUploadStatusResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/attachment-targets/{targetType}/{targetId}/attachments']"
                        + "['get']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("AttachmentPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/attachment-targets/{targetType}/{targetId}/attachments/{fileId}"
                        + "']['get']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("AttachmentResponse")));
    }

    @Test
    void publishesTheControlledCrmCsvImportDraftContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/crm-import-drafts']['post']['responses']['201']"
                        + "['content']['application/json']['schema']['$ref']")
                        .value(containsString("CrmImportDraftResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/crm-import-drafts/templates/{objectType}']"
                        + "['get']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("CrmImportTemplateResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/crm-import-drafts/history/{objectType}']"
                        + "['get']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("CrmImportHistoryPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/crm-import-drafts/{draftId}/preview']"
                        + "['get']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("CrmImportPreviewResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/crm-import-drafts/{draftId}/confirm']"
                        + "['post']['responses']['200']['content']['application/json']['schema']['$ref']")
                        .value(containsString("CrmImportConfirmResponse")))
                .andExpect(jsonPath("$['components']['schemas']['CrmImportDraftCreateRequest']['required']")
                        .value(hasItems("objectType")))
                .andExpect(jsonPath("$['components']['schemas']['CrmImportDraftConfirmRequest']['required']")
                        .value(hasItems("version")));
    }

    @Test
    void publishesTypedP6DashboardReportAndOaDetailContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/overview']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("DashboardOverviewResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/preferences']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("DashboardPreferenceResponse")))
                .andExpect(jsonPath("$['components']['schemas']['DashboardPreferenceResponse']['required']")
                        .value(hasItems("timeRange", "dataScope", "hiddenModules")))
                .andExpect(jsonPath("$['components']['schemas']['DashboardPreferenceResponse']['properties']"
                        + "['hiddenModules']['items']['enum']")
                        .value(hasItems("CUSTOMER_CONTACT_REMINDER")))
                .andExpect(jsonPath("$['components']['schemas']['DashboardOverviewResponse']['required']")
                        .value(hasItems("asOfDate", "timeRange", "from", "to", "preferredDataScope",
                                "granularity", "cards", "salesBrief", "dataSummary", "salesFunnel",
                                "customerContactReminder", "rankings", "target")))
                .andExpect(jsonPath("$['components']['schemas']['DashboardOverviewResponse']['properties']"
                        + "['timeRange']['enum']")
                        .value(hasItems("CURRENT_MONTH", "CUSTOM")))
                .andExpect(jsonPath("$['components']['schemas']['CardResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("AVAILABLE", "UNAVAILABLE")))
                .andExpect(jsonPath("$['components']['schemas']['SalesBriefResponse']"
                        + "['properties']['unit']['enum']")
                        .value(hasItems("COUNT", "AMOUNT")))
                .andExpect(jsonPath("$['components']['schemas']['SalesBriefResponse']"
                        + "['properties']['metric']['enum']")
                        .value(hasItems("NEW_FOLLOW_UP")))
                .andExpect(jsonPath("$['components']['schemas']['DataSummaryResponse']"
                        + "['properties']['metric']['enum']")
                        .value(hasItems("CUSTOMER_CLAIMED_FROM_POOL", "EXPECTED_RECEIVABLE")))
                .andExpect(jsonPath("$['components']['schemas']['DataSummaryResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("AVAILABLE", "UNAVAILABLE")))
                .andExpect(jsonPath("$['components']['schemas']['SalesFunnelResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("AVAILABLE", "UNAVAILABLE", "EMPTY")))
                .andExpect(jsonPath("$['components']['schemas']['SalesFunnelStageResponse']"
                        + "['required']")
                        .value(hasItems("code", "name", "probability", "count", "amount")))
                .andExpect(jsonPath("$['components']['schemas']['CustomerContactReminderResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("AVAILABLE", "UNAVAILABLE")))
                .andExpect(jsonPath("$['components']['schemas']['CustomerContactReminderBucketResponse']"
                        + "['properties']['bucket']['enum']")
                        .value(hasItems("OVER_7_DAYS", "OVERDUE")))
                .andExpect(jsonPath("$['components']['schemas']['RankingResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("AVAILABLE", "UNAVAILABLE")))
                .andExpect(jsonPath("$['components']['schemas']['TargetResponse']"
                        + "['properties']['status']['enum']")
                        .value(hasItems("AVAILABLE", "UNAVAILABLE", "EMPTY")))
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/performance-targets']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("PerformanceTargetListResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/department-performance-targets']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("DepartmentPerformanceTargetListResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/department-performance-targets/batch']['put']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("DepartmentPerformanceTargetListResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/performance-targets/import']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("PerformanceTargetImportResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/dashboard/department-performance-targets/import']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("PerformanceTargetImportResponse")))
                .andExpect(jsonPath("$['components']['schemas']['DepartmentPerformanceTargetResponse']"
                        + "['properties']['period']['enum']")
                        .value(hasItems("YEAR", "QUARTER", "MONTH")))
                .andExpect(jsonPath("$['paths']['/api/v1/reports/definitions/query']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ReportDefinitionPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ReportDefinitionPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("ReportDefinitionSummaryResponse")))
                .andExpect(jsonPath("$['components']['schemas']['ReportCatalogResponse']['required']")
                        .value(hasItems("objectType", "supportedObjectTypes", "timeFields", "dimensionFields",
                                "sumFields", "filterFields", "measureTypes")))
                .andExpect(jsonPath("$['paths']['/api/v1/reports/catalog']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ReportCatalogResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/reports/catalog']['get']['parameters'][0]['required']")
                        .value(false))
                .andExpect(jsonPath("$['paths']['/api/v1/reports/trends']['post']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("ReportTrendResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/forms/{formId}']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaFormResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/forms']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaFormPageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/forms/configuration-options']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaFormConfigurationOptionsResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/forms/available/{formId}/fields/{fieldCode}/candidates']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaFormFieldCandidatePageResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/forms/available/{formId}/approval-selections/{selectionCode}/candidates']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("ApprovalSelectionCandidatePageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaFormFieldCandidateResponse']['required']")
                        .value(hasItems("id", "displayName", "valueType")))
                .andExpect(jsonPath("$['components']['schemas']['OaFormFieldCandidateResponse']"
                        + "['properties']['valueType']['enum']")
                        .value(hasItems("ACTOR_ID", "DEPARTMENT_ID")))
                .andExpect(jsonPath("$['components']['schemas']['OaFormConfigurationOptionsResponse']"
                        + "['required']")
                        .value(hasItems("approvalDefinitions", "fields")))
                .andExpect(jsonPath("$['components']['schemas']['OaFormPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("OaFormResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaFormResponse']['required']")
                        .value(hasItems("id", "code", "name", "approvalDefinitionId", "status",
                                "approvalSelections", "fields", "version", "createdTime",
                                "updatedTime")))
                .andExpect(jsonPath("$['components']['schemas']['OaFormResponse']['properties']['status']['enum']")
                        .value(hasItems("INACTIVE", "ACTIVE", "DELETED")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/requests/{requestId}']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaRequestResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/requests']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaRequestPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaRequestPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("OaRequestResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaRequestResponse']['required']")
                        .value(hasItems("id", "formId", "formVersion", "approvalInstanceId", "approvalStatus",
                                "values", "fileFields", "version")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/announcements/{announcementId}']"
                        + "['get']['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaAnnouncementResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/announcements']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaAnnouncementPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaAnnouncementPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("OaAnnouncementSummaryResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/oa/announcements/mine']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("OaAnnouncementInboxPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaAnnouncementInboxPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("OaAnnouncementInboxResponse")))
                .andExpect(jsonPath("$['components']['schemas']['OaAnnouncementInboxResponse']['required']")
                        .value(hasItems("id", "title", "body", "read", "publishedTime")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-forms/{formId}']['get']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingFormResponse")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-forms']['get']['responses']['200']"
                        + "['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingFormPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("MarketingFormResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormResponse']['required']")
                        .value(hasItems("id", "publicId", "name", "title", "submitButtonText", "ownerActorId",
                                "status", "fields", "version", "createdTime", "updatedTime")))
                .andExpect(jsonPath("$['paths']['/api/v1/marketing-forms/{formId}/submissions/query']['post']"
                        + "['responses']['200']['content']['*/*']['schema']['$ref']")
                        .value(containsString("MarketingFormSubmissionLinkPageResponse")))
                .andExpect(jsonPath("$['components']['schemas']['MarketingFormSubmissionLinkPageResponse']"
                        + "['properties']['objectList']['items']['$ref']")
                        .value(containsString("MarketingFormSubmissionLinkResponse")));
    }
}
