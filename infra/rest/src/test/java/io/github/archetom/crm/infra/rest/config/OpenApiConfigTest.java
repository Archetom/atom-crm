package io.github.archetom.crm.infra.rest.config;

import io.github.archetom.crm.infra.rest.security.TrustedHeaderAuthenticationFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void usesConfiguredApplicationNameAndApiVersionWithoutAdvertisingBearerAuth() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "orders-service")
                .withProperty("atom.api.version", "v2")
                .withProperty("atom.security.trusted-header.enabled", "false");

        OpenAPI openAPI = config.customOpenAPI(environment);

        assertEquals("orders-service API", openAPI.getInfo().getTitle());
        assertEquals("v2", openAPI.getInfo().getVersion());
        assertTrue(openAPI.getSecurity() == null || openAPI.getSecurity().isEmpty());
        Map<String, SecurityScheme> schemes = openAPI.getComponents().getSecuritySchemes();
        assertTrue(schemes == null || !schemes.containsKey("bearerAuth"));
    }

    @Test
    void documentsBothDevelopmentHeadersOnlyWhenTheAdapterIsEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "orders-service")
                .withProperty("atom.security.trusted-header.enabled", "true");
        environment.setActiveProfiles("dev");

        OpenAPI openAPI = config.customOpenAPI(environment);
        Map<String, SecurityScheme> schemes = openAPI.getComponents().getSecuritySchemes();

        assertNotNull(schemes);
        assertHeaderScheme(
                schemes.get(OpenApiConfig.DEV_USER_ID_SCHEME),
                TrustedHeaderAuthenticationFilter.USER_ID_HEADER);
        assertHeaderScheme(
                schemes.get(OpenApiConfig.DEV_TENANT_ID_SCHEME),
                TrustedHeaderAuthenticationFilter.TENANT_ID_HEADER);
        assertFalse(schemes.containsKey("bearerAuth"));

        SecurityRequirement requirement = openAPI.getSecurity().getFirst();
        assertTrue(requirement.containsKey(OpenApiConfig.DEV_USER_ID_SCHEME));
        assertTrue(requirement.containsKey(OpenApiConfig.DEV_TENANT_ID_SCHEME));
    }

    @Test
    void doesNotDocumentDevelopmentHeadersUnderTheProductionProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("atom.security.trusted-header.enabled", "true");
        environment.setActiveProfiles("prod");

        OpenAPI openAPI = config.customOpenAPI(environment);

        assertTrue(openAPI.getSecurity() == null || openAPI.getSecurity().isEmpty());
        Map<String, SecurityScheme> schemes = openAPI.getComponents().getSecuritySchemes();
        assertTrue(schemes == null || schemes.isEmpty());
    }

    private void assertHeaderScheme(SecurityScheme scheme, String headerName) {
        assertNotNull(scheme);
        assertEquals(SecurityScheme.Type.APIKEY, scheme.getType());
        assertEquals(SecurityScheme.In.HEADER, scheme.getIn());
        assertEquals(headerName, scheme.getName());
    }
}
