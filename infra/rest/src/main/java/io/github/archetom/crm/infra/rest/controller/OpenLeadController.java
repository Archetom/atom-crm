package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.OpenLeadSubmissionRequest;
import io.github.archetom.crm.api.dto.response.OpenLeadSubmissionResponse;
import io.github.archetom.crm.api.facade.LeadFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.github.archetom.crm.shared.util.ResultUtil;
import io.github.archetom.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authentication filter binds tenant and owner from the API key, never this request body. */
@RestController
@RequestMapping("/api/v1/open/leads")
@RequiredArgsConstructor
@Tag(name = "Open lead intake", description = "API-key protected lead submission")
public class OpenLeadController {

    private final LeadFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping
    @Operation(summary = "Submit a lead with X-API-Key")
    public ResponseEntity<?> submit(Authentication authentication,
                                    @RequestBody @Valid OpenLeadSubmissionRequest request) {
        return response(ResultUtil.map(
                facade.create(caller(authentication), request.toInternalRequest()),
                ignored -> new OpenLeadSubmissionResponse(true)));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
