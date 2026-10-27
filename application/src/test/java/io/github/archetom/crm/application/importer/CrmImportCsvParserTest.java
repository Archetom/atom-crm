package io.github.archetom.crm.application.importer;

import io.github.archetom.crm.domain.model.CrmImportObjectType;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrmImportCsvParserTest {
    @Test
    void parsesStrictLeadTemplateWithoutLeakingSourceValues() {
        String source = "\uFEFFname,phoneNumber,telephone,jobTitle,email,website,description,customerId,tags\r\n"
                + "Lead one,+8613812345678,010-1234,Manager,lead@example.com,https://example.com,\"a,b\",12,a;b\r\n";
        CrmImportCsvParser.Parsed result = parse(CrmImportObjectType.LEAD, source, validRows());

        assertTrue(result.valid());
        assertEquals(1, result.validRows());
        assertEquals("Lead one", result.leads().getFirst().getName());
        assertEquals(12L, result.leads().getFirst().getCustomerId());
        assertEquals(2, result.leads().getFirst().getTags().size());
    }

    @Test
    void returnsOnlyReviewedCodesForInvalidRows() {
        String source = "name,phoneNumber,telephone,jobTitle,email,website,description,customerId,tags\n"
                + "Bad,+8613812345678,,,,bad-email,,,\n";
        Validator validator = mock(Validator.class);
        when(validator.validate(any())).thenReturn(java.util.Set.of(mock(jakarta.validation.ConstraintViolation.class)));
        CrmImportCsvParser.Parsed result = parse(CrmImportObjectType.CONTACT, source, validator);

        assertFalse(result.valid());
        assertEquals(0, result.validRows());
        assertEquals(2, result.issues().getFirst().rowNumber());
        assertEquals("CSV_ROW_VALIDATION_FAILED", result.issues().getFirst().errCode());
    }

    @Test
    void rejectsAHeaderThatWouldChangeTheServerDefinedTemplate() {
        String source = "name,email\nA,a@example.com\n";
        CrmImportCsvParser.Parsed result = parse(CrmImportObjectType.LEAD, source, validRows());

        assertFalse(result.valid());
        assertEquals("CSV_HEADER_INVALID", result.issues().getFirst().errCode());
    }

    @Test
    void rejectsMalformedQuotedRows() {
        String source = "name,phoneNumber,telephone,jobTitle,email,website,description,customerId,tags\n"
                + "\"unclosed,+8613812345678,,,,,,,\n";
        CrmImportCsvParser.Parsed result = parse(CrmImportObjectType.CONTACT, source, validRows());

        assertFalse(result.valid());
        assertEquals("CSV_FORMAT_INVALID", result.issues().getFirst().errCode());
    }

    @Test
    void parsesTheProductTemplateAndRejectsUnknownAvailability() {
        String valid = "name,code,categoryId,unit,listPrice,description,availability\n"
                + "Product one,SKU-001,,piece,12.50,Imported product,AVAILABLE\n";
        CrmImportCsvParser.Parsed parsed = parse(CrmImportObjectType.PRODUCT, valid, validRows());

        assertTrue(parsed.valid());
        assertEquals(1, parsed.products().size());
        assertEquals("SKU-001", parsed.products().getFirst().getCode());
        assertEquals("12.50", parsed.products().getFirst().getListPrice().toPlainString());

        String lowerCaseAvailability = "name,code,categoryId,unit,listPrice,description,availability\n"
                + "Product two,SKU-002,,piece,12.50,Imported product,available\n";
        CrmImportCsvParser.Parsed normalized = parse(CrmImportObjectType.PRODUCT, lowerCaseAvailability, validRows());
        assertTrue(normalized.valid());
        assertEquals("AVAILABLE", normalized.products().getFirst().getAvailability());

        String invalid = "name,code,categoryId,unit,listPrice,description,availability\n"
                + "Product one,SKU-001,,piece,12.50,Imported product,NOT_A_STATE\n";
        CrmImportCsvParser.Parsed rejected = parse(CrmImportObjectType.PRODUCT, invalid, validRows());
        assertFalse(rejected.valid());
        assertEquals("CSV_ROW_VALIDATION_FAILED", rejected.issues().getFirst().errCode());
    }

    private static CrmImportCsvParser.Parsed parse(CrmImportObjectType type, String source, Validator validator) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        return new CrmImportCsvParser(validator).parse(type, new ByteArrayInputStream(bytes), bytes.length);
    }

    private static Validator validRows() {
        Validator validator = mock(Validator.class);
        when(validator.validate(any())).thenReturn(java.util.Set.of());
        return validator;
    }
}
