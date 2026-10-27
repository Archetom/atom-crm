package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.ContactCsvExportRequest;
import io.github.archetom.crm.application.export.ContactCsvDocument;
import io.github.archetom.crm.application.service.ContactCsvExportService;
import io.github.archetom.crm.application.service.impl.CustomerCsvExportAdmission;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.rest.security.ActorPrincipal;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactControllerCsvExportTest {

    @Test
    void writesTheCompletedContactDocumentWithFixedDownloadHeaders() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        ContactCsvDocument document = new ContactCsvDocument(
                "\uFEFFcontact_id,contact_name\r\n2,Ada\r\n".getBytes(StandardCharsets.UTF_8),
                releases::incrementAndGet);
        Result<ContactCsvDocument> result = new Result<>();
        result.setSuccess(true);
        result.setData(document);
        ContactCsvExportService export = (caller, request) -> result;
        ContactController controller = new ContactController(null, export,
                new AuthenticatedCallerMapper());
        var authentication = new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 9L), null,
                List.of(new SimpleGrantedAuthority("contacts:read"),
                        new SimpleGrantedAuthority("contacts:export")));

        var response = controller.exportCsv(authentication,
                new ContactCsvExportRequest().setFieldCodes(List.of("contact_name")));

        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("attachment; filename=\"contacts.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        InputStreamResource body = assertInstanceOf(InputStreamResource.class, response.getBody());
        BufferingHttpOutputMessage output = new BufferingHttpOutputMessage();
        new ResourceHttpMessageConverter().write(body,
                MediaType.parseMediaType("text/csv;charset=UTF-8"), output);
        assertEquals("\uFEFFcontact_id,contact_name\r\n2,Ada\r\n", output.bodyAsString());
        assertEquals(1, releases.get());
        document.close();
        assertEquals(1, releases.get());
    }

    @Test
    void releasesContactDocumentWhenTheSpringWriterFailsLikeClientCancellation() throws Exception {
        TenantId tenant = new TenantId(9L);
        MemberId member = new MemberId(7L);
        CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        CustomerCsvExportAdmission.Lease lease = admission.tryAcquire(tenant, member);
        assertNotNull(lease);
        AtomicInteger releases = new AtomicInteger();
        ContactCsvDocument document = new ContactCsvDocument(
                "\uFEFFcontact_id,contact_name\r\n2,Ada\r\n".getBytes(StandardCharsets.UTF_8), () -> {
                    releases.incrementAndGet();
                    lease.close();
                });
        Result<ContactCsvDocument> result = new Result<>();
        result.setSuccess(true);
        result.setData(document);
        ContactCsvExportService export = (caller, request) -> result;
        ContactController controller = new ContactController(null, export,
                new AuthenticatedCallerMapper());
        var authentication = new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 9L), null,
                List.of(new SimpleGrantedAuthority("contacts:read"),
                        new SimpleGrantedAuthority("contacts:export")));

        InputStreamResource body = assertInstanceOf(InputStreamResource.class, controller.exportCsv(
                authentication, new ContactCsvExportRequest().setFieldCodes(List.of("contact_name"))).getBody());

        assertNull(admission.tryAcquire(tenant, member));
        assertThrows(IOException.class, () -> new ResourceHttpMessageConverter().write(body,
                MediaType.parseMediaType("text/csv;charset=UTF-8"), failingOutput()));
        assertEquals(1, releases.get());
        CustomerCsvExportAdmission.Lease reacquired = admission.tryAcquire(tenant, member);
        assertNotNull(reacquired);
        reacquired.close();
        document.close();
        assertEquals(1, releases.get());
    }

    private static HttpOutputMessage failingOutput() {
        return new HttpOutputMessage() {
            private final HttpHeaders headers = new HttpHeaders();

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }

            @Override
            public OutputStream getBody() {
                return new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("simulated client cancellation");
                    }

                    @Override
                    public void write(byte[] value, int offset, int length) throws IOException {
                        throw new IOException("simulated client cancellation");
                    }
                };
            }
        };
    }

    private static final class BufferingHttpOutputMessage implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public OutputStream getBody() {
            return body;
        }

        private String bodyAsString() {
            return body.toString(StandardCharsets.UTF_8);
        }
    }
}
