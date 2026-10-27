package io.github.archetom.crm;

import io.github.archetom.crm.api.dto.response.MarketingActivityResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies optional activity fields follow the generated OpenAPI omission contract in every profile. */
class MarketingActivityResponseSerializationTest {

    @Test
    void omitsUnsetOptionalPropertiesEvenWhenMapperDefaultsIncludeNulls() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new MarketingActivityResponse().setId(1L));

        assertFalse(json.contains("\"marketingFormId\""));
        assertFalse(json.contains("\"marketingFormPublicId\""));
        assertFalse(json.contains("\"activityType\""));
        assertFalse(json.contains("\"budget\""));
        assertFalse(json.contains("\"address\""));
        assertFalse(json.contains("\"description\""));
    }
}
