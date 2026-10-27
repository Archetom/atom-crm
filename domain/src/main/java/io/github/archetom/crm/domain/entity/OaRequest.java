package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.aggregate.AggregateRoot;
import io.github.archetom.crm.domain.exception.OaRequestDomainException;
import io.github.archetom.crm.domain.policy.OaRequestFieldCatalog;
import io.github.archetom.crm.domain.valueobject.ApprovalInstanceId;
import io.github.archetom.crm.domain.valueobject.DepartmentId;
import io.github.archetom.crm.domain.valueobject.FieldCode;
import io.github.archetom.crm.domain.valueobject.MemberId;
import io.github.archetom.crm.domain.valueobject.OaFormId;
import io.github.archetom.crm.domain.valueobject.OaRequestId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable OA submission facts with one write-once B11 approval association. */
@Getter
public class OaRequest extends AggregateRoot<OaRequestId> {

    /** Up to 100 configured inputs plus four server-derived authority facts. */
    public static final int MAX_FACTS = 104;

    private static final FieldCode REQUEST_TITLE = code(OaRequestFieldCatalog.REQUEST_TITLE);
    private static final FieldCode FORM_CODE = code(OaRequestFieldCatalog.FORM_CODE);
    private static final FieldCode APPLICANT_ACTOR = code(OaRequestFieldCatalog.APPLICANT_ACTOR);
    private static final FieldCode APPLICANT_DEPARTMENT =
            code(OaRequestFieldCatalog.APPLICANT_DEPARTMENT);
    private static final FieldCode SUBMITTED_TIME = code(OaRequestFieldCatalog.SUBMITTED_TIME);

    private OaRequestId id;
    private TenantId tenantId;
    private OaFormId formId;
    private Long formVersion;
    private String formCode;
    private MemberId applicantMemberId;
    private DepartmentId applicantDepartmentId;
    private ApprovalInstanceId approvalInstanceId;
    private String idempotencyKey;
    private String submissionDigest;
    private Map<FieldCode, List<String>> facts = Map.of();
    private LocalDateTime submittedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    private OaRequest() {
    }

    public record Snapshot(
            OaRequestId id,
            TenantId tenantId,
            OaFormId formId,
            Long formVersion,
            String formCode,
            MemberId applicantMemberId,
            DepartmentId applicantDepartmentId,
            ApprovalInstanceId approvalInstanceId,
            String idempotencyKey,
            String submissionDigest,
            Map<FieldCode, List<String>> facts,
            Long version,
            LocalDateTime submittedTime,
            LocalDateTime createdTime,
            LocalDateTime updatedTime) {
    }

    public static OaRequest submit(
            TenantId tenantId,
            OaFormId formId,
            Long formVersion,
            String formCode,
            MemberId applicantMemberId,
            DepartmentId applicantDepartmentId,
            String idempotencyKey,
            String submissionDigest,
            Map<FieldCode, List<String>> facts,
            LocalDateTime submittedTime) {
        OaRequest request = new OaRequest();
        request.tenantId = required(tenantId, "OA request tenant");
        request.formId = required(formId, "OA request form");
        request.formVersion = nonNegative(formVersion, "OA request form version");
        request.formCode = text(formCode, 200, "OA request form code");
        request.applicantMemberId = required(applicantMemberId, "OA request applicant");
        request.applicantDepartmentId = applicantDepartmentId;
        request.idempotencyKey = idempotencyKey(idempotencyKey);
        request.submissionDigest = digest(submissionDigest);
        request.submittedTime = time(submittedTime);
        request.facts = facts(facts);
        request.validateAuthorityFacts();
        request.createdTime = request.submittedTime;
        request.updatedTime = request.submittedTime;
        return request;
    }

    public static OaRequest reconstitute(Snapshot snapshot) {
        if (snapshot == null) {
            throw new OaRequestDomainException("OA request snapshot is required");
        }
        OaRequest request = new OaRequest();
        request.id = snapshot.id();
        request.tenantId = required(snapshot.tenantId(), "OA request tenant");
        request.formId = required(snapshot.formId(), "OA request form");
        request.formVersion = nonNegative(snapshot.formVersion(), "OA request form version");
        request.formCode = text(snapshot.formCode(), 200, "OA request form code");
        request.applicantMemberId = required(snapshot.applicantMemberId(), "OA request applicant");
        request.applicantDepartmentId = snapshot.applicantDepartmentId();
        request.approvalInstanceId = snapshot.approvalInstanceId();
        request.idempotencyKey = idempotencyKey(snapshot.idempotencyKey());
        request.submissionDigest = digest(snapshot.submissionDigest());
        request.facts = facts(snapshot.facts());
        request.submittedTime = time(snapshot.submittedTime());
        request.validateAuthorityFacts();
        request.restoreVersion(snapshot.version());
        request.createdTime = snapshot.createdTime();
        request.updatedTime = snapshot.updatedTime();
        return request;
    }

    public void linkApproval(ApprovalInstanceId approvalInstanceId) {
        if (id == null) {
            throw new OaRequestDomainException(
                    "OA request must be persisted before approval is linked");
        }
        if (this.approvalInstanceId != null) {
            if (this.approvalInstanceId.equals(approvalInstanceId)) {
                return;
            }
            throw new OaRequestDomainException("OA request approval is immutable");
        }
        this.approvalInstanceId = required(
                approvalInstanceId, "OA request approval instance");
        updatedTime = submittedTime;
    }

    public String getRequestTitle() {
        return facts.get(REQUEST_TITLE).getFirst();
    }

    public void onPersisted(
            OaRequestId persistedId,
            Long persistedVersion,
            LocalDateTime created,
            LocalDateTime updated) {
        if (persistedId == null || id != null && !id.sameValueAs(persistedId)) {
            throw new OaRequestDomainException("persisted OA request ID is invalid");
        }
        id = persistedId;
        restoreVersion(persistedVersion);
        if (created != null) {
            createdTime = created;
        }
        if (updated != null) {
            updatedTime = updated;
        }
    }

    private void validateAuthorityFacts() {
        requireFact(REQUEST_TITLE, getRequestTitleValue(), true);
        requireFact(FORM_CODE, formCode, true);
        requireFact(APPLICANT_ACTOR, applicantMemberId.getValue().toString(), true);
        requireFact(APPLICANT_DEPARTMENT,
                applicantDepartmentId == null ? null
                        : applicantDepartmentId.getValue().toString(), false);
        requireFact(SUBMITTED_TIME, submittedTime.toString(), true);
        String title = getRequestTitleValue();
        if (title.codePointCount(0, title.length()) > ApprovalInstance.MAX_TITLE_LENGTH) {
            throw new OaRequestDomainException("OA request title is too long");
        }
    }

    private String getRequestTitleValue() {
        List<String> values = facts.get(REQUEST_TITLE);
        if (values == null || values.size() != 1) {
            throw new OaRequestDomainException("OA request title fact is invalid");
        }
        return values.getFirst();
    }

    private void requireFact(FieldCode code, String expected, boolean required) {
        List<String> values = facts.get(code);
        if (expected == null) {
            if (required || values != null && !values.isEmpty()) {
                throw new OaRequestDomainException("OA request authority fact is invalid");
            }
            return;
        }
        if (values == null || values.size() != 1 || !expected.equals(values.getFirst())) {
            throw new OaRequestDomainException("OA request authority fact is invalid");
        }
    }

    private static Map<FieldCode, List<String>> facts(Map<FieldCode, List<String>> source) {
        if (source == null || source.isEmpty() || source.size() > MAX_FACTS) {
            throw new OaRequestDomainException("OA request facts are invalid");
        }
        LinkedHashMap<FieldCode, List<String>> result = new LinkedHashMap<>();
        source.forEach((fieldCode, values) -> {
            if (fieldCode == null || values == null || values.size() > 20
                    || values.stream().anyMatch(value -> value == null || value.length() > 500)) {
                throw new OaRequestDomainException("OA request fact value is invalid");
            }
            result.put(fieldCode, List.copyOf(values));
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    private static String idempotencyKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null
                || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new OaRequestDomainException("OA request idempotency key is invalid");
        }
        return normalized;
    }

    private static String digest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new OaRequestDomainException("OA request submission digest is invalid");
        }
        return value;
    }

    private static LocalDateTime time(LocalDateTime value) {
        if (value == null || value.getYear() < 1000 || value.getYear() > 9999) {
            throw new OaRequestDomainException("OA request submitted time is invalid");
        }
        return value.withNano(value.getNano() / 1_000 * 1_000);
    }

    private static Long nonNegative(Long value, String description) {
        if (value == null || value < 0) {
            throw new OaRequestDomainException(description + " is invalid");
        }
        return value;
    }

    private static String text(String value, int maximum, String description) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > maximum) {
            throw new OaRequestDomainException(description + " is invalid");
        }
        return normalized;
    }

    private static <T> T required(T value, String description) {
        if (value == null) {
            throw new OaRequestDomainException(description + " is required");
        }
        return value;
    }

    private static FieldCode code(String value) {
        return new FieldCode(value);
    }
}
