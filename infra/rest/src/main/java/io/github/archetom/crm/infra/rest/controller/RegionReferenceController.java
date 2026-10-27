package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.facade.RegionReferenceFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for region reference data; it is separate from organization aggregates. */
@RestController
@RequestMapping("/api/v1/reference/regions")
@RequiredArgsConstructor
public class RegionReferenceController {

    private final RegionReferenceFacade regionReferenceFacade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping
    public ResponseEntity<?> listChildren(Authentication authentication,
                                          @RequestParam(required = false) String parentCode) {
        AuthenticatedCaller caller = callerMapper.from(authentication);
        return ResponseEntityUtil.assembleResponse(regionReferenceFacade.listChildren(caller, parentCode));
    }
}
