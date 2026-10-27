package io.github.archetom.crm.api.json;

import io.github.archetom.crm.api.dto.request.ReturnVisitUpdateRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Preserves explicit-null versus omitted return-visit relation fields in outbound requests. */
public final class ReturnVisitUpdateRequestSerializer extends ValueSerializer<ReturnVisitUpdateRequest> {

    @Override
    public void serialize(
            ReturnVisitUpdateRequest value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        generator.writeStartObject(value);
        write(context, generator, "version", value.getVersion());
        if (value.contactIdSpecified()) {
            write(context, generator, "contactId", value.getContactId());
        }
        if (value.contractIdSpecified()) {
            write(context, generator, "contractId", value.getContractId());
        }
        write(context, generator, "visitTime", value.getVisitTime());
        if (value.visitFormSpecified()) {
            write(context, generator, "visitForm", value.getVisitForm());
        }
        if (value.satisfactionSpecified()) {
            write(context, generator, "satisfaction", value.getSatisfaction());
        }
        write(context, generator, "remark", value.getRemark());
        write(context, generator, "customFields", value.getCustomFields());
        write(context, generator, "fileFields", value.getFileFields());
        generator.writeEndObject();
    }

    private void write(
            SerializationContext context, JsonGenerator generator, String name, Object value)
            throws JacksonException {
        generator.writeName(name);
        context.writeValue(generator, value);
    }
}
