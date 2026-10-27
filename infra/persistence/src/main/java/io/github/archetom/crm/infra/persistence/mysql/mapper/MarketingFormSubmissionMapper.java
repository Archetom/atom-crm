package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.MarketingFormSubmissionPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface MarketingFormSubmissionMapper
        extends BaseMapper<MarketingFormSubmissionPO> {

    @Insert("""
            INSERT INTO crm_marketing_form_submission
                (tenant_id, marketing_form_id, form_version, target_type, lead_id, customer_id,
                 idempotency_key, submission_digest, submitted_time)
            VALUES
                (#{tenantId}, #{marketingFormId}, #{formVersion}, #{targetType}, #{leadId},
                 #{customerId}, #{idempotencyKey}, #{submissionDigest}, UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithDatabaseUtcTime(MarketingFormSubmissionPO submission);
}
