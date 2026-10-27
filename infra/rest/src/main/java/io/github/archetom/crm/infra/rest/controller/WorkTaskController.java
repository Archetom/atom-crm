package io.github.archetom.crm.infra.rest.controller;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.request.WorkTaskAssigneeUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskBusinessRecordUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCommentCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskCreateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskFollowerUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskQueryRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskUpdateRequest;
import io.github.archetom.crm.api.dto.request.WorkTaskVersionRequest;
import io.github.archetom.crm.api.dto.response.WorkTaskCommentPageResponse;
import io.github.archetom.crm.api.dto.response.WorkTaskPageResponse;
import io.github.archetom.crm.api.facade.WorkTaskFacade;
import io.github.archetom.crm.infra.rest.security.AuthenticatedCallerMapper;
import io.github.archetom.crm.infra.rest.util.ResponseEntityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work/tasks")
@RequiredArgsConstructor
@Validated
@Tag(name = "Work tasks", description = "Tenant-scoped manually managed work tasks")
public class WorkTaskController {

    private final WorkTaskFacade facade;
    private final AuthenticatedCallerMapper callerMapper;

    @PostMapping("/query")
    @Operation(summary = "Query visible work tasks", responses = @ApiResponse(
            responseCode = "200", description = "Server-authorized Work task page",
            content = @Content(schema = @Schema(implementation = WorkTaskPageResponse.class))))
    public ResponseEntity<?> query(
            Authentication authentication,
            @RequestBody @Valid WorkTaskQueryRequest request) {
        return response(facade.query(caller(authentication), request));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get a visible work task")
    public ResponseEntity<?> get(
            Authentication authentication, @PathVariable @Positive Long taskId) {
        return response(facade.get(caller(authentication), taskId));
    }

    @PostMapping
    @Operation(summary = "Create and assign a work task")
    public ResponseEntity<?> create(
            Authentication authentication,
            @RequestBody @Valid WorkTaskCreateRequest request) {
        return response(facade.create(caller(authentication), request));
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Update work task structure")
    public ResponseEntity<?> update(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskUpdateRequest request) {
        return response(facade.update(caller(authentication), taskId, request));
    }

    @PutMapping("/{taskId}/assignee")
    @Operation(summary = "Reassign an open work task")
    public ResponseEntity<?> updateAssignee(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskAssigneeUpdateRequest request) {
        return response(facade.updateAssignee(caller(authentication), taskId, request));
    }

    @PutMapping("/{taskId}/followers")
    @Operation(summary = "Replace followers on an open work task")
    public ResponseEntity<?> replaceFollowers(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskFollowerUpdateRequest request) {
        return response(facade.replaceFollowers(caller(authentication), taskId, request));
    }

    @PutMapping("/{taskId}/business-record")
    @Operation(summary = "Replace or clear the CRM record associated with a work task")
    public ResponseEntity<?> updateBusinessRecord(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskBusinessRecordUpdateRequest request) {
        return response(facade.updateBusinessRecord(caller(authentication), taskId, request));
    }

    @GetMapping("/{taskId}/comments")
    @Operation(summary = "Page active comments on a visible work task", responses = @ApiResponse(
            responseCode = "200", description = "Server-authorized Work task comment page",
            content = @Content(schema = @Schema(implementation = WorkTaskCommentPageResponse.class))))
    public ResponseEntity<?> queryComments(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestParam(defaultValue = "1") @Positive Integer page,
            @RequestParam(defaultValue = "20") @Positive Integer size) {
        return response(facade.queryComments(
                caller(authentication), taskId, page, size));
    }

    @PostMapping("/{taskId}/comments")
    @Operation(summary = "Add a plain-text comment to a visible work task")
    public ResponseEntity<?> createComment(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskCommentCreateRequest request) {
        return response(facade.createComment(caller(authentication), taskId, request));
    }

    @DeleteMapping("/{taskId}/comments/{commentId}")
    @Operation(summary = "Soft-delete an authored work-task comment")
    public ResponseEntity<?> deleteComment(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @PathVariable @Positive Long commentId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.deleteComment(
                caller(authentication), taskId, commentId, version));
    }

    @PostMapping("/{taskId}/start")
    @Operation(summary = "Start a todo work task")
    public ResponseEntity<?> start(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskVersionRequest request) {
        return response(facade.start(caller(authentication), taskId, request));
    }

    @PostMapping("/{taskId}/return-to-todo")
    @Operation(summary = "Return an in-progress work task to todo")
    public ResponseEntity<?> returnToTodo(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskVersionRequest request) {
        return response(facade.returnToTodo(caller(authentication), taskId, request));
    }

    @PostMapping("/{taskId}/complete")
    @Operation(summary = "Complete an open work task")
    public ResponseEntity<?> complete(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskVersionRequest request) {
        return response(facade.complete(caller(authentication), taskId, request));
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "Cancel an open work task")
    public ResponseEntity<?> cancel(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestBody @Valid WorkTaskVersionRequest request) {
        return response(facade.cancel(caller(authentication), taskId, request));
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "Soft-delete a never-started work task")
    public ResponseEntity<?> delete(
            Authentication authentication, @PathVariable @Positive Long taskId,
            @RequestParam @NotNull @Min(0) Long version) {
        return response(facade.delete(caller(authentication), taskId, version));
    }

    private AuthenticatedCaller caller(Authentication authentication) {
        return callerMapper.from(authentication);
    }

    private ResponseEntity<?> response(Result<?> result) {
        return ResponseEntityUtil.assembleResponse(result);
    }
}
