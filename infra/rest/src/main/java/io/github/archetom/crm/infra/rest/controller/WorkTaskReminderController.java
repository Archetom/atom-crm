package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskReminderPlanUpdateRequest;
import io.github.archetom.crm.api.facade.WorkTaskReminderFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work")
@RequiredArgsConstructor
@Validated
@Tag(name = "Work task reminders", description = "Due-time reminder plans for Work tasks and future series occurrences")
public class WorkTaskReminderController {
    private final WorkTaskReminderFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @GetMapping("/tasks/{taskId}/reminders")
    @Operation(summary = "Get a visible Work-task reminder plan")
    public ResponseEntity<?> getTaskPlan(Authentication authentication, @PathVariable @Positive Long taskId) {
        return response(facade.getTaskPlan(caller(authentication), taskId));
    }

    @PutMapping("/tasks/{taskId}/reminders")
    @Operation(summary = "Replace a Work-task reminder plan")
    public ResponseEntity<?> updateTaskPlan(Authentication authentication, @PathVariable @Positive Long taskId,
                                             @RequestBody @Valid WorkTaskReminderPlanUpdateRequest request) {
        return response(facade.updateTaskPlan(caller(authentication), taskId, request));
    }

    @GetMapping("/task-series/{seriesId}/reminders")
    @Operation(summary = "Get a Work-task series future reminder template")
    public ResponseEntity<?> getSeriesPlan(Authentication authentication, @PathVariable @Positive Long seriesId) {
        return response(facade.getSeriesPlan(caller(authentication), seriesId));
    }

    @PutMapping("/task-series/{seriesId}/reminders")
    @Operation(summary = "Replace a Work-task series future reminder template")
    public ResponseEntity<?> updateSeriesPlan(Authentication authentication, @PathVariable @Positive Long seriesId,
                                               @RequestBody @Valid WorkTaskReminderPlanUpdateRequest request) {
        return response(facade.updateSeriesPlan(caller(authentication), seriesId, request));
    }

    private AuthenticatedCaller caller(Authentication authentication) { return callerMapper.from(authentication); }
    private ResponseEntity<?> response(Result<?> result) { return ResponseEntityUtil.assembleResponse(result); }
}
