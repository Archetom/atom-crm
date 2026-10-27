package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.dto.request.CustomerCsvExportRequest;
import io.github.archetom.crm.application.export.CustomerCsvDocument;
import io.github.archetom.crm.application.service.CustomerCsvExportService;
import io.github.archetom.crm.application.service.impl.CustomerCsvExportAdmission;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.rest.security.ActorPrincipal;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.common.result.Result;
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

class CustomerControllerCsvExportTest {

    @Test
    void writesCompletedDocumentThroughSpringResourceConverterWithFixedDownloadHeaders() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        CustomerCsvDocument document = new CustomerCsvDocument(
                "\uFEFFcustomer_id,customer_name\r\n2,Acme\r\n".getBytes(StandardCharsets.UTF_8),
                releases::incrementAndGet);
        Result<CustomerCsvDocument> result = new Result<>();
        result.setSuccess(true);
        result.setData(document);
        CustomerCsvExportService export = (caller, request) -> result;
        CustomerController controller = new CustomerController(null, null, export,
                new AuthenticatedCallerMapper());
        var authentication = new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 9L), null,
                List.of(new SimpleGrantedAuthority("customers:read"),
                        new SimpleGrantedAuthority("customers:export")));

        var response = controller.exportCsv(authentication,
                new CustomerCsvExportRequest().setFieldCodes(List.of("customer_name")));

        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("attachment; filename=\"customers.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        InputStreamResource body = assertInstanceOf(InputStreamResource.class, response.getBody());
        BufferingHttpOutputMessage output = new BufferingHttpOutputMessage();
        new ResourceHttpMessageConverter().write(body, MediaType.parseMediaType("text/csv;charset=UTF-8"), output);
        assertEquals("\uFEFFcustomer_id,customer_name\r\n2,Acme\r\n",
                output.bodyAsString());
        assertEquals(1, releases.get());
        document.close();
        assertEquals(1, releases.get());
    }

    @Test
    void releasesCompletedDocumentWhenTheSpringWriterFailsLikeClientCancellation() throws Exception {
        TenantId tenant = new TenantId(9L);
        MemberId member = new MemberId(7L);
        CustomerCsvExportAdmission admission = new CustomerCsvExportAdmission();
        CustomerCsvExportAdmission.Lease lease = admission.tryAcquire(tenant, member);
        assertNotNull(lease);
        CustomerCsvDocument document = new CustomerCsvDocument(
                "\uFEFFcustomer_id,customer_name\r\n2,Acme\r\n".getBytes(StandardCharsets.UTF_8), lease);
        Result<CustomerCsvDocument> result = new Result<>();
        result.setSuccess(true);
        result.setData(document);
        CustomerCsvExportService export = (caller, request) -> result;
        CustomerController controller = new CustomerController(null, null, export,
                new AuthenticatedCallerMapper());
        var authentication = new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 9L), null,
                List.of(new SimpleGrantedAuthority("customers:read"),
                        new SimpleGrantedAuthority("customers:export")));

        InputStreamResource body = assertInstanceOf(InputStreamResource.class, controller.exportCsv(
                authentication, new CustomerCsvExportRequest().setFieldCodes(List.of("customer_name"))).getBody());

        assertNull(admission.tryAcquire(tenant, member));
        assertThrows(IOException.class, () -> new ResourceHttpMessageConverter().write(body,
                MediaType.parseMediaType("text/csv;charset=UTF-8"), failingOutput()));
        CustomerCsvExportAdmission.Lease reacquired = admission.tryAcquire(tenant, member);
        assertNotNull(reacquired);
        reacquired.close();
        document.close();
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
