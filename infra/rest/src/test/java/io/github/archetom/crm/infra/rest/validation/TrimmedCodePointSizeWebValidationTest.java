package io.github.archetom.crm.infra.rest.validation;

import io.github.archetom.crm.api.dto.request.ContractQueryRequest;
import io.github.archetom.crm.api.dto.request.ContactQueryRequest;
import io.github.archetom.crm.api.dto.request.CustomerQueryRequest;
import io.github.archetom.crm.api.dto.request.InvoiceQueryRequest;
import io.github.archetom.crm.api.dto.request.LeadQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityQueryRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageCreateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineStageUpdateRequest;
import io.github.archetom.crm.api.dto.request.OpportunityPipelineUpdateRequest;
import io.github.archetom.crm.api.dto.request.ProductQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivablePlanQueryRequest;
import io.github.archetom.crm.api.dto.request.ReceivableQueryRequest;
import io.github.archetom.crm.api.dto.request.ReturnVisitQueryRequest;
import io.github.archetom.crm.domain.valueobject.SearchKeyword;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrimmedCodePointSizeWebValidationTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchValidationController())
                .setValidator(validator)
                .build();
    }

    @Test
    void acceptsTheSameTrimmedCodePointBoundaryAsSearchKeyword() throws Exception {
        String boundaryKeyword = " " + "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS) + " ";

        new SearchKeyword(boundaryKeyword);
        assertNoViolations(boundaryKeyword);
        mockMvc.perform(post("/test/search-keyword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\" " + "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS)
                                + " \"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsTheSameOverLimitKeywordAsSearchKeyword() throws Exception {
        String overLimit = "\uD83D\uDE80".repeat(SearchKeyword.MAX_CODE_POINTS + 1);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SearchKeyword(overLimit));
        assertViolations(overLimit);
        mockMvc.perform(post("/test/search-keyword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"" + overLimit + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pipelineTransportAcceptsTrimmedCodeAndNameBoundaries() throws Exception {
        String code = " " + "A".repeat(64) + " ";
        String name = " " + "N".repeat(200) + " ";

        assertNoPipelineViolations(code, name);
        mockMvc.perform(post("/test/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name
                                + "\",\"displayOrder\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void pipelineTransportCountsSupplementaryNameCodePoints() throws Exception {
        String atLimit = " " + "\uD83D\uDE80".repeat(200) + " ";
        String aboveLimit = "\uD83D\uDE80".repeat(201);

        assertNoPipelineViolations("direct", atLimit);
        assertPipelineViolations("direct", aboveLimit);
        mockMvc.perform(post("/test/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"direct\",\"name\":\"" + atLimit
                                + "\",\"displayOrder\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/test/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"direct\",\"name\":\"" + aboveLimit
                                + "\",\"displayOrder\":0}"))
                .andExpect(status().isBadRequest());
    }

    private void assertNoPipelineViolations(String code, String name) {
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new OpportunityPipelineCreateRequest()
                        .setCode(code).setName(name).setDisplayOrder(0)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new OpportunityPipelineStageCreateRequest().setCode(code).setName(name)
                        .setDisplayOrder(0).setProbability(0)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new OpportunityPipelineUpdateRequest().setVersion(0L).setName(name)
                        .setDisplayOrder(0)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new OpportunityPipelineStageUpdateRequest().setVersion(0L).setName(name)
                        .setDisplayOrder(0).setProbability(0)).isEmpty());
    }

    private void assertPipelineViolations(String code, String name) {
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new OpportunityPipelineCreateRequest()
                        .setCode(code).setName(name).setDisplayOrder(0)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new OpportunityPipelineStageCreateRequest().setCode(code).setName(name)
                        .setDisplayOrder(0).setProbability(0)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new OpportunityPipelineUpdateRequest().setVersion(0L).setName(name)
                        .setDisplayOrder(0)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new OpportunityPipelineStageUpdateRequest().setVersion(0L).setName(name)
                        .setDisplayOrder(0).setProbability(0)).isEmpty());
    }

    private void assertNoViolations(String keyword) {
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new ContractQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new InvoiceQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new ReceivableQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new ReceivablePlanQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new CustomerQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new ContactQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new LeadQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new OpportunityQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new ProductQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(
                new ReturnVisitQueryRequest().setKeyword(keyword)).isEmpty());
    }

    private void assertViolations(String keyword) {
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new ContractQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new InvoiceQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new ReceivableQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new ReceivablePlanQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new CustomerQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new ContactQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new LeadQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new OpportunityQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new ProductQueryRequest().setKeyword(keyword)).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(
                new ReturnVisitQueryRequest().setKeyword(keyword)).isEmpty());
    }

    @RestController
    static class SearchValidationController {

        @PostMapping("/test/search-keyword")
        void search(@Valid @RequestBody ContractQueryRequest request) {
        }

        @PostMapping("/test/pipeline")
        void pipeline(@Valid @RequestBody OpportunityPipelineCreateRequest request) {
        }
    }
}
