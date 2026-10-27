package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.application.export.CrmImportErrorReportDocument;
import io.github.archetom.crm.application.service.CrmImportDraftService;
import io.github.archetom.crm.infra.rest.security.ActorPrincipal;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrmImportDraftControllerErrorReportTest {

    @Test
    void downloadsOnlyTheDerivedIssueCoordinatesWithFixedNoStoreHeaders() {
        CrmImportDraftService service = mock(CrmImportDraftService.class);
        Result<CrmImportErrorReportDocument> result = new Result<>();
        result.setSuccess(true);
        result.setData(new CrmImportErrorReportDocument(
                "\uFEFFrowNumber,errCode\r\n3,CSV_ROW_VALIDATION_FAILED\r\n".getBytes(StandardCharsets.UTF_8)));
        when(service.errorReport(any(), eq(41L))).thenReturn(result);
        CrmImportDraftController controller = new CrmImportDraftController(null, service,
                new AuthenticatedCallerMapper());
        var authentication = new UsernamePasswordAuthenticationToken(new ActorPrincipal(7L, 9L), null, List.of());

        var response = controller.errorReport(authentication, 41L);

        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("attachment; filename=\"crm-import-errors.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("\uFEFFrowNumber,errCode\r\n3,CSV_ROW_VALIDATION_FAILED\r\n",
                new String(response.getBody(), StandardCharsets.UTF_8));
    }
}
