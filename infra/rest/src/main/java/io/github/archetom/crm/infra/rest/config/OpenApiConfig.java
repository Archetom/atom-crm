package io.github.archetom.crm.infra.rest.config;

import io.github.archetom.crm.domain.model.CustomFieldType;
import io.github.archetom.crm.domain.model.FieldAccessLevel;
import io.github.archetom.crm.domain.model.FieldMaskType;
import io.github.archetom.crm.infra.rest.security.TrustedHeaderAuthenticationFilter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Arrays;

/**
 * Publishes an API description that reflects authentication actually available
 * in the generated application.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String DEV_USER_ID_SCHEME = "devUserId";
    static final String DEV_TENANT_ID_SCHEME = "devTenantId";

    @Bean
    public OpenAPI customOpenAPI(Environment environment) {
        String applicationName = environment.getProperty("spring.application.name", "atom-crm");
        String apiVersion = environment.getProperty("atom.api.version", "v1");

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .version(apiVersion)
                        .description(applicationName + " REST API")
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/license/mit")))
                .components(new Components());

        if (trustedHeaderAdapterEnabled(environment)) {
            addDevelopmentHeaderSecurity(openAPI);
        }
        return openAPI;
    }

    /**
     * Springdoc currently loses {@code allowableValues} for these Lombok-backed
     * string properties. Publish the values from the domain enums explicitly so
     * generated clients can never have to guess field configuration values.
     */
    @Bean
    public OpenApiCustomizer fieldCustomizationEnumContract() {
        return openAPI -> {
            addLegacyPagerSchema(openAPI);
            setEnum(openAPI, "FieldCreateRequest", "fieldType", CustomFieldType.values());
            setEnum(openAPI, "FieldDefinitionResponse", "fieldType", CustomFieldType.values());
            setEnum(openAPI, "RoleFieldAccessRequest", "accessLevel", FieldAccessLevel.values());
            setEnum(openAPI, "RoleFieldAccessResponse", "accessLevel", FieldAccessLevel.values());
            setEnum(openAPI, "RoleFieldAccessRequest", "maskType", FieldMaskType.values());
            setEnum(openAPI, "RoleFieldAccessResponse", "maskType", FieldMaskType.values());
        };
    }

    /**
     * Retains the documented generic page carrier while legacy paged routes are
     * progressively migrated to named response schemas. Existing generated
     * clients use this component to decode their explicitly generic endpoints;
     * newly typed routes must not use it as their success response.
     */
    private void addLegacyPagerSchema(OpenAPI openAPI) {
        Schema<Object> anyValue = new Schema<>();
        Schema<?> pager = new ObjectSchema()
                .addProperty("totalNum", new IntegerSchema().format("int64"))
                .addProperty("pageNum", new IntegerSchema().format("int64"))
                .addProperty("pageSize", new IntegerSchema().format("int64"))
                .addProperty("objectList", new ArraySchema().items(anyValue))
                .addProperty("meta", new ObjectSchema().additionalProperties(anyValue));
        openAPI.getComponents().addSchemas("Pager", pager);
    }

    @SuppressWarnings("unchecked")
    private void setEnum(OpenAPI openAPI, String schemaName, String propertyName, Enum<?>[] values) {
        Schema<?> schema = openAPI.getComponents().getSchemas().get(schemaName);
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        Schema<String> property = (Schema<String>) schema.getProperties().get(propertyName);
        if (property != null) {
            property.setEnum(Arrays.stream(values).map(Enum::name).toList());
        }
    }

    private boolean trustedHeaderAdapterEnabled(Environment environment) {
        return environment.getProperty("atom.security.trusted-header.enabled", Boolean.class, false)
                && environment.acceptsProfiles(Profiles.of("(dev | test) & !prod"));
    }

    private void addDevelopmentHeaderSecurity(OpenAPI openAPI) {
        openAPI.getComponents()
                .addSecuritySchemes(DEV_USER_ID_SCHEME, developmentHeader(
                        TrustedHeaderAuthenticationFilter.USER_ID_HEADER,
                        "Positive actor ID accepted only by the explicitly enabled dev/test adapter"))
                .addSecuritySchemes(DEV_TENANT_ID_SCHEME, developmentHeader(
                        TrustedHeaderAuthenticationFilter.TENANT_ID_HEADER,
                        "Positive tenant ID accepted only by the explicitly enabled dev/test adapter"));
        openAPI.addSecurityItem(new SecurityRequirement()
                .addList(DEV_USER_ID_SCHEME)
                .addList(DEV_TENANT_ID_SCHEME));
    }

    private SecurityScheme developmentHeader(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }
}
