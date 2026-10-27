package io.github.archetom.crm.infra.persistence.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.archetom.crm.infra.persistence.mysql.po.NotificationDeliveryPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDeliveryPO> {
    @Select("SELECT UTC_TIMESTAMP(6)")
    LocalDateTime currentUtc();

    @Select("SELECT * FROM crm_notification_delivery WHERE tenant_id = #{tenantId} AND id = #{id}")
    NotificationDeliveryPO selectByTenantId(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("""
            <script>
            SELECT * FROM crm_notification_delivery
            WHERE template_revision IN
            <foreach collection="templateRevisions" item="templateRevision" open="(" separator="," close=")">
                #{templateRevision}
            </foreach>
              AND ((status IN ('READY', 'RETRY_WAIT')
                    AND (available_at &lt;= #{now} OR deadline_at &lt;= #{now} OR attempt_count >= #{maxAttempts}))
                   OR (status IN ('LEASED', 'DISPATCHING') AND lease_until &lt;= #{now}))
            ORDER BY available_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            </script>
            """)
    NotificationDeliveryPO selectClaimableForUpdate(@Param("now") LocalDateTime now,
                                                     @Param("maxAttempts") int maxAttempts,
                                                     @Param("templateRevisions") java.util.Collection<String> templateRevisions);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'LEASED', attempt_count = attempt_count + 1,
                lease_owner = #{leaseOwner}, lease_fence = lease_fence + 1, lease_until = #{leaseUntil},
                safe_reason = NULL
            WHERE id = #{id} AND status IN ('READY', 'RETRY_WAIT') AND available_at <= #{now}
              AND deadline_at > #{now} AND attempt_count < #{maxAttempts}
            """)
    int claimReady(@Param("id") long id, @Param("leaseOwner") String leaseOwner,
                   @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now,
                   @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'RETRY_WAIT', available_at = #{now}, lease_owner = NULL, lease_until = NULL,
                safe_reason = 'LEASE_EXPIRED'
            WHERE id = #{id} AND status = 'LEASED' AND lease_until <= #{now}
              AND deadline_at > #{now}
            """)
    int requeueExpiredLease(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'RETRY_WAIT', available_at = #{now}, lease_owner = NULL, lease_until = NULL,
                dispatch_started_time = NULL, effect_certainty = 'AMBIGUOUS', safe_reason = 'IDEMPOTENT_RECOVERY'
            WHERE id = #{id} AND status = 'DISPATCHING' AND guarantee = 'IDEMPOTENT_SEND'
              AND lease_until <= #{now} AND idempotency_until > #{now} AND deadline_at > #{now}
              AND attempt_count < #{maxAttempts}
            """)
    int recoverExpiredIdempotentDispatch(@Param("id") long id, @Param("now") LocalDateTime now,
                                         @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'AMBIGUOUS', lease_owner = NULL, lease_until = NULL,
                terminal_time = #{now}, effect_certainty = 'AMBIGUOUS', safe_reason = 'DISPATCH_EXPIRED'
            WHERE id = #{id} AND status = 'DISPATCHING' AND lease_until <= #{now}
            """)
    int markExpiredDispatchAmbiguous(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = CASE WHEN effect_certainty = 'AMBIGUOUS' THEN 'AMBIGUOUS' ELSE 'DEAD' END,
                lease_owner = NULL, lease_until = NULL, terminal_time = #{now},
                safe_reason = CASE WHEN effect_certainty = 'AMBIGUOUS' THEN 'EFFECT_AMBIGUOUS'
                                   ELSE 'RETRY_EXHAUSTED' END
            WHERE id = #{id} AND status IN ('READY', 'RETRY_WAIT', 'LEASED')
              AND (status <> 'LEASED' OR lease_until <= #{now})
              AND (deadline_at <= #{now} OR attempt_count >= #{maxAttempts})
            """)
    int deadBeforeClaim(@Param("id") long id, @Param("now") LocalDateTime now,
                        @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'DISPATCHING',
                idempotency_until = CASE WHEN guarantee IS NULL THEN #{idempotencyUntil}
                                         ELSE idempotency_until END,
                endpoint_digest = CASE WHEN guarantee IS NULL THEN #{endpointDigest}
                                       ELSE endpoint_digest END,
                endpoint_digest_version = CASE WHEN guarantee IS NULL THEN #{endpointDigestVersion}
                                                ELSE endpoint_digest_version END,
                guarantee = COALESCE(guarantee, #{guarantee}),
                dispatch_started_time = #{now}
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'LEASED'
              AND lease_owner = #{leaseOwner} AND lease_fence = #{leaseFence}
              AND lease_until > UTC_TIMESTAMP(6) AND deadline_at > UTC_TIMESTAMP(6)
              AND (guarantee IS NULL OR guarantee = #{guarantee})
              AND (guarantee IS NULL OR guarantee = 'AT_MOST_ONCE_SEND'
                   OR (guarantee = 'IDEMPOTENT_SEND' AND idempotency_until > UTC_TIMESTAMP(6)))
              AND (guarantee IS NULL OR (endpoint_digest = #{endpointDigest}
                                         AND endpoint_digest_version = #{endpointDigestVersion}))
            """)
    int beginDispatch(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                      @Param("guarantee") String guarantee, @Param("idempotencyUntil") LocalDateTime idempotencyUntil,
                      @Param("endpointDigest") String endpointDigest,
                      @Param("endpointDigestVersion") String endpointDigestVersion,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = #{status}, lease_owner = NULL, lease_until = NULL, terminal_time = #{now},
                safe_reason = #{safeReason},
                effect_certainty = CASE WHEN #{status} = 'ACCEPTED' THEN 'ACCEPTED' ELSE 'NO_EFFECT' END
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DISPATCHING'
              AND lease_owner = #{leaseOwner} AND lease_fence = #{leaseFence}
              AND lease_until > UTC_TIMESTAMP(6)
            """)
    int settleTerminal(@Param("tenantId") long tenantId, @Param("id") long id,
                       @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                       @Param("status") String status, @Param("safeReason") String safeReason,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                              THEN CASE WHEN effect_certainty = 'AMBIGUOUS' THEN 'AMBIGUOUS' ELSE 'DEAD' END
                              ELSE 'RETRY_WAIT' END,
                available_at = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                    THEN available_at ELSE #{retryAt} END,
                lease_owner = NULL, lease_until = NULL, dispatch_started_time = NULL,
                terminal_time = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                     THEN #{now} ELSE NULL END,
                safe_reason = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                   THEN CASE WHEN effect_certainty = 'AMBIGUOUS' THEN 'EFFECT_AMBIGUOUS'
                                             ELSE 'RETRY_DEADLINE' END
                                   ELSE 'NO_DISPATCH_RETRY' END
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'LEASED'
              AND lease_owner = #{leaseOwner} AND lease_fence = #{leaseFence}
              AND lease_until > UTC_TIMESTAMP(6)
            """)
    int retryLeaseOrDead(@Param("tenantId") long tenantId, @Param("id") long id,
                         @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                         @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts,
                         @Param("retryAt") LocalDateTime retryAt);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                   OR idempotency_until <= #{retryAt}
                              THEN CASE WHEN effect_certainty = 'AMBIGUOUS' OR #{effectCertainty} = 'AMBIGUOUS'
                                        THEN 'AMBIGUOUS' ELSE 'DEAD' END
                              ELSE 'RETRY_WAIT' END,
                available_at = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                        OR idempotency_until <= #{retryAt}
                                    THEN available_at ELSE #{retryAt} END,
                lease_owner = NULL, lease_until = NULL, dispatch_started_time = NULL,
                terminal_time = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                          OR idempotency_until <= #{retryAt}
                                     THEN #{now} ELSE NULL END,
                safe_reason = CASE WHEN attempt_count >= #{maxAttempts} OR deadline_at <= #{retryAt}
                                        OR idempotency_until <= #{retryAt}
                                   THEN CASE WHEN effect_certainty = 'AMBIGUOUS' OR #{effectCertainty} = 'AMBIGUOUS'
                                             THEN 'IDEMPOTENCY_EFFECT_AMBIGUOUS' ELSE 'RETRY_DEADLINE' END
                                   ELSE 'IDEMPOTENT_RETRY' END,
                effect_certainty = CASE WHEN effect_certainty = 'AMBIGUOUS' OR #{effectCertainty} = 'AMBIGUOUS'
                                        THEN 'AMBIGUOUS' ELSE #{effectCertainty} END
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DISPATCHING'
              AND guarantee = 'IDEMPOTENT_SEND' AND lease_owner = #{leaseOwner}
              AND lease_fence = #{leaseFence} AND lease_until > UTC_TIMESTAMP(6)
            """)
    int retryIdempotentOrTerminal(@Param("tenantId") long tenantId, @Param("id") long id,
                                  @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                                  @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts,
                                  @Param("retryAt") LocalDateTime retryAt,
                                  @Param("effectCertainty") String effectCertainty);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'AMBIGUOUS', lease_owner = NULL, lease_until = NULL,
                terminal_time = #{now}, effect_certainty = 'AMBIGUOUS', safe_reason = #{safeReason}
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'DISPATCHING'
              AND guarantee = 'AT_MOST_ONCE_SEND' AND lease_owner = #{leaseOwner}
              AND lease_fence = #{leaseFence} AND lease_until > UTC_TIMESTAMP(6)
            """)
    int settleAtMostOnceAmbiguous(@Param("tenantId") long tenantId, @Param("id") long id,
                                  @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                                  @Param("safeReason") String safeReason, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = CASE WHEN guarantee IS NULL THEN 'SUPPRESSED' ELSE 'AMBIGUOUS' END,
                lease_owner = NULL, lease_until = NULL, terminal_time = #{now},
                safe_reason = #{safeReason},
                effect_certainty = CASE WHEN guarantee IS NULL THEN effect_certainty ELSE 'AMBIGUOUS' END
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'LEASED'
              AND lease_owner = #{leaseOwner} AND lease_fence = #{leaseFence}
              AND lease_until > UTC_TIMESTAMP(6)
            """)
    int suppressClaim(@Param("tenantId") long tenantId, @Param("id") long id,
                      @Param("leaseOwner") String leaseOwner, @Param("leaseFence") long leaseFence,
                      @Param("safeReason") String safeReason, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery delivery
            JOIN crm_notification notification
              ON notification.tenant_id = delivery.tenant_id AND notification.id = delivery.notification_id
            SET delivery.status = 'SUPPRESSED', delivery.lease_owner = NULL, delivery.lease_until = NULL,
                delivery.terminal_time = #{now}, delivery.safe_reason = 'TASK_SUPERSEDED'
            WHERE delivery.tenant_id = #{tenantId} AND delivery.work_task_id = #{workTaskId}
              AND delivery.status IN ('READY', 'LEASED', 'RETRY_WAIT')
              AND delivery.guarantee IS NULL
              AND (notification.status = 'WITHDRAWN' OR #{taskOpen} = 0
                   OR notification.task_notification_projection_version <> #{projectionVersion})
            """)
    int suppressStaleForTaskWithoutDispatch(@Param("tenantId") long tenantId, @Param("workTaskId") long workTaskId,
                                            @Param("projectionVersion") long projectionVersion,
                                            @Param("taskOpen") boolean taskOpen, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery delivery
            JOIN crm_notification notification
              ON notification.tenant_id = delivery.tenant_id AND notification.id = delivery.notification_id
            SET delivery.status = 'AMBIGUOUS', delivery.lease_owner = NULL, delivery.lease_until = NULL,
                delivery.terminal_time = #{now}, delivery.effect_certainty = 'AMBIGUOUS',
                delivery.safe_reason = 'DISPATCH_HISTORY'
            WHERE delivery.tenant_id = #{tenantId} AND delivery.work_task_id = #{workTaskId}
              AND delivery.status IN ('READY', 'LEASED', 'RETRY_WAIT')
              AND delivery.guarantee IS NOT NULL
              AND (notification.status = 'WITHDRAWN' OR #{taskOpen} = 0
                   OR notification.task_notification_projection_version <> #{projectionVersion})
            """)
    int markStaleForTaskAmbiguous(@Param("tenantId") long tenantId, @Param("workTaskId") long workTaskId,
                                  @Param("projectionVersion") long projectionVersion,
                                  @Param("taskOpen") boolean taskOpen, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'SUPPRESSED', lease_owner = NULL, lease_until = NULL, terminal_time = #{now},
                safe_reason = 'ANNOUNCEMENT_WITHDRAWN'
            WHERE tenant_id = #{tenantId} AND source_kind = 'OA_ANNOUNCEMENT_RECIPIENT'
              AND oa_announcement_id = #{announcementId} AND status IN ('READY', 'RETRY_WAIT', 'LEASED')
              AND guarantee IS NULL AND effect_certainty = 'NO_EFFECT'
            """)
    int suppressOaAnnouncementWithoutDispatch(@Param("tenantId") long tenantId,
                                               @Param("announcementId") long announcementId,
                                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'AMBIGUOUS', lease_owner = NULL, lease_until = NULL, terminal_time = #{now},
                effect_certainty = 'AMBIGUOUS', safe_reason = 'ANNOUNCEMENT_WITHDRAWN'
            WHERE tenant_id = #{tenantId} AND source_kind = 'OA_ANNOUNCEMENT_RECIPIENT'
              AND oa_announcement_id = #{announcementId}
              AND ((status IN ('READY', 'RETRY_WAIT', 'LEASED')
                    AND (guarantee IS NOT NULL OR effect_certainty <> 'NO_EFFECT'))
                   OR status = 'DISPATCHING')
            """)
    int markOaAnnouncementAmbiguous(@Param("tenantId") long tenantId,
                                    @Param("announcementId") long announcementId,
                                    @Param("now") LocalDateTime now);

    @Insert("""
            <script>
            INSERT INTO crm_notification_delivery
              (tenant_id, source_kind, recipient_actor_id, oa_announcement_id, channel, template_revision,
               payload_digest, status, attempt_count, available_at, deadline_at, lease_fence, effect_certainty)
            VALUES
            <foreach collection="recipientIds" item="recipientId" separator=",">
              (#{tenantId}, 'OA_ANNOUNCEMENT_RECIPIENT', #{recipientId}, #{announcementId}, 'EMAIL',
               #{templateRevision}, #{payloadDigest}, 'READY', 0, #{availableAt}, #{deadlineAt}, 0, 'NO_EFFECT')
            </foreach>
            </script>
            """)
    int insertOaAnnouncementEmailBatch(@Param("tenantId") long tenantId,
                                        @Param("announcementId") long announcementId,
                                        @Param("recipientIds") java.util.Collection<Long> recipientIds,
                                        @Param("templateRevision") String templateRevision,
                                        @Param("payloadDigest") String payloadDigest,
                                        @Param("availableAt") LocalDateTime availableAt,
                                        @Param("deadlineAt") LocalDateTime deadlineAt);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'DEAD', lease_owner = NULL, lease_until = NULL,
                terminal_time = #{now}, safe_reason = 'DELIVERY_DEADLINE'
            WHERE status IN ('READY', 'RETRY_WAIT', 'LEASED') AND deadline_at <= #{now}
              AND effect_certainty <> 'AMBIGUOUS'
              AND (status <> 'LEASED' OR lease_until <= #{now})
            ORDER BY deadline_at ASC, id ASC LIMIT #{limit}
            """)
    int expireOverdueNoEffect(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'AMBIGUOUS', lease_owner = NULL, lease_until = NULL,
                terminal_time = #{now}, safe_reason = 'EFFECT_AMBIGUOUS'
            WHERE status IN ('READY', 'RETRY_WAIT', 'LEASED') AND deadline_at <= #{now}
              AND effect_certainty = 'AMBIGUOUS'
              AND (status <> 'LEASED' OR lease_until <= #{now})
            ORDER BY deadline_at ASC, id ASC LIMIT #{limit}
            """)
    int expireOverdueAmbiguousEffect(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE crm_notification_delivery
            SET status = 'AMBIGUOUS', lease_owner = NULL, lease_until = NULL,
                terminal_time = #{now}, effect_certainty = 'AMBIGUOUS', safe_reason = 'DISPATCH_DEADLINE'
            WHERE status = 'DISPATCHING' AND deadline_at <= #{now}
            ORDER BY deadline_at ASC, id ASC LIMIT #{limit}
            """)
    int expireOverdueDispatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Delete("""
            DELETE FROM crm_notification_delivery
            WHERE status IN ('ACCEPTED', 'REJECTED', 'SUPPRESSED', 'AMBIGUOUS', 'DEAD')
              AND terminal_time < #{beforeExclusive}
            ORDER BY terminal_time ASC, id ASC LIMIT #{limit}
            """)
    int purgeTerminal(@Param("beforeExclusive") LocalDateTime beforeExclusive, @Param("limit") int limit);
}
