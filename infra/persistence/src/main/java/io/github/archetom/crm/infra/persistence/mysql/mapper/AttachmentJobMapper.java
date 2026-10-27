package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.AttachmentJobPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AttachmentJobMapper extends BaseMapper<AttachmentJobPO> {
    @Select("""
            SELECT * FROM crm_attachment_job
            WHERE kind = #{kind} AND state IN ('PENDING', 'LEASED') AND (deadline <= #{now}
                OR (state = 'PENDING' AND not_before <= #{now})
                OR (state = 'LEASED' AND lease_until <= #{now}))
            ORDER BY not_before, id LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<AttachmentJobPO> selectDueForUpdate(@Param("kind") String kind,
                                             @Param("now") LocalDateTime now,
                                             @Param("limit") int limit);

    @Update("""
            UPDATE crm_attachment_job SET state = 'LEASED', attempt = attempt + 1,
              lease_owner = #{owner}, lease_fence = lease_fence + 1, lease_until = #{until},
              updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND deadline > #{now} AND (state = 'PENDING'
              OR (state = 'LEASED' AND lease_until <= #{now}))
            """)
    int lease(@Param("id") long id, @Param("owner") byte[] owner,
              @Param("until") LocalDateTime until, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_job SET state = #{nextState}, result_digest = #{digest},
              lease_owner = NULL, lease_until = NULL, updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND deadline > #{now}
            """)
    int finish(@Param("id") long id, @Param("tenantId") long tenantId, @Param("fileId") long fileId,
               @Param("contentVersion") long contentVersion, @Param("generation") long generation,
               @Param("owner") byte[] owner, @Param("fence") long fence,
               @Param("nextState") String nextState, @Param("digest") byte[] digest,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_job SET result_digest = #{digest}, updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND deadline > #{now}
            """)
    int recordEffect(@Param("id") long id, @Param("tenantId") long tenantId, @Param("fileId") long fileId,
                     @Param("contentVersion") long contentVersion, @Param("generation") long generation,
                     @Param("owner") byte[] owner, @Param("fence") long fence,
                     @Param("digest") byte[] digest, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_job SET state = 'PENDING', lease_owner = NULL, lease_until = NULL,
              not_before = #{notBefore}, updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND deadline > #{now}
            """)
    int release(@Param("id") long id, @Param("tenantId") long tenantId, @Param("fileId") long fileId,
                @Param("contentVersion") long contentVersion, @Param("generation") long generation,
                @Param("owner") byte[] owner, @Param("fence") long fence,
                @Param("notBefore") LocalDateTime notBefore, @Param("now") LocalDateTime now);

    /**
     * A Customer/Contact pool pause is not a failure. Claiming has already incremented the
     * attempt, so restore it while moving the exact lease out of the claimable state set.
     */
    @Update("""
            UPDATE crm_attachment_job SET state = 'PAUSED', attempt = attempt - 1,
              lease_owner = NULL, lease_until = NULL, updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND deadline > #{now} AND attempt > 0
            """)
    int deferPaused(@Param("id") long id, @Param("tenantId") long tenantId, @Param("fileId") long fileId,
                    @Param("contentVersion") long contentVersion, @Param("generation") long generation,
                    @Param("owner") byte[] owner, @Param("fence") long fence, @Param("now") LocalDateTime now);

    /** Restoring ownership re-arms only durable pool-paused work and refreshes its deadline. */
    @Update("""
            UPDATE crm_attachment_job SET state = 'PENDING', not_before = #{notBefore}, deadline = #{deadline},
              updated_time = #{now}, version = version + 1
            WHERE tenant_id = #{tenantId} AND file_id = #{fileId}
              AND kind IN ('INSPECTION', 'PUBLICATION') AND state = 'PAUSED'
            """)
    int resumePaused(@Param("tenantId") long tenantId, @Param("fileId") long fileId,
                     @Param("notBefore") LocalDateTime notBefore, @Param("deadline") LocalDateTime deadline,
                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_job SET lease_until = #{until}, updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND deadline > #{now}
            """)
    int heartbeat(@Param("id") long id, @Param("tenantId") long tenantId, @Param("fileId") long fileId,
                  @Param("contentVersion") long contentVersion, @Param("generation") long generation,
                  @Param("owner") byte[] owner, @Param("fence") long fence,
                  @Param("until") LocalDateTime until, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_job SET state = 'DEAD', lease_owner = NULL, lease_until = NULL,
              updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND (attempt >= #{maxAttempts} OR deadline <= #{now}
                OR #{notBefore} >= deadline)
            """)
    int deadLetterLeased(@Param("id") long id, @Param("tenantId") long tenantId, @Param("fileId") long fileId,
                         @Param("contentVersion") long contentVersion, @Param("generation") long generation,
                         @Param("owner") byte[] owner, @Param("fence") long fence,
                         @Param("notBefore") LocalDateTime notBefore, @Param("maxAttempts") int maxAttempts,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_attachment_job SET state = 'DEAD', lease_owner = NULL, lease_until = NULL,
              updated_time = #{now}, version = version + 1
            WHERE id = #{id} AND (deadline <= #{now}
                OR (state = 'PENDING' AND not_before <= #{now})
                OR (state = 'LEASED' AND lease_until <= #{now}))
            """)
    int deadLetterCandidate(@Param("id") long id, @Param("now") LocalDateTime now);

    @Select("""
            SELECT id FROM crm_attachment_job WHERE id = #{id} AND tenant_id = #{tenantId} AND file_id = #{fileId}
              AND content_version = #{contentVersion} AND generation = #{generation} AND state = 'LEASED'
              AND lease_owner = #{owner} AND lease_fence = #{fence} AND lease_until > #{now}
              AND deadline > #{now} FOR UPDATE
            """)
    Long selectCurrentLeaseForUpdate(@Param("id") long id, @Param("tenantId") long tenantId,
                                     @Param("fileId") long fileId, @Param("contentVersion") long contentVersion,
                                     @Param("generation") long generation, @Param("owner") byte[] owner,
                                     @Param("fence") long fence, @Param("now") LocalDateTime now);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM crm_attachment_job WHERE id = #{id} AND tenant_id = #{tenantId}
              AND file_id = #{fileId} AND content_version = #{contentVersion} AND generation = #{generation}
              AND state = 'LEASED' AND lease_owner = #{owner} AND lease_fence = #{fence}
              AND lease_until > #{now} AND deadline > #{now})
            """)
    boolean existsCurrentLease(@Param("id") long id, @Param("tenantId") long tenantId,
                               @Param("fileId") long fileId, @Param("contentVersion") long contentVersion,
                               @Param("generation") long generation, @Param("owner") byte[] owner,
                               @Param("fence") long fence, @Param("now") LocalDateTime now);
}
