package io.github.archetom.crm.application.importer;

import io.github.archetom.crm.api.dto.request.ContactCreateRequest;
import io.github.archetom.crm.api.dto.request.LeadCreateRequest;
import io.github.archetom.crm.api.dto.request.ProductCreateRequest;
import io.github.archetom.crm.domain.model.CrmImportObjectType;
import io.github.archetom.crm.domain.model.ProductAvailability;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strict, bounded UTF-8 CSV parser. Source values remain request-local and are never returned. */
@Component
public class CrmImportCsvParser {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 200;
    private static final List<String> LEAD_COLUMNS = List.of(
            "name", "phoneNumber", "telephone", "jobTitle", "email", "website", "description", "customerId", "tags");
    private static final List<String> CONTACT_COLUMNS = List.of(
            "name", "phoneNumber", "telephone", "jobTitle", "email", "website", "description", "customerId", "tags");
    private static final List<String> PRODUCT_COLUMNS = List.of(
            "name", "code", "categoryId", "unit", "listPrice", "description", "availability");

    private final Validator validator;

    public CrmImportCsvParser(Validator validator) {
        this.validator = validator;
    }

    public List<String> template(CrmImportObjectType objectType) {
        return switch (objectType) {
            case LEAD -> LEAD_COLUMNS;
            case CONTACT -> CONTACT_COLUMNS;
            case PRODUCT -> PRODUCT_COLUMNS;
        };
    }

    public Parsed parse(CrmImportObjectType objectType, InputStream input, long declaredBytes) {
        if (input == null || declaredBytes < 1 || declaredBytes > MAX_BYTES) {
            return Parsed.invalid(0, "SOURCE_SIZE_INVALID");
        }
        try (input) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length != declaredBytes || bytes.length > MAX_BYTES) {
                return Parsed.invalid(0, "SOURCE_SIZE_INVALID");
            }
            String source = strictUtf8(bytes);
            if (source == null || source.indexOf('\u0000') >= 0) return Parsed.invalid(0, "SOURCE_ENCODING_INVALID");
            List<List<String>> rows = records(source);
            if (rows.isEmpty() || !headers(rows.getFirst(), template(objectType))) {
                return Parsed.invalid(1, "CSV_HEADER_INVALID");
            }
            if (rows.size() == 1) return Parsed.invalid(1, "CSV_ROWS_REQUIRED");
            if (rows.size() - 1 > MAX_ROWS) return Parsed.invalid(0, "CSV_ROW_LIMIT_EXCEEDED");
            List<LeadCreateRequest> leads = new ArrayList<>();
            List<ContactCreateRequest> contacts = new ArrayList<>();
            List<ProductCreateRequest> products = new ArrayList<>();
            List<Issue> issues = new ArrayList<>();
            for (int index = 1; index < rows.size(); index++) {
                List<String> row = rows.get(index);
                int rowNumber = index + 1;
                if (row.size() != template(objectType).size()) {
                    issues.add(new Issue(rowNumber, "CSV_COLUMN_COUNT_INVALID"));
                    continue;
                }
                switch (objectType) {
                    case LEAD -> {
                        LeadCreateRequest request = lead(row);
                        if (validator.validate(request).isEmpty()) leads.add(request);
                        else issues.add(new Issue(rowNumber, "CSV_ROW_VALIDATION_FAILED"));
                    }
                    case CONTACT -> {
                        ContactCreateRequest request = contact(row);
                        if (validator.validate(request).isEmpty()) contacts.add(request);
                        else issues.add(new Issue(rowNumber, "CSV_ROW_VALIDATION_FAILED"));
                    }
                    case PRODUCT -> {
                        ProductCreateRequest request = product(row);
                        if (isAvailability(request.getAvailability()) && validator.validate(request).isEmpty()) {
                            products.add(request);
                        } else {
                            issues.add(new Issue(rowNumber, "CSV_ROW_VALIDATION_FAILED"));
                        }
                    }
                }
            }
            return new Parsed(List.copyOf(leads), List.copyOf(contacts), List.copyOf(products),
                    List.copyOf(issues));
        } catch (IOException | CsvParseException exception) {
            return Parsed.invalid(0, "CSV_FORMAT_INVALID");
        }
    }

    private static LeadCreateRequest lead(List<String> row) {
        return new LeadCreateRequest().setName(required(row.get(0))).setPhoneNumber(empty(row.get(1)))
                .setTelephone(empty(row.get(2))).setJobTitle(empty(row.get(3))).setEmail(empty(row.get(4)))
                .setWebsite(empty(row.get(5))).setDescription(empty(row.get(6))).setCustomerId(id(row.get(7)))
                .setTags(tags(row.get(8)));
    }

    private static ContactCreateRequest contact(List<String> row) {
        return new ContactCreateRequest().setName(required(row.get(0))).setPhoneNumber(empty(row.get(1)))
                .setTelephone(empty(row.get(2))).setJobTitle(empty(row.get(3))).setEmail(empty(row.get(4)))
                .setWebsite(empty(row.get(5))).setDescription(empty(row.get(6))).setCustomerId(id(row.get(7)))
                .setTags(tags(row.get(8)));
    }

    private static ProductCreateRequest product(List<String> row) {
        return new ProductCreateRequest().setName(required(row.get(0))).setCode(required(row.get(1)))
                .setCategoryId(id(row.get(2))).setUnit(empty(row.get(3))).setListPrice(price(row.get(4)))
                .setDescription(empty(row.get(5))).setAvailability(availability(row.get(6)));
    }

    private static String required(String value) { return value == null ? "" : value.trim(); }
    private static String empty(String value) { String result = required(value); return result.isEmpty() ? null : result; }

    private static Long id(String value) {
        String result = empty(value);
        if (result == null) return null;
        try { return Long.parseLong(result); }
        catch (NumberFormatException exception) { return 0L; }
    }

    private static BigDecimal price(String value) {
        String result = empty(value);
        if (result == null) return null;
        try { return new BigDecimal(result); }
        catch (NumberFormatException exception) { return null; }
    }

    private static boolean isAvailability(String value) {
        if (value == null) return true;
        try {
            ProductAvailability.valueOf(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String availability(String value) {
        String result = empty(value);
        return result == null ? null : result.toUpperCase(java.util.Locale.ROOT);
    }

    private static List<String> tags(String value) {
        String result = empty(value);
        if (result == null) return List.of();
        Set<String> values = new LinkedHashSet<>();
        for (String part : result.split(";", -1)) values.add(part.trim());
        return List.copyOf(values);
    }

    private static boolean headers(List<String> values, List<String> expected) {
        if (values.size() != expected.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            String value = values.get(index);
            if (index == 0 && value != null && value.startsWith("\uFEFF")) value = value.substring(1);
            if (!expected.get(index).equals(value)) return false;
        }
        return true;
    }

    private static String strictUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    private static List<List<String>> records(String source) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < source.length() && source.charAt(index + 1) == '"') {
                        cell.append(current); index++;
                    } else quoted = false;
                } else cell.append(current);
                continue;
            }
            if (current == '"') {
                if (!cell.isEmpty()) throw new CsvParseException();
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString()); cell.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < source.length() && source.charAt(index + 1) == '\n') index++;
                row.add(cell.toString()); cell.setLength(0); rows.add(List.copyOf(row)); row.clear();
            } else {
                if (Character.isISOControl(current) && current != '\t') throw new CsvParseException();
                cell.append(current);
            }
        }
        if (quoted) throw new CsvParseException();
        if (!cell.isEmpty() || !row.isEmpty()) { row.add(cell.toString()); rows.add(List.copyOf(row)); }
        return List.copyOf(rows);
    }

    public record Parsed(List<LeadCreateRequest> leads, List<ContactCreateRequest> contacts,
                         List<ProductCreateRequest> products, List<Issue> issues) {
        static Parsed invalid(int rowNumber, String code) {
            return new Parsed(List.of(), List.of(), List.of(), List.of(new Issue(rowNumber, code)));
        }
        public int validRows() { return leads.size() + contacts.size() + products.size(); }
        public boolean valid() { return issues.isEmpty() && validRows() > 0; }
    }

    public record Issue(int rowNumber, String errCode) { }

    private static final class CsvParseException extends RuntimeException { }
}
