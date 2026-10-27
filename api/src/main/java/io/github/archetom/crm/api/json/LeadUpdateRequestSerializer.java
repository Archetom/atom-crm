package io.github.archetom.crm.api.json;

import io.github.archetom.crm.api.dto.request.LeadUpdateRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Preserves explicit-null versus omitted relation and time fields in outbound requests. */
public final class LeadUpdateRequestSerializer extends ValueSerializer<LeadUpdateRequest> {

    @Override
    public void serialize(
            LeadUpdateRequest value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        generator.writeStartObject(value);
        write(context, generator, "version", value.getVersion());
        write(context, generator, "name", value.getName());
        write(context, generator, "phoneNumber", value.getPhoneNumber());
        write(context, generator, "telephone", value.getTelephone());
        write(context, generator, "jobTitle", value.getJobTitle());
        write(context, generator, "email", value.getEmail());
        write(context, generator, "website", value.getWebsite());
        write(context, generator, "description", value.getDescription());
        if (value.nextContactTimeSpecified()) {
            write(context, generator, "nextContactTime", value.getNextContactTime());
        }
        if (value.customerIdSpecified()) {
            write(context, generator, "customerId", value.getCustomerId());
        }
        write(context, generator, "addresses", value.getAddresses());
        write(context, generator, "customFields", value.getCustomFields());
        generator.writeEndObject();
    }

    private void write(
            SerializationContext context, JsonGenerator generator, String name, Object value)
            throws JacksonException {
        generator.writeName(name);
        context.writeValue(generator, value);
    }
}
