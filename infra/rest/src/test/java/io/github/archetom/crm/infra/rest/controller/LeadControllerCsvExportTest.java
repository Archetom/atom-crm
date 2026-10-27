package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.dto.request.LeadCsvExportRequest;
import io.github.archetom.crm.application.export.LeadCsvDocument;
import io.github.archetom.crm.application.service.LeadCsvExportService;
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LeadControllerCsvExportTest {

    @Test
    void writesTheCompletedLeadDocumentWithFixedDownloadHeaders() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        LeadCsvDocument document = new LeadCsvDocument(
                "\uFEFFlead_id,lead_name\r\n2,Ada\r\n".getBytes(StandardCharsets.UTF_8),
                releases::incrementAndGet);
        Result<LeadCsvDocument> result = new Result<>();
        result.setSuccess(true);
        result.setData(document);
        LeadCsvExportService export = (caller, request) -> result;
        LeadController controller = new LeadController(null, export,
                new AuthenticatedCallerMapper());
        var authentication = new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 9L), null,
                List.of(new SimpleGrantedAuthority("leads:read"),
                        new SimpleGrantedAuthority("leads:export")));

        var response = controller.exportCsv(authentication,
                new LeadCsvExportRequest().setFieldCodes(List.of("lead_name")));

        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("attachment; filename=\"leads.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        InputStreamResource body = assertInstanceOf(InputStreamResource.class, response.getBody());
        BufferingHttpOutputMessage output = new BufferingHttpOutputMessage();
        new ResourceHttpMessageConverter().write(body,
                MediaType.parseMediaType("text/csv;charset=UTF-8"), output);
        assertEquals("\uFEFFlead_id,lead_name\r\n2,Ada\r\n", output.bodyAsString());
        assertEquals(1, releases.get());
        document.close();
        assertEquals(1, releases.get());
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
