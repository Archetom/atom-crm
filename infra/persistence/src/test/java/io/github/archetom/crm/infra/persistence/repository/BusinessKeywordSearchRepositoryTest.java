package io.github.archetom.crm.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.archetom.crm.domain.model.ContactAccessMode;
import io.github.archetom.crm.domain.model.ContactQuery;
import io.github.archetom.crm.domain.model.ContactVisibility;
import io.github.archetom.crm.domain.model.CustomerAccessMode;
import io.github.archetom.crm.domain.model.CustomerQuery;
import io.github.archetom.crm.domain.model.CustomerVisibility;
import io.github.archetom.crm.domain.model.DataScope;
import io.github.archetom.crm.domain.model.LeadAccessMode;
import io.github.archetom.crm.domain.model.LeadQuery;
import io.github.archetom.crm.domain.model.LeadVisibility;
import io.github.archetom.crm.domain.model.OpportunityAccessMode;
import io.github.archetom.crm.domain.model.OpportunityQuery;
import io.github.archetom.crm.domain.model.OpportunityVisibility;
import io.github.archetom.crm.domain.model.ProductQuery;
import io.github.archetom.crm.domain.model.ReturnVisitAccessMode;
import io.github.archetom.crm.domain.model.ReturnVisitQuery;
import io.github.archetom.crm.domain.model.ReturnVisitSort;
import io.github.archetom.crm.domain.model.ReturnVisitVisibility;
import io.github.archetom.crm.domain.model.SearchMatchMode;
import io.github.archetom.crm.domain.policy.ContactFieldCatalog;
import io.github.archetom.crm.domain.policy.CustomerFieldCatalog;
import io.github.archetom.crm.domain.policy.LeadFieldCatalog;
import io.github.archetom.crm.domain.policy.OpportunityFieldCatalog;
import io.github.archetom.crm.domain.policy.ProductFieldCatalog;
import io.github.archetom.crm.domain.policy.ReturnVisitFieldCatalog;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ContactMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.CustomerMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.LeadMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.OpportunityMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ProductMapper;
import io.github.archetom.crm.infra.persistence.mysql.mapper.ReturnVisitMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.ContactPO;
import io.github.archetom.crm.infra.persistence.mysql.po.CustomerPO;
import io.github.archetom.crm.infra.persistence.mysql.po.LeadPO;
import io.github.archetom.crm.infra.persistence.mysql.po.OpportunityPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ProductPO;
import io.github.archetom.crm.infra.persistence.mysql.po.ReturnVisitPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessKeywordSearchRepositoryTest {

    private static final TenantId TENANT = new TenantId(9L);
    private static final MemberId MEMBER = new MemberId(7L);

    @Test
    void customerSearchUsesOnlySelectedFieldsAndEscapesLikeMetacharacters() {
        CustomerMapper mapper = mock(CustomerMapper.class);
        AtomicReference<QueryWrapper<CustomerPO>> captured = captureCustomerPage(mapper);
        CustomerRepositoryImpl repository = new CustomerRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null, null);

        repository.findCustomers(TENANT, new CustomerQuery(customerVisibility(), "%_", Map.of(
                code(CustomerFieldCatalog.PHONE_NUMBER), SearchMatchMode.LITERAL_PREFIX),
                null, null, null, null, null, List.of(), 1, 20));

        String sql = captured.get().getCustomSqlSegment();
        assertTrue(sql.contains("phone_number LIKE"));
        assertFalse(sql.contains("name LIKE"));
        assertTrue(sql.contains("ESCAPE '!'"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("!%!_"));
    }

    @Test
    void subordinateOwnerSetIsAppliedInsideTheCustomerVisibilityQuery() {
        CustomerMapper mapper = mock(CustomerMapper.class);
        AtomicReference<QueryWrapper<CustomerPO>> captured = captureCustomerPage(mapper);
        CustomerRepositoryImpl repository = new CustomerRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null, null);

        repository.findCustomers(TENANT, new CustomerQuery(customerVisibility(), null, Map.of(),
                null, null, Set.of(new MemberId(12L)), null, null, List.of(), 1, 20));

        assertTrue(captured.get().getCustomSqlSegment().contains("owner_actor_id IN"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue(12L));
    }

    @Test
    void anEmptySubordinateOwnerSetNeverWidensTheCustomerQuery() {
        CustomerMapper mapper = mock(CustomerMapper.class);
        AtomicReference<QueryWrapper<CustomerPO>> captured = captureCustomerPage(mapper);
        CustomerRepositoryImpl repository = new CustomerRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null, null);

        repository.findCustomers(TENANT, new CustomerQuery(customerVisibility(), null, Map.of(),
                null, null, Set.of(), null, null, List.of(), 1, 20));

        assertTrue(captured.get().getCustomSqlSegment().contains("1 = 0"));
    }

    @Test
    void contactAndLeadSearchKeepTheirTenantVisibilityAndFixedCandidateColumns() {
        ContactMapper contactMapper = mock(ContactMapper.class);
        AtomicReference<QueryWrapper<ContactPO>> contact = captureContactPage(contactMapper);
        new ContactRepositoryImpl(contactMapper, null, null, null, null, null, null,
                null, null, null).findContacts(TENANT, new ContactQuery(contactVisibility(),
                "mail", Map.of(code(ContactFieldCatalog.EMAIL), SearchMatchMode.LITERAL_PREFIX),
                null, null, null, null, null, null, List.of(), 1, 20));

        LeadMapper leadMapper = mock(LeadMapper.class);
        AtomicReference<QueryWrapper<LeadPO>> lead = captureLeadPage(leadMapper);
        new LeadRepositoryImpl(leadMapper, null, null, null, null, null, null,
                null, null, null).findLeads(TENANT, new LeadQuery(leadVisibility(),
                "desk", Map.of(code(LeadFieldCatalog.TELEPHONE), SearchMatchMode.LITERAL_PREFIX),
                null, null, null, null, null, null, List.of(), 1, 20));

        assertTrue(contact.get().getCustomSqlSegment().contains("email LIKE"));
        assertTrue(contact.get().getCustomSqlSegment().contains("tenant_id"));
        assertTrue(contact.get().getCustomSqlSegment().contains("status"));
        assertTrue(lead.get().getCustomSqlSegment().contains("telephone LIKE"));
        assertTrue(lead.get().getCustomSqlSegment().contains("tenant_id"));
        assertTrue(lead.get().getCustomSqlSegment().contains("status"));
    }

    @Test
    void opportunityAndReturnVisitUseTheirSingleCatalogCandidates() {
        OpportunityMapper opportunityMapper = mock(OpportunityMapper.class);
        AtomicReference<QueryWrapper<OpportunityPO>> opportunity =
                captureOpportunityPage(opportunityMapper);
        new OpportunityRepositoryImpl(opportunityMapper, null, null, null, null, null,
                null, null, null, null, null).findOpportunities(TENANT,
                new OpportunityQuery(opportunityVisibility(), "renew", Map.of(
                        code(OpportunityFieldCatalog.NAME), SearchMatchMode.LITERAL_PREFIX),
                        null, null, null, null, null, null, List.of(), 1, 20));

        ReturnVisitMapper returnVisitMapper = mock(ReturnVisitMapper.class);
        AtomicReference<QueryWrapper<ReturnVisitPO>> visit = captureReturnVisitPage(returnVisitMapper);
        new ReturnVisitRepositoryImpl(returnVisitMapper, null, null, null, null, null, null)
                .findReturnVisits(TENANT, new ReturnVisitQuery(1, 20, "RV-", Map.of(
                        code(ReturnVisitFieldCatalog.NUMBER), SearchMatchMode.LITERAL_PREFIX),
                        null, null, null, null, ReturnVisitSort.from(null, null), returnVisitVisibility()));

        assertTrue(opportunity.get().getCustomSqlSegment().contains("name LIKE"));
        assertTrue(visit.get().getCustomSqlSegment().contains("visit_number LIKE"));
    }

    @Test
    void productCodeSearchUsesTheNormalizedIdentifierKey() {
        ProductMapper mapper = mock(ProductMapper.class);
        AtomicReference<QueryWrapper<ProductPO>> captured = captureProductPage(mapper);
        ProductRepositoryImpl repository = new ProductRepositoryImpl(
                mapper, null, null, null, null, null, null);

        repository.findProducts(TENANT, new ProductQuery(" ＡＣＭＥ％＿ ", Map.of(
                code(ProductFieldCatalog.CODE), SearchMatchMode.NORMALIZED_PREFIX),
                null, null, null, null, null, null, List.of(), 1, 20));

        assertTrue(captured.get().getCustomSqlSegment().contains("code_key LIKE"));
        assertFalse(captured.get().getCustomSqlSegment().contains("name LIKE"));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("acme!%!_"));
    }

    @Test
    void aKeywordWithNoPermittedCandidatesAddsAFalsePredicate() {
        LeadMapper mapper = mock(LeadMapper.class);
        AtomicReference<QueryWrapper<LeadPO>> captured = captureLeadPage(mapper);
        LeadRepositoryImpl repository = new LeadRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null);

        repository.findLeads(TENANT, new LeadQuery(leadVisibility(), "hidden", Map.of(),
                null, null, null, null, null, null, List.of(), 1, 20));

        assertTrue(captured.get().getCustomSqlSegment().contains("1 = 0"));
    }

    @Test
    void leadConversionFilterIsAppliedInsideTheTenantAndVisibilityQuery() {
        LeadMapper mapper = mock(LeadMapper.class);
        AtomicReference<QueryWrapper<LeadPO>> captured = captureLeadPage(mapper);
        LeadRepositoryImpl repository = new LeadRepositoryImpl(
                mapper, null, null, null, null, null, null, null, null, null);

        repository.findLeads(TENANT, new LeadQuery(leadVisibility(), null, Map.of(),
                null, null, null, null, true, null, List.of(), 1, 20));

        String segment = captured.get().getCustomSqlSegment();
        assertTrue(segment.contains("tenant_id"));
        assertTrue(segment.contains("status ="));
        assertTrue(captured.get().getParamNameValuePairs().containsValue("CONVERTED"));
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<CustomerPO>> captureCustomerPage(CustomerMapper mapper) {
        AtomicReference<QueryWrapper<CustomerPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<CustomerPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<ContactPO>> captureContactPage(ContactMapper mapper) {
        AtomicReference<QueryWrapper<ContactPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<ContactPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<LeadPO>> captureLeadPage(LeadMapper mapper) {
        AtomicReference<QueryWrapper<LeadPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<LeadPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<OpportunityPO>> captureOpportunityPage(
            OpportunityMapper mapper) {
        AtomicReference<QueryWrapper<OpportunityPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<OpportunityPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<ProductPO>> captureProductPage(ProductMapper mapper) {
        AtomicReference<QueryWrapper<ProductPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<ProductPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<QueryWrapper<ReturnVisitPO>> captureReturnVisitPage(
            ReturnVisitMapper mapper) {
        AtomicReference<QueryWrapper<ReturnVisitPO>> captured = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            captured.set((QueryWrapper<ReturnVisitPO>) invocation.getArgument(1));
            return invocation.getArgument(0);
        });
        return captured;
    }

    private CustomerVisibility customerVisibility() {
        return new CustomerVisibility(DataScope.TENANT, MEMBER, Set.of(), CustomerAccessMode.READ);
    }

    private ContactVisibility contactVisibility() {
        return new ContactVisibility(DataScope.TENANT, MEMBER, Set.of(), ContactAccessMode.READ);
    }

    private LeadVisibility leadVisibility() {
        return new LeadVisibility(DataScope.TENANT, MEMBER, Set.of(), LeadAccessMode.READ);
    }

    private OpportunityVisibility opportunityVisibility() {
        return new OpportunityVisibility(
                DataScope.TENANT, MEMBER, Set.of(), OpportunityAccessMode.READ);
    }

    private ReturnVisitVisibility returnVisitVisibility() {
        return new ReturnVisitVisibility(
                DataScope.TENANT, MEMBER, Set.of(), ReturnVisitAccessMode.READ);
    }

    private FieldCode code(String value) {
        return new FieldCode(value);
    }
}
