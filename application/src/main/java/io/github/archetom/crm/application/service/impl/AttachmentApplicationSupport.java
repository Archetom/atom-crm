package io.github.archetom.crm.application.service.impl;

import io.github.archetom.crm.application.port.out.attachment.AttachmentRange;
import io.github.archetom.crm.application.vo.AttachmentUploadStatusVO;
import io.github.archetom.crm.application.vo.AttachmentVO;
import io.github.archetom.crm.domain.entity.AttachmentFile;
import io.github.archetom.crm.domain.model.AttachmentByteState;
import io.github.archetom.crm.domain.model.AttachmentInspectionState;
import io.github.archetom.crm.domain.model.AttachmentPublicationState;
import io.github.archetom.crm.domain.model.AttachmentTarget;
import io.github.archetom.crm.domain.model.AttachmentTargetType;
import io.github.archetom.crm.domain.model.MarketingActivityImageType;
import io.github.archetom.crm.domain.model.ProductImageType;
import io.github.archetom.crm.domain.valueobject.AttachmentFileId;
import io.github.archetom.crm.shared.enums.ApplicationErrorCode;
import io.github.archetom.crm.shared.exception.NonRetryableApplicationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Component
public class AttachmentApplicationSupport {
    private static final Map<String, String> EXTENSIONS = Map.of(
            ".pdf", "application/pdf", ".png", "image/png", ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg", ".csv", "text/csv");

    AttachmentTarget target(String token, Long id) {
        if (token == null || id == null || id <= 0) {
            throw invalid("attachment target is invalid");
        }
        String normalized = token.trim();
        AttachmentTargetType type = switch (normalized) {
            case "work-task" -> AttachmentTargetType.WORK_TASK;
            case "customer" -> AttachmentTargetType.CUSTOMER;
            case "contact" -> AttachmentTargetType.CONTACT;
            case "lead" -> AttachmentTargetType.LEAD;
            case "opportunity" -> AttachmentTargetType.OPPORTUNITY;
            case "contract" -> AttachmentTargetType.CONTRACT;
            case "invoice" -> AttachmentTargetType.INVOICE;
            case "receivable" -> AttachmentTargetType.RECEIVABLE;
            case "receivable-plan" -> AttachmentTargetType.RECEIVABLE_PLAN;
            case "product" -> AttachmentTargetType.PRODUCT;
            case "return-visit" -> AttachmentTargetType.RETURN_VISIT;
            case "marketing-activity" -> AttachmentTargetType.MARKETING_ACTIVITY;
            case "oa-announcement" -> AttachmentTargetType.OA_ANNOUNCEMENT;
            case "oa-request" -> AttachmentTargetType.OA_REQUEST;
            case "import-draft" -> AttachmentTargetType.IMPORT_DRAFT;
            default -> throw invalid("attachment target is unsupported");
        };
        return new AttachmentTarget(type, id);
    }

    AttachmentFileId fileId(Long value) {
        try {
            return new AttachmentFileId(value);
        } catch (RuntimeException exception) {
            throw invalid("attachment file ID is invalid");
        }
    }

    String displayName(String value) {
        if (value == null) {
            throw invalid("attachment display name is required");
        }
        String result = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (result.isEmpty() || result.codePointCount(0, result.length()) > 200
                || result.getBytes(StandardCharsets.UTF_8).length > 512
                || result.equals(".") || result.equals("..") || result.endsWith(".")
                || result.endsWith(" ") || hasForbiddenNameCharacter(result)) {
            throw invalid("attachment display name is invalid");
        }
        return result;
    }

    String declaredMediaType(String value, String displayName) {
        if (value == null) {
            throw invalid("attachment media type is invalid");
        }
        String result = value.trim().toLowerCase(Locale.ROOT);
        String extension = extension(displayName);
        if (!EXTENSIONS.containsValue(result) || !result.equals(EXTENSIONS.get(extension))) {
            throw invalid("attachment media type does not match display name");
        }
        return result;
    }

    void requireMediaAllowedForTarget(AttachmentTarget target, String mediaType) {
        if (target == null || mediaType == null) {
            throw invalid("attachment media type is invalid");
        }
        if (target.type() == AttachmentTargetType.IMPORT_DRAFT) {
            if (!"text/csv".equals(mediaType)) {
                throw invalid("CRM import drafts require CSV files");
            }
        } else if ("text/csv".equals(mediaType)) {
            throw invalid("CSV files are only valid for CRM import drafts");
        }
    }

    MarketingActivityImageType marketingActivityImageType(
            AttachmentTarget target, String value, String declaredMediaType) {
        if (value == null) return null;
        if (target == null || target.type() != AttachmentTargetType.MARKETING_ACTIVITY) {
            throw invalid("marketing activity image type is unsupported for this attachment target");
        }
        MarketingActivityImageType result;
        try {
            result = MarketingActivityImageType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("marketing activity image type is invalid");
        }
        if (!"image/png".equals(declaredMediaType) && !"image/jpeg".equals(declaredMediaType)) {
            throw invalid("marketing activity images must be PNG or JPEG");
        }
        return result;
    }

    MarketingActivityImageType marketingActivityImageTypeForQuery(AttachmentTarget target, String value) {
        if (value == null) return null;
        if (target == null || target.type() != AttachmentTargetType.MARKETING_ACTIVITY) {
            throw invalid("marketing activity image type is unsupported for this attachment target");
        }
        try {
            return MarketingActivityImageType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("marketing activity image type is invalid");
        }
    }

    ProductImageType productImageType(AttachmentTarget target, String value, String declaredMediaType) {
        if (value == null) return null;
        if (target == null || target.type() != AttachmentTargetType.PRODUCT) {
            throw invalid("product image type is unsupported for this attachment target");
        }
        ProductImageType result;
        try {
            result = ProductImageType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("product image type is invalid");
        }
        if (!"image/png".equals(declaredMediaType) && !"image/jpeg".equals(declaredMediaType)) {
            throw invalid("product images must be PNG or JPEG");
        }
        return result;
    }

    ProductImageType productImageTypeForQuery(AttachmentTarget target, String value) {
        if (value == null) return null;
        if (target == null || target.type() != AttachmentTargetType.PRODUCT) {
            throw invalid("product image type is unsupported for this attachment target");
        }
        try {
            return ProductImageType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("product image type is invalid");
        }
    }

    byte[] expectedSha256(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.indexOf('=') >= 0 || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw invalid("attachment SHA-256 is invalid");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(decoded).equals(value)) {
                throw invalid("attachment SHA-256 is invalid");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw invalid("attachment SHA-256 is invalid");
        }
    }

    AttachmentVO toVO(AttachmentFile file) {
        return new AttachmentVO(file.getId().getValue(), file.getDisplayName(), file.getMediaType(),
                file.getActualBytes(), file.getUploaderMemberId() == null ? null
                        : file.getUploaderMemberId().getValue(), file.getCreatedTime(),
                file.getAvailableTime(), file.getVersion());
    }

    AttachmentUploadStatusVO status(AttachmentFile file) {
        String value;
        if (file.getPublicationState() == AttachmentPublicationState.WITHDRAWN) {
            value = "WITHDRAWN";
        } else if (file.getPublicationState() == AttachmentPublicationState.DELETED) {
            value = file.getByteState() == AttachmentByteState.REMOVED ? "REMOVED" : "REMOVING";
        } else if (file.getByteState() == AttachmentByteState.UPLOADING) {
            value = "UPLOADING";
        } else if (file.getInspectionState() == AttachmentInspectionState.PENDING
                || file.getInspectionState() == AttachmentInspectionState.NOT_RUN) {
            value = "ACCEPTED_FOR_INSPECTION";
        } else if (file.getInspectionState() == AttachmentInspectionState.CLEAN
                && file.getPublicationState() == AttachmentPublicationState.AVAILABLE) {
            value = "AVAILABLE";
        } else if (file.getInspectionState() == AttachmentInspectionState.REJECTED) {
            value = "REJECTED";
        } else {
            value = "INDETERMINATE";
        }
        return new AttachmentUploadStatusVO(value, file.getControlledReason(), file.getVersion());
    }

    AttachmentRange singleRange(String value, long total) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (total <= 0 || !value.startsWith("bytes=") || value.indexOf(',') >= 0) {
            throw invalidRange();
        }
        String specification = value.substring("bytes=".length());
        int dash = specification.indexOf('-');
        if (dash < 0 || dash != specification.lastIndexOf('-')) {
            throw invalidRange();
        }
        String left = specification.substring(0, dash);
        String right = specification.substring(dash + 1);
        try {
            if (left.isEmpty()) {
                if (!digits(right)) throw invalidRange();
                long suffix = Long.parseLong(right);
                if (suffix <= 0) throw invalidRange();
                long length = Math.min(suffix, total);
                return new AttachmentRange(total - length, length, total);
            }
            if (!digits(left) || !right.isEmpty() && !digits(right)) throw invalidRange();
            long start = Long.parseLong(left);
            if (start >= total) throw invalidRange();
            long end = right.isEmpty() ? total - 1 : Long.parseLong(right);
            if (end < start) throw invalidRange();
            end = Math.min(end, total - 1);
            return new AttachmentRange(start, Math.addExact(Math.subtractExact(end, start), 1), total);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalidRange();
        }
    }

    NonRetryableApplicationException invalid(String message) {
        return new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID, message);
    }
    NonRetryableApplicationException notFound() {
        return new NonRetryableApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found");
    }
    NonRetryableApplicationException unavailable() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ATTACHMENT_UNAVAILABLE,
                "Attachment service is temporarily unavailable");
    }
    NonRetryableApplicationException marketingActivityImageLimit() {
        return new NonRetryableApplicationException(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                "Marketing activity image limit is reached");
    }
    NonRetryableApplicationException productImageLimit() {
        return new NonRetryableApplicationException(ApplicationErrorCode.OPERATION_NOT_ALLOWED,
                "Product image limit is reached");
    }
    NonRetryableApplicationException conflict() {
        return new NonRetryableApplicationException(ApplicationErrorCode.VERSION_CONFLICT,
                "Attachment upload intent has already been consumed");
    }

    private static String extension(String name) {
        int index = name.lastIndexOf('.');
        String extension = index < 0 ? "" : name.substring(index).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.containsKey(extension)) {
            throw new NonRetryableApplicationException(ApplicationErrorCode.PARAMETER_INVALID,
                    "attachment display name extension is invalid");
        }
        return extension;
    }
    private static boolean hasForbiddenNameCharacter(String value) {
        return value.codePoints().anyMatch(point -> point <= 0x1F || (point >= 0x7F && point <= 0x9F)
                || point == '/' || point == '\\' || point == ':' || point == 0x202A
                || point == 0x202B || point == 0x202C || point == 0x202D || point == 0x202E
                || (point >= 0x2066 && point <= 0x2069));
    }
    private static boolean digits(String value) {
        return !value.isEmpty() && value.chars().allMatch(character -> character >= '0' && character <= '9');
    }
    private NonRetryableApplicationException invalidRange() {
        return new NonRetryableApplicationException(ApplicationErrorCode.ATTACHMENT_RANGE_INVALID,
                "Attachment range is invalid");
    }
}
