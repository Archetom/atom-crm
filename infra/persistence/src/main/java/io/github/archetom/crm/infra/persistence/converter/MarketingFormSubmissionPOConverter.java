package io.github.archetom.crm.infra.persistence.converter;

import io.github.archetom.crm.domain.model.MarketingFormSubmission;
import io.github.archetom.crm.domain.model.MarketingFormTargetType;
import io.github.archetom.crm.domain.valueobject.MarketingFormId;
import io.github.archetom.crm.domain.valueobject.MarketingFormSubmissionId;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormSubmissionPO;
import org.springframework.stereotype.Component;

@Component
public class MarketingFormSubmissionPOConverter {

    public MarketingFormSubmissionPO toPO(MarketingFormSubmission submission) {
        if (submission == null) {
            return null;
        }
        MarketingFormSubmissionPO po = new MarketingFormSubmissionPO();
        po.setId(submission.id() == null ? null : submission.id().getValue());
        po.setTenantId(submission.tenantId().getValue());
        po.setMarketingFormId(submission.marketingFormId().getValue());
        po.setFormVersion(submission.formVersion());
        po.setTargetType(submission.targetType().name());
        if (submission.targetType() == MarketingFormTargetType.LEAD) {
            po.setLeadId(submission.targetRecordId());
        } else {
            po.setCustomerId(submission.targetRecordId());
        }
        po.setIdempotencyKey(submission.idempotencyKey());
        po.setSubmissionDigest(submission.submissionDigest());
        po.setSubmittedTime(submission.submittedTime());
        return po;
    }

    public MarketingFormSubmission toDomain(MarketingFormSubmissionPO po) {
        if (po == null) {
            return null;
        }
        MarketingFormTargetType targetType = po.getTargetType() == null
                ? MarketingFormTargetType.LEAD : MarketingFormTargetType.valueOf(po.getTargetType());
        Long targetRecordId = targetType == MarketingFormTargetType.LEAD
                ? po.getLeadId() : po.getCustomerId();
        return new MarketingFormSubmission(
                new MarketingFormSubmissionId(po.getId()),
                new TenantId(po.getTenantId()),
                new MarketingFormId(po.getMarketingFormId()),
                po.getFormVersion(), targetType, requireTargetRecordId(targetRecordId),
                po.getIdempotencyKey(),
                po.getSubmissionDigest(),
                po.getSubmittedTime());
    }

    private long requireTargetRecordId(Long value) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException("marketing form target record ID is invalid");
        }
        return value;
    }
}
