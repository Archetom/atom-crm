package io.github.archetom.crm.infra.facade;

import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.api.dto.response.AttachmentResponse;
import io.github.archetom.crm.application.service.FileFieldValueQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Suppresses structured values when any independent FILE read predicate does not hold. */
@Component
@RequiredArgsConstructor
class FileFieldResponseEnricher {
    private final FileFieldValueQueryService service;

    Map<String, List<AttachmentResponse>> crm(AuthenticatedCaller caller, String targetType, Long targetId) {
        var result = service.crmValues(caller, targetType, targetId);
        if (!result.isSuccess() || result.getData() == null) return Map.of();
        return responses(result.getData());
    }

    /**
     * Resolves a whole page in one read. Enriching row by row repeats the membership lookup,
     * target authorization, field catalog and value query per row, each in its own transaction.
     */
    Map<Long, Map<String, List<AttachmentResponse>>> crm(
            AuthenticatedCaller caller, String targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) return Map.of();
        var result = service.crmValues(caller, targetType, targetIds);
        if (!result.isSuccess() || result.getData() == null) return Map.of();
        return result.getData().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> responses(entry.getValue()),
                (left, right) -> left, java.util.LinkedHashMap::new));
    }

    private static Map<String, List<AttachmentResponse>> responses(
            Map<String, List<io.github.archetom.crm.application.vo.AttachmentVO>> values) {
        return values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(AttachmentResponseAssembler::toResponse).toList(),
                (left, right) -> left, java.util.LinkedHashMap::new));
    }

    /** Resolves a whole page of OA requests in one read, for the same reason as {@link #crm}. */
    Map<Long, Map<String, List<AttachmentResponse>>> oa(
            AuthenticatedCaller caller, List<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) return Map.of();
        var result = service.oaValues(caller, requestIds);
        if (!result.isSuccess() || result.getData() == null) return Map.of();
        return result.getData().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> responses(entry.getValue()),
                (left, right) -> left, java.util.LinkedHashMap::new));
    }

    Map<String, List<AttachmentResponse>> oa(AuthenticatedCaller caller, Long requestId) {
        var result = service.oaValues(caller, requestId);
        if (!result.isSuccess() || result.getData() == null) return Map.of();
        return result.getData().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(AttachmentResponseAssembler::toResponse).toList(),
                (left, right) -> left, java.util.LinkedHashMap::new));
    }
}
