package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.facade.WorklistFacade;
import io.github.archetom.crm.api.dto.request.WorklistAcknowledgementRequest;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/worklist")
@RequiredArgsConstructor
@Tag(name = "Worklist", description = "Current caller's local follow-up worklist")
public class WorklistController {
    private final WorklistFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    @Operation(summary = "List due local follow-ups and scheduled return visits for one date")
    public ResponseEntity<?> query(Authentication authentication,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate date, @RequestParam(required = false) String cursor,
                                   @RequestParam(required = false) Integer size) {
        return ResponseEntityUtil.assembleResponse(facade.query(callerMapper.from(authentication), date, cursor, size));
    }

    @PostMapping("/acknowledgements")
    @Operation(summary = "Acknowledge visible worklist items or a current category snapshot")
    public ResponseEntity<?> acknowledge(
            Authentication authentication,
            @RequestBody @Valid WorklistAcknowledgementRequest request) {
        return ResponseEntityUtil.assembleResponse(
                facade.acknowledge(callerMapper.from(authentication), request));
    }
}
