package io.github.archetom.crm.infra.persistence.mysql.mapper;

import io.github.archetom.crm.infra.persistence.mysql.po.OaAnnouncementPublicationOccurrencePO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OaAnnouncementPublicationOccurrenceMapper
        extends BaseMapper<OaAnnouncementPublicationOccurrencePO> {

    @Select("SELECT UTC_TIMESTAMP(6)")
    LocalDateTime currentUtc();

    @Select("""
            SELECT * FROM oa_announcement_publication_occurrence
            WHERE (status IN ('READY', 'RETRY_WAIT')
                   AND (available_at <= #{now} OR deadline_at <= #{now}
                        OR attempt_count >= #{maxAttempts}))
               OR (status = 'LEASED' AND lease_until <= #{now})
            ORDER BY due_time ASC, id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    OaAnnouncementPublicationOccurrencePO selectClaimableForUpdate(
            @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE oa_announcement_publication_occurrence
            SET status = 'LEASED', attempt_count = attempt_count + 1,
                lease_owner = #{leaseOwner}, lease_fence = lease_fence + 1,
                lease_until = #{leaseUntil}, safe_reason = NULL
            WHERE id = #{id} AND status IN ('READY', 'RETRY_WAIT')
              AND available_at <= #{now} AND deadline_at > #{now}
              AND attempt_count < #{maxAttempts}
            """)
    int claimReady(@Param("id") long id, @Param("leaseOwner") String leaseOwner,
                   @Param("leaseUntil") LocalDateTime leaseUntil,
                   @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE oa_announcement_publication_occurrence
            SET status = 'LEASED', lease_owner = #{leaseOwner},
                lease_fence = lease_fence + 1, lease_until = #{leaseUntil}, safe_reason = NULL
            WHERE id = #{id} AND status IN ('READY', 'RETRY_WAIT')
            """)
    int claimTerminal(@Param("id") long id, @Param("leaseOwner") String leaseOwner,
                      @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE oa_announcement_publication_occurrence
            SET status = 'RETRY_WAIT', available_at = #{now},
                lease_owner = NULL, lease_until = NULL, safe_reason = 'RETRY'
            WHERE id = #{id} AND status = 'LEASED' AND lease_until <= #{now}
            """)
    int requeueExpiredLease(@Param("id") long id, @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM oa_announcement_publication_occurrence
            WHERE status IN ('PUBLISHED', 'CANCELLED', 'DEAD')
              AND terminal_time < #{beforeExclusive}
              AND NOT EXISTS (
                  SELECT 1 FROM oa_announcement announcement
                  WHERE announcement.tenant_id = oa_announcement_publication_occurrence.tenant_id
                    AND announcement.id = oa_announcement_publication_occurrence.announcement_id
                    AND announcement.publication_generation
                        = oa_announcement_publication_occurrence.publication_generation
                    AND announcement.status = 'SCHEDULED'
              )
            ORDER BY terminal_time ASC, id ASC LIMIT #{limit}
            """)
    int purgeTerminal(@Param("beforeExclusive") LocalDateTime beforeExclusive,
                      @Param("limit") int limit);
}
