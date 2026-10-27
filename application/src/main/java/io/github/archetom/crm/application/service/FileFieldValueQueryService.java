package io.github.archetom.crm.application.service;

import io.github.archetom.common.result.Result;
import io.github.archetom.crm.api.context.AuthenticatedCaller;
import io.github.archetom.crm.application.vo.AttachmentVO;

import java.util.List;
import java.util.Map;

/** Read-side policy gate for structured FILE values; it never exposes attachment relations. */
public interface FileFieldValueQueryService {
    Result<Map<String, List<AttachmentVO>>> crmValues(AuthenticatedCaller caller,
                                                       String targetType, Long targetId);

    /**
     * Resolves the FILE values of a whole page in one read, keyed by target id.
     *
     * <p>A list projection needs the same values for every row it already admitted. Asking per row
     * repeats the membership lookup, the target authorization, the field catalog and the value
     * query once per row, each in its own transaction.</p>
     */
    Result<Map<Long, Map<String, List<AttachmentVO>>>> crmValues(AuthenticatedCaller caller,
                                                                  String targetType,
                                                                  List<Long> targetIds);

    Result<Map<String, List<AttachmentVO>>> oaValues(AuthenticatedCaller caller, Long requestId);

    /** Resolves the OA FILE facts of a whole page in one read, keyed by request id. */
    Result<Map<Long, Map<String, List<AttachmentVO>>>> oaValues(
            AuthenticatedCaller caller, List<Long> requestIds);
}
