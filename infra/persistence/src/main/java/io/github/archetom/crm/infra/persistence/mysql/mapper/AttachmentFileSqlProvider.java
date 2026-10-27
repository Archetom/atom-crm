package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.domain.model.AttachmentTargetType;

import java.util.Map;

/** Builds only closed-catalog relation joins; target columns are never interpolated from requests. */
public class AttachmentFileSqlProvider {
    public String countAvailableByTarget(Map<String, Object> parameters) {
        return selectPrefix(parameters) + "COUNT(*)" + selectSuffix(parameters);
    }

    public String selectAvailablePageByTarget(Map<String, Object> parameters) {
        return selectPrefix(parameters) + "f.*" + selectSuffix(parameters)
                + " ORDER BY f.available_time DESC, f.id DESC LIMIT #{offset}, #{size}";
    }

    private static String selectPrefix(Map<String, Object> parameters) {
        return "SELECT ";
    }

    private static String selectSuffix(Map<String, Object> parameters) {
        return " FROM crm_attachment_file f"
                + " JOIN crm_attachment_relation r ON r.tenant_id = f.tenant_id AND r.file_id = f.id"
                + " WHERE f.tenant_id = #{tenantId}"
                + " AND r.target_type = #{targetType}"
                + " AND r." + targetColumn(parameters) + " = #{targetId}"
                + imageTypePredicate(parameters)
                + " AND r.relation_state = 'ACTIVE' AND f.publication_state = 'AVAILABLE'";
    }

    private static String imageTypePredicate(Map<String, Object> parameters) {
        Object marketingImageType = parameters.get("marketingActivityImageType");
        Object productImageType = parameters.get("productImageType");
        if (marketingImageType != null && productImageType != null) {
            throw new IllegalArgumentException("attachment image groups are mutually exclusive");
        }
        if (marketingImageType == null && productImageType == null) return "";
        Object targetType = parameters.get("targetType");
        if (marketingImageType != null && (targetType == null
                || !AttachmentTargetType.MARKETING_ACTIVITY.name().equals(targetType.toString()))) {
            throw new IllegalArgumentException("marketing activity image type is unsupported");
        }
        if (productImageType != null && (targetType == null
                || !AttachmentTargetType.PRODUCT.name().equals(targetType.toString()))) {
            throw new IllegalArgumentException("product image type is unsupported");
        }
        return marketingImageType != null
                ? " AND r.marketing_activity_image_type = #{marketingActivityImageType}"
                : " AND r.product_image_type = #{productImageType}";
    }

    private static String targetColumn(Map<String, Object> parameters) {
        Object value = parameters.get("targetType");
        AttachmentTargetType type;
        try {
            type = AttachmentTargetType.valueOf(value == null ? "" : value.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("attachment target is unsupported", exception);
        }
        return switch (type) {
            case WORK_TASK -> "work_task_id";
            case CUSTOMER -> "customer_id";
            case CONTACT -> "contact_id";
            case LEAD -> "lead_id";
            case OPPORTUNITY -> "opportunity_id";
            case CONTRACT -> "contract_id";
            case INVOICE -> "invoice_id";
            case RECEIVABLE -> "receivable_id";
            case RECEIVABLE_PLAN -> "receivable_plan_id";
            case PRODUCT -> "product_id";
            case RETURN_VISIT -> "return_visit_id";
            case MARKETING_ACTIVITY -> "marketing_activity_id";
            case OA_ANNOUNCEMENT -> "oa_announcement_id";
            case OA_REQUEST -> "oa_request_id";
            case IMPORT_DRAFT -> "import_draft_id";
        };
    }
}
